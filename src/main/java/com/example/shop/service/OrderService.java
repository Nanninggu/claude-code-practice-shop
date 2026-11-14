package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.dto.*;
import com.example.shop.repository.*;
import com.example.shop.util.Constants;
import com.example.shop.util.DateUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 주문 서비스
 *
 * 2021.03 최초 작성
 * 2021.09 포인트 사용 추가
 * 2022.07 부분취소 추가
 * 2023.01 관리자 통계/검색 추가
 * 2023.06 CSV 다운로드 추가
 * 2023.11 재주문 기능 추가
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderItemRepository orderItemRepository;

    private final ProductRepository productRepository;

    private final MemberRepository memberRepository;

    private final PaymentRepository paymentRepository;

    private final CouponRepository couponRepository;

    private final PaymentService paymentService;

    private final ProductService productService;

    private final NotificationService notificationService;

    private final EntityManager em;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[0-9]-?\\d{3,4}-?\\d{4}$");

    private static int sequence = 0;

    // =====================================================================
    // 주문 생성
    // =====================================================================

    public OrderResponse createOrder(Long memberId, OrderCreateRequest req) {
        log.info("주문 생성 시작 memberId={}", memberId);

        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + memberId);
        }

        validateOrderRequest(req);

        Order order = new Order();
        order.setOrdNo(generateOrdNo());
        order.setMember(member);
        order.setStatus(OrderStatus.CREATED);
        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setAddress(req.getAddress());
        order.setMemo(req.getMemo());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        int totalPrice = 0;
        for (OrderCreateRequest.Item reqItem : req.getItems()) {
            Product product = productRepository.findById(reqItem.getProductId()).orElse(null);
            if (product == null) {
                throw new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + reqItem.getProductId());
            }
            if (product.getStatus() != ProductStatus.ON_SALE) {
                throw new IllegalStateException("판매중인 상품이 아닙니다: " + product.getName());
            }
            if (product.getStock() < reqItem.getQuantity()) {
                throw new IllegalStateException("재고가 부족합니다: " + product.getName());
            }

            // 재고 차감
            product.setStock(product.getStock() - reqItem.getQuantity());
            if (product.getStock() == 0) {
                product.setStatus(ProductStatus.SOLD_OUT);
            }
            product.setUpdatedAt(LocalDateTime.now());

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(reqItem.getQuantity());
            item.setCanceledQuantity(0);
            item.setStatus(OrderItemStatus.ORDERED);
            order.getItems().add(item);

            totalPrice += product.getPrice() * reqItem.getQuantity();
        }

        // 등급 할인
        int discountPrice = calculateDiscount(totalPrice, member.getGrade());

        // 쿠폰 (등급 할인 후 금액 기준)
        int couponDiscount = 0;
        if (hasCouponCode(req)) {
            Coupon coupon = couponRepository.findByCode(req.getCouponCode().trim()).orElse(null);
            validateCoupon(coupon, req.getCouponCode());
            couponDiscount = coupon.calcDiscount(totalPrice - discountPrice);
            coupon.setUsed(true);
            order.setCoupon(coupon);
        }

        // 배송비
        int shippingFee = calculateShippingFee(totalPrice - discountPrice - couponDiscount, member.getGrade());

        // 포인트 사용
        int usePoint = req.getUsePoint();
        if (usePoint > 0) {
            if (usePoint < Constants.MIN_USE_POINT) {
                throw new IllegalArgumentException("포인트는 " + Constants.MIN_USE_POINT + "점 이상부터 사용 가능합니다.");
            }
            if (member.getPoint() < usePoint) {
                throw new IllegalStateException("보유 포인트가 부족합니다.");
            }
            int maxUsable = totalPrice - discountPrice - couponDiscount + shippingFee;
            if (usePoint > maxUsable) {
                usePoint = maxUsable;
            }
            member.setPoint(member.getPoint() - usePoint);
        }

        order.setTotalPrice(totalPrice);
        order.setDiscountPrice(discountPrice);
        order.setCouponDiscount(couponDiscount);
        order.setShippingFee(shippingFee);
        order.setUsedPoint(usePoint);
        order.setPayPrice(totalPrice - discountPrice - couponDiscount + shippingFee - usePoint);

        orderRepository.save(order);

        notificationService.sendOrderCreated(member, order);

        log.info("주문 생성 완료 ordNo={}, payPrice={}", order.getOrdNo(), order.getPayPrice());
        return toResponse(order);
    }

    /**
     * 재주문 - 이전 주문과 동일한 상품/수량으로 새 주문 생성
     */
    public OrderResponse reOrder(Long memberId, Long orderId) {
        Order prevOrder = orderRepository.findById(orderId).orElse(null);
        if (prevOrder == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (!prevOrder.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인 주문만 재주문할 수 있습니다.");
        }

        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + memberId);
        }

        Order order = new Order();
        order.setOrdNo(generateOrdNo());
        order.setMember(member);
        order.setStatus(OrderStatus.CREATED);
        order.setReceiverName(prevOrder.getReceiverName());
        order.setReceiverPhone(prevOrder.getReceiverPhone());
        order.setAddress(prevOrder.getAddress());
        order.setMemo(prevOrder.getMemo());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        int totalPrice = 0;
        for (OrderItem prevItem : prevOrder.getItems()) {
            int quantity = prevItem.getQuantity() - prevItem.getCanceledQuantity();
            if (quantity <= 0) {
                continue;
            }
            Product product = productRepository.findById(prevItem.getProduct().getId()).orElse(null);
            if (product == null) {
                throw new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + prevItem.getProduct().getId());
            }
            if (product.getStock() < quantity) {
                throw new IllegalStateException("재고가 부족합니다: " + product.getName());
            }

            product.setStock(product.getStock() - quantity);
            if (product.getStock() == 0) {
                product.setStatus(ProductStatus.SOLD_OUT);
            }
            product.setUpdatedAt(LocalDateTime.now());

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(quantity);
            item.setCanceledQuantity(0);
            item.setStatus(OrderItemStatus.ORDERED);
            order.getItems().add(item);

            totalPrice += product.getPrice() * quantity;
        }

        if (order.getItems().isEmpty()) {
            throw new IllegalStateException("재주문할 상품이 없습니다.");
        }

        int discountPrice = calculateDiscount(totalPrice, member.getGrade());
        int shippingFee = calculateShippingFee(totalPrice - discountPrice, member.getGrade());

        order.setTotalPrice(totalPrice);
        order.setDiscountPrice(discountPrice);
        order.setShippingFee(shippingFee);
        order.setUsedPoint(0);
        order.setPayPrice(totalPrice - discountPrice + shippingFee);

        orderRepository.save(order);

        notificationService.sendOrderCreated(member, order);

        return toResponse(order);
    }

    // =====================================================================
    // 주문 조회
    // =====================================================================

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrdNo(String ordNo) {
        Order order = orderRepository.findByOrdNo(ordNo).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. ordNo=" + ordNo);
        }
        return toResponse(order);
    }

    /**
     * 내 주문 목록
     */
    public List<OrderResponse> getMyOrders(Long memberId) {
        List<Order> orders = orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        List<OrderResponse> result = new ArrayList<>();
        for (Order order : orders) {
            result.add(toResponse(order));
        }
        return result;
    }

    /**
     * 내 주문 상태별 건수 (마이페이지 상단)
     */
    public Map<String, Integer> getOrderStatusSummary(Long memberId) {
        List<Order> orders = orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("결제대기", 0);
        summary.put("결제완료", 0);
        summary.put("배송준비", 0);
        summary.put("배송중", 0);
        summary.put("배송완료", 0);
        summary.put("취소", 0);
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CREATED) {
                summary.put("결제대기", summary.get("결제대기") + 1);
            } else if (order.getStatus() == OrderStatus.PAID) {
                summary.put("결제완료", summary.get("결제완료") + 1);
            } else if (order.getStatus() == OrderStatus.PREPARING) {
                summary.put("배송준비", summary.get("배송준비") + 1);
            } else if (order.getStatus() == OrderStatus.SHIPPED) {
                summary.put("배송중", summary.get("배송중") + 1);
            } else if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CONFIRMED) {
                summary.put("배송완료", summary.get("배송완료") + 1);
            } else if (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.PARTIAL_CANCELED) {
                summary.put("취소", summary.get("취소") + 1);
            }
        }
        return summary;
    }

    // =====================================================================
    // 주문 취소
    // =====================================================================

    public void cancelOrder(Long orderId, Long memberId, String reason) {
        log.info("주문 취소 요청 orderId={}, memberId={}, reason={}", orderId, memberId, reason);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("배송이 시작된 주문은 취소할 수 없습니다.");
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment.getStatus() == PaymentStatus.APPROVED || payment.getStatus() == PaymentStatus.PARTIAL_CANCELED) {
            int refundAmount = payment.getAmount() - payment.getCanceledAmount();
            paymentService.refund(orderId, refundAmount);

            // 적립된 포인트 회수
            Member member = order.getMember();
            int rate = (member.getGrade() == MemberGrade.GOLD || member.getGrade() == MemberGrade.VIP) ? 2 : 1;
            int earnedPoint = payment.getAmount() * rate / 100;
            member.setPoint(Math.max(0, member.getPoint() - earnedPoint));
        }

        // 재고 복구
        for (OrderItem item : order.getItems()) {
            if (item.getStatus() == OrderItemStatus.CANCELED) {
                continue;
            }
            int restoreQty = item.getQuantity() - item.getCanceledQuantity();
            productService.increaseStock(item.getProduct().getId(), restoreQty);
            item.setCanceledQuantity(item.getQuantity());
            item.setStatus(OrderItemStatus.CANCELED);
        }

        // 사용 포인트 복구
        Member member = order.getMember();
        member.setPoint(member.getPoint() + order.getUsedPoint());

        // 쿠폰 복원
        restoreCoupon(order);

        order.setStatus(OrderStatus.CANCELED);
        order.setCancelReason(reason);
        order.setCanceledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        notificationService.sendOrderCanceled(member, order);
    }

    /**
     * 부분 취소 - 주문 상품 일부 수량만 취소
     */
    public void partialCancel(Long orderId, Long itemId, int quantity) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PARTIAL_CANCELED) {
            throw new IllegalStateException("결제 완료된 주문만 부분 취소할 수 있습니다.");
        }

        OrderItem target = null;
        for (OrderItem item : order.getItems()) {
            if (item.getId().equals(itemId)) {
                target = item;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("주문 상품을 찾을 수 없습니다. itemId=" + itemId);
        }

        int remain = target.getQuantity() - target.getCanceledQuantity();
        if (quantity <= 0 || quantity > remain) {
            throw new IllegalArgumentException("취소 가능 수량을 초과했습니다. 남은 수량=" + remain);
        }

        // 환불 금액 계산
        // 상품 금액에서 할인(등급+쿠폰) 몫을 비례 배분해 뺀다.
        int itemAmount = target.getUnitPrice() * quantity;
        int discountShare = order.getTotalPrice() == 0 ? 0
                : (int) ((long) (order.getDiscountPrice() + order.getCouponDiscount()) * itemAmount / order.getTotalPrice());
        int refundAmount = itemAmount - discountShare;
        paymentService.refund(orderId, refundAmount);

        target.setCanceledQuantity(target.getCanceledQuantity() + quantity);
        if (target.getCanceledQuantity() == target.getQuantity()) {
            target.setStatus(OrderItemStatus.CANCELED);
        }

        productService.increaseStock(target.getProduct().getId(), quantity);

        // 모든 상품이 취소되면 전체 취소 처리
        boolean allCanceled = true;
        for (OrderItem item : order.getItems()) {
            if (item.getStatus() != OrderItemStatus.CANCELED) {
                allCanceled = false;
                break;
            }
        }
        if (allCanceled) {
            order.setStatus(OrderStatus.CANCELED);
            order.setCanceledAt(LocalDateTime.now());
        } else {
            order.setStatus(OrderStatus.PARTIAL_CANCELED);
        }
        order.setUpdatedAt(LocalDateTime.now());

        // FIXME 2022-07 부분취소 시 배송비/할인 재계산 필요 - 정산팀 확인 후 처리
    }

    /**
     * 관리자 강제 취소 (CS 처리용)
     */
    public void adminForceCancel(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment != null && (payment.getStatus() == PaymentStatus.APPROVED
                || payment.getStatus() == PaymentStatus.PARTIAL_CANCELED)) {
            int refundAmount = payment.getAmount() - payment.getCanceledAmount();
            paymentService.refund(orderId, refundAmount);
        }

        for (OrderItem item : order.getItems()) {
            if (item.getStatus() == OrderItemStatus.CANCELED) {
                continue;
            }
            int restoreQty = item.getQuantity() - item.getCanceledQuantity();
            productService.increaseStock(item.getProduct().getId(), restoreQty);
            item.setCanceledQuantity(item.getQuantity());
            item.setStatus(OrderItemStatus.CANCELED);
        }

        Member member = order.getMember();
        member.setPoint(member.getPoint() + order.getUsedPoint());

        // 쿠폰 복원
        restoreCoupon(order);

        order.setStatus(OrderStatus.CANCELED);
        order.setCancelReason("[관리자] " + reason);
        order.setCanceledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        log.info("관리자 강제 취소 완료 orderId={}", orderId);
    }

    /**
     * 미결제 주문 자동 취소 (스케줄러에서 호출)
     * 주문 후 30분 동안 결제하지 않으면 취소
     */
    public int expireUnpaidOrders() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(30);
        List<Order> orders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.CREATED, limit);

        int count = 0;
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStock(product.getStock() + item.getQuantity());
                if (product.getStatus() == ProductStatus.SOLD_OUT) {
                    product.setStatus(ProductStatus.ON_SALE);
                }
                item.setStatus(OrderItemStatus.CANCELED);
            }
            restoreCoupon(order);
            order.setStatus(OrderStatus.CANCELED);
            order.setCancelReason("결제 시간 초과");
            order.setCanceledAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            count++;
        }

        if (count > 0) {
            log.info("미결제 주문 자동 취소 {}건", count);
        }
        return count;
    }

    // =====================================================================
    // 주문 변경 / 배송
    // =====================================================================

    public OrderResponse changeAddress(Long orderId, AddressChangeRequest req) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("배송이 시작된 주문은 배송지를 변경할 수 없습니다.");
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new IllegalStateException("취소된 주문입니다.");
        }

        if (req.getReceiverName() == null || req.getReceiverName().trim().isEmpty()) {
            throw new IllegalArgumentException("받는 분 이름을 입력해주세요.");
        }
        if (req.getReceiverPhone() == null || !PHONE_PATTERN.matcher(req.getReceiverPhone()).matches()) {
            throw new IllegalArgumentException("휴대폰 번호 형식이 올바르지 않습니다.");
        }
        if (req.getAddress() == null || req.getAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("배송지 주소를 입력해주세요.");
        }
        if (req.getAddress().length() > 200) {
            throw new IllegalArgumentException("주소는 200자 이내로 입력해주세요.");
        }

        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setAddress(req.getAddress());
        order.setUpdatedAt(LocalDateTime.now());

        return toResponse(order);
    }

    public void startPreparing(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("결제 완료 상태에서만 배송 준비를 시작할 수 있습니다.");
        }
        order.setStatus(OrderStatus.PREPARING);
        order.setUpdatedAt(LocalDateTime.now());
    }

    public void ship(Long orderId, String trackingNo) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (order.getStatus() != OrderStatus.PREPARING) {
            throw new IllegalStateException("배송 준비 상태에서만 발송할 수 있습니다.");
        }
        if (trackingNo == null || trackingNo.isEmpty()) {
            throw new IllegalArgumentException("송장번호를 입력해주세요.");
        }
        order.setTrackingNo(trackingNo);
        order.setStatus(OrderStatus.SHIPPED);
        order.setUpdatedAt(LocalDateTime.now());

        notificationService.sendShipped(order.getMember(), order);
    }

    public void deliver(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalStateException("배송중 상태에서만 배송 완료 처리할 수 있습니다.");
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.setUpdatedAt(LocalDateTime.now());
    }

    public void confirmPurchase(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId);
        }
        if (!order.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인 주문만 구매확정할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("배송 완료된 주문만 구매확정할 수 있습니다.");
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(LocalDateTime.now());
    }

    // =====================================================================
    // 관리자 검색 / 통계
    // =====================================================================

    /**
     * 관리자 주문 검색
     * status: 주문상태 (선택), keyword: 주문번호 또는 받는분 이름 (선택)
     */
    @SuppressWarnings("unchecked")
    public List<OrderResponse> searchOrdersForAdmin(String status, String keyword, String fromDate, String toDate) {
        StringBuilder sql = new StringBuilder("select * from orders o where 1=1");
        if (status != null && !status.isEmpty()) {
            sql.append(" and o.status = '").append(status).append("'");
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" and (o.ord_no like '%").append(keyword).append("%'")
                    .append(" or o.receiver_name like '%").append(keyword).append("%')");
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append(" and o.created_at >= '").append(fromDate).append(" 00:00:00'");
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append(" and o.created_at <= '").append(toDate).append(" 23:59:59'");
        }
        sql.append(" order by o.id desc");

        log.debug("관리자 검색 SQL: {}", sql);

        List<Order> orders = em.createNativeQuery(sql.toString(), Order.class).getResultList();

        List<OrderResponse> result = new ArrayList<>();
        for (Order order : orders) {
            result.add(toResponse(order));
        }
        return result;
    }

    /**
     * 일별 매출
     */
    public Map<String, Object> getDailySales(LocalDate date) {
        List<Order> all = orderRepository.findAll();

        int orderCount = 0;
        int salesAmount = 0;
        int cancelAmount = 0;
        int itemCount = 0;

        for (Order order : all) {
            if (order.getCreatedAt().toLocalDate().equals(date)) {
                if (order.getStatus() == OrderStatus.CREATED) {
                    continue;
                }
                if (order.getStatus() == OrderStatus.CANCELED) {
                    cancelAmount += order.getPayPrice();
                    continue;
                }
                orderCount++;
                salesAmount += order.getPayPrice();
                for (OrderItem item : order.getItems()) {
                    itemCount += item.getQuantity() - item.getCanceledQuantity();
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.toString());
        result.put("orderCount", orderCount);
        result.put("salesAmount", salesAmount);
        result.put("cancelAmount", cancelAmount);
        result.put("itemCount", itemCount);
        return result;
    }

    /**
     * 월별 매출
     */
    public Map<String, Object> getMonthlySales(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<Order> all = orderRepository.findAll();

        int orderCount = 0;
        int salesAmount = 0;
        int cancelAmount = 0;
        Map<String, Integer> dailyAmount = new TreeMap<>();
        Map<String, Integer> gradeAmount = new LinkedHashMap<>();
        for (MemberGrade grade : MemberGrade.values()) {
            gradeAmount.put(grade.name(), 0);
        }

        for (Order order : all) {
            LocalDateTime createdAt = order.getCreatedAt();
            if (createdAt.getYear() != ym.getYear() || createdAt.getMonthValue() != ym.getMonthValue()) {
                continue;
            }
            if (order.getStatus() == OrderStatus.CREATED) {
                continue;
            }
            if (order.getStatus() == OrderStatus.CANCELED) {
                cancelAmount += order.getPayPrice();
                continue;
            }
            orderCount++;
            salesAmount += order.getPayPrice();

            String day = createdAt.toLocalDate().toString();
            dailyAmount.put(day, dailyAmount.getOrDefault(day, 0) + order.getPayPrice());

            String grade = order.getMember().getGrade().name();
            gradeAmount.put(grade, gradeAmount.get(grade) + order.getPayPrice());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yearMonth", ym.toString());
        result.put("orderCount", orderCount);
        result.put("salesAmount", salesAmount);
        result.put("cancelAmount", cancelAmount);
        result.put("averageOrderAmount", orderCount == 0 ? 0 : salesAmount / orderCount);
        result.put("daily", dailyAmount);
        result.put("byGrade", gradeAmount);
        return result;
    }

    /**
     * 인기 상품 TOP N (판매 수량 기준)
     */
    public List<Map<String, Object>> getTopProducts(int limit) {
        List<Order> all = orderRepository.findAll();

        Map<Long, Integer> quantityMap = new HashMap<>();
        Map<Long, Integer> amountMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();

        for (Order order : all) {
            if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.CANCELED) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                if (item.getStatus() == OrderItemStatus.CANCELED) {
                    continue;
                }
                Long productId = item.getProduct().getId();
                int qty = item.getQuantity() - item.getCanceledQuantity();
                quantityMap.put(productId, quantityMap.getOrDefault(productId, 0) + qty);
                amountMap.put(productId, amountMap.getOrDefault(productId, 0) + qty * item.getUnitPrice());
                nameMap.put(productId, item.getProduct().getName());
            }
        }

        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(quantityMap.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Long, Integer> entry : entries) {
            if (rank > limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put("productId", entry.getKey());
            row.put("productName", nameMap.get(entry.getKey()));
            row.put("quantity", entry.getValue());
            row.put("amount", amountMap.get(entry.getKey()));
            result.add(row);
            rank++;
        }
        return result;
    }

    /**
     * 회원 등급별 주문 통계
     */
    public Map<String, Object> getGradeStatistics() {
        List<Order> all = orderRepository.findAll();

        Map<String, Integer> countByGrade = new LinkedHashMap<>();
        Map<String, Integer> amountByGrade = new LinkedHashMap<>();
        for (MemberGrade grade : MemberGrade.values()) {
            countByGrade.put(grade.name(), 0);
            amountByGrade.put(grade.name(), 0);
        }

        for (Order order : all) {
            if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.CANCELED) {
                continue;
            }
            String grade = order.getMember().getGrade().name();
            countByGrade.put(grade, countByGrade.get(grade) + 1);
            amountByGrade.put(grade, amountByGrade.get(grade) + order.getPayPrice());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("countByGrade", countByGrade);
        result.put("amountByGrade", amountByGrade);
        return result;
    }

    /**
     * 주문 내역 CSV 다운로드 (정산팀 요청)
     */
    public String exportOrdersCsv(LocalDate from, LocalDate to) {
        List<Order> orders = orderRepository.findByCreatedAtBetween(DateUtil.startOfDay(from), DateUtil.endOfDay(to));

        StringBuilder sb = new StringBuilder();
        sb.append("주문번호,주문일시,회원명,등급,상태,상품명,단가,수량,취소수량,상품금액,할인,배송비,포인트,결제금액,받는분,연락처,주소\n");

        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                sb.append(order.getOrdNo()).append(",");
                sb.append(DateUtil.format(order.getCreatedAt())).append(",");
                sb.append(order.getMember().getName()).append(",");
                sb.append(order.getMember().getGrade()).append(",");
                sb.append(order.getStatus()).append(",");
                sb.append(item.getProductName()).append(",");
                sb.append(item.getUnitPrice()).append(",");
                sb.append(item.getQuantity()).append(",");
                sb.append(item.getCanceledQuantity()).append(",");
                sb.append(order.getTotalPrice()).append(",");
                sb.append(order.getDiscountPrice()).append(",");
                sb.append(order.getShippingFee()).append(",");
                sb.append(order.getUsedPoint()).append(",");
                sb.append(order.getPayPrice()).append(",");
                sb.append(order.getReceiverName()).append(",");
                sb.append(order.getReceiverPhone()).append(",");
                sb.append(order.getAddress()).append("\n");
            }
        }
        return sb.toString();
    }

    // =====================================================================
    // 금액 계산
    // =====================================================================

    private boolean hasCouponCode(OrderCreateRequest req) {
        return req.getCouponCode() != null && !req.getCouponCode().trim().isEmpty();
    }

    private void validateCoupon(Coupon coupon, String code) {
        if (coupon == null) {
            throw new IllegalArgumentException("존재하지 않는 쿠폰입니다. code=" + code.trim());
        }
        if (!coupon.isUsable(LocalDate.now())) {
            // 사용 불가 사유 구분 (판단 기준은 Coupon.isUsable)
            throw new IllegalStateException(coupon.isUsed() ? "이미 사용된 쿠폰입니다." : "만료된 쿠폰입니다.");
        }
    }

    // 주문 전체 취소 시 쿠폰 복원
    private void restoreCoupon(Order order) {
        Coupon coupon = order.getCoupon();
        if (coupon != null) {
            coupon.setUsed(false);
        }
    }

    private int calculateTotalPrice(List<OrderItem> items) {
        int total = 0;
        for (OrderItem item : items) {
            total += item.getUnitPrice() * item.getQuantity();
        }
        return total;
    }

    private int calculateDiscount(int totalPrice, MemberGrade grade) {
        int rate = 0;
        if (grade == MemberGrade.SILVER) {
            rate = 3;
        } else if (grade == MemberGrade.GOLD) {
            rate = 5;
        } else if (grade == MemberGrade.VIP) {
            rate = 10;
        }
        return totalPrice * rate / 100;
    }

    private int calculateShippingFee(int amount, MemberGrade grade) {
        if (grade == MemberGrade.VIP) {
            return 0;
        }
        if (amount >= Constants.FREE_SHIPPING_THRESHOLD) {
            return 0;
        }
        return Constants.SHIPPING_FEE;
    }

    // private int calculateShippingFee(int amount) {
    //     if (amount >= 30000) {
    //         return 0;
    //     }
    //     return 2500;
    // }

    /**
     * 주문 금액 미리보기 (장바구니 화면)
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> previewPrice(Long memberId, OrderCreateRequest req) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + memberId);
        }

        int totalPrice = 0;
        for (OrderCreateRequest.Item reqItem : req.getItems()) {
            Product product = productRepository.findById(reqItem.getProductId()).orElse(null);
            if (product == null) {
                throw new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + reqItem.getProductId());
            }
            totalPrice += product.getPrice() * reqItem.getQuantity();
        }

        int discountPrice = calculateDiscount(totalPrice, member.getGrade());

        // 쿠폰: 검증만 하고 사용 처리는 하지 않는다 (락 없음)
        int couponDiscount = 0;
        if (hasCouponCode(req)) {
            Coupon coupon = couponRepository.findFirstByCode(req.getCouponCode().trim()).orElse(null);
            validateCoupon(coupon, req.getCouponCode());
            couponDiscount = coupon.calcDiscount(totalPrice - discountPrice);
        }

        int shippingFee = calculateShippingFee(totalPrice - discountPrice - couponDiscount, member.getGrade());
        int usePoint = Math.min(req.getUsePoint(), member.getPoint());

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("totalPrice", totalPrice);
        result.put("discountPrice", discountPrice);
        result.put("couponDiscount", couponDiscount);
        result.put("shippingFee", shippingFee);
        result.put("usePoint", usePoint);
        result.put("payPrice", totalPrice - discountPrice - couponDiscount + shippingFee - usePoint);
        return result;
    }

    // =====================================================================
    // 검증
    // =====================================================================

    private void validateOrderRequest(OrderCreateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("주문 정보가 없습니다.");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("주문 상품이 없습니다.");
        }
        if (req.getItems().size() > 20) {
            throw new IllegalArgumentException("한 번에 20개 상품까지 주문할 수 있습니다.");
        }
        Set<Long> productIds = new HashSet<>();
        for (OrderCreateRequest.Item item : req.getItems()) {
            if (item.getProductId() == null) {
                throw new IllegalArgumentException("상품 ID가 없습니다.");
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
            }
            if (item.getQuantity() > Constants.MAX_ORDER_QUANTITY) {
                throw new IllegalArgumentException("상품당 최대 " + Constants.MAX_ORDER_QUANTITY + "개까지 주문할 수 있습니다.");
            }
            if (!productIds.add(item.getProductId())) {
                throw new IllegalArgumentException("중복된 상품이 있습니다. id=" + item.getProductId());
            }
        }
        if (req.getUsePoint() < 0) {
            throw new IllegalArgumentException("사용 포인트가 올바르지 않습니다.");
        }
        if (req.getReceiverName() == null || req.getReceiverName().trim().isEmpty()) {
            throw new IllegalArgumentException("받는 분 이름을 입력해주세요.");
        }
        if (req.getReceiverName().length() > 20) {
            throw new IllegalArgumentException("받는 분 이름은 20자 이내로 입력해주세요.");
        }
        if (req.getReceiverPhone() == null || !PHONE_PATTERN.matcher(req.getReceiverPhone()).matches()) {
            throw new IllegalArgumentException("휴대폰 번호 형식이 올바르지 않습니다.");
        }
        if (req.getAddress() == null || req.getAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("배송지 주소를 입력해주세요.");
        }
        if (req.getAddress().length() > 200) {
            throw new IllegalArgumentException("주소는 200자 이내로 입력해주세요.");
        }
        if (req.getMemo() != null && req.getMemo().length() > 100) {
            throw new IllegalArgumentException("배송 메모는 100자 이내로 입력해주세요.");
        }
    }

    // =====================================================================
    // 기타
    // =====================================================================

    private synchronized String generateOrdNo() {
        sequence++;
        return LocalDate.now().format(DateUtil.YYYYMMDD) + "-" + String.format("%06d", sequence);
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setOrderId(order.getId());
        res.setOrderNumber(order.getOrdNo());
        res.setStatus(order.getStatus().name());
        res.setMemberName(order.getMember().getName());
        res.setTotalPrice(order.getTotalPrice());
        res.setDiscountPrice(order.getDiscountPrice());
        res.setCouponDiscount(order.getCouponDiscount());
        res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        res.setShippingFee(order.getShippingFee());
        res.setUsedPoint(order.getUsedPoint());
        res.setPayPrice(order.getPayPrice());
        res.setReceiverName(order.getReceiverName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setAddress(order.getAddress());
        res.setMemo(order.getMemo());
        res.setTrackingNo(order.getTrackingNo());
        res.setCreatedAt(order.getCreatedAt());
        res.setPaidAt(order.getPaidAt());

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            itemResponses.add(toItemResponse(item));
        }
        res.setItems(itemResponses);
        return res;
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        OrderItemResponse res = new OrderItemResponse();
        res.setItemId(item.getId());
        res.setProductId(item.getProduct().getId());
        // 상품명이 변경된 경우 최신 상품명으로 표시 (2022.03 기획 요청)
        res.setProductName(item.getProduct().getName());
        res.setUnitPrice(item.getUnitPrice());
        res.setQuantity(item.getQuantity());
        res.setCanceledQuantity(item.getCanceledQuantity());
        res.setStatus(item.getStatus().name());
        return res;
    }

    // public OrderResponse createOrderV1(Long memberId, Long productId, int quantity) {
    //     Member member = memberRepository.findById(memberId).get();
    //     Product product = productRepository.findById(productId).get();
    //     if (product.getStock() < quantity) {
    //         throw new RuntimeException("재고 부족");
    //     }
    //     product.setStock(product.getStock() - quantity);
    //     Order order = new Order();
    //     order.setMember(member);
    //     order.setStatus(OrderStatus.CREATED);
    //     order.setTotalPrice(product.getPrice() * quantity);
    //     order.setPayPrice(product.getPrice() * quantity);
    //     orderRepository.save(order);
    //     return toResponse(order);
    // }
}
