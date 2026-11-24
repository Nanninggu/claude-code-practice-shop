package com.example.shop.config;

import com.example.shop.domain.*;
import com.example.shop.repository.CouponRepository;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.PaymentRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.util.DateUtil;
import com.example.shop.util.PriceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 로컬 개발용 초기 데이터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;

    private int seedSeq = 900000;

    @Override
    @Transactional
    public void run(String... args) {
        List<Member> members = new ArrayList<>();
        members.add(member("minjun@example.com", "김민준", "010-1111-2222", MemberGrade.BRONZE, 0));
        members.add(member("seoyeon@example.com", "이서연", "010-2222-3333", MemberGrade.SILVER, 5000));
        members.add(member("jiho@example.com", "박지호", "010-3333-4444", MemberGrade.GOLD, 12000));
        members.add(member("sua@example.com", "최수아", "010-4444-5555", MemberGrade.VIP, 30000));
        members.add(member("doyun@example.com", "정도윤", "010-5555-6666", MemberGrade.SILVER, 2000));
        memberRepository.saveAll(members);

        List<Product> products = new ArrayList<>();
        products.add(product("무선 이어폰", "전자기기", 89000, 50, ProductStatus.ON_SALE));
        products.add(product("보조배터리 10000mAh", "전자기기", 23900, 100, ProductStatus.ON_SALE));
        products.add(product("USB-C 케이블", "전자기기", 8950, 300, ProductStatus.ON_SALE));
        products.add(product("텀블러 500ml", "생활", 12350, 80, ProductStatus.ON_SALE));
        products.add(product("한정판 텀블러 (스노우)", "생활", 25000, 10, ProductStatus.ON_SALE));
        products.add(product("원두 1kg", "식품", 32000, 60, ProductStatus.ON_SALE));
        products.add(product("드립백 커피 10입", "식품", 12900, 120, ProductStatus.ON_SALE));
        products.add(product("그래놀라 500g", "식품", 9800, 90, ProductStatus.ON_SALE));
        products.add(product("프리미엄 선물세트", "식품", 50000, 30, ProductStatus.ON_SALE));
        products.add(product("노트 A5", "문구", 3450, 200, ProductStatus.ON_SALE));
        products.add(product("볼펜 세트", "문구", 6500, 150, ProductStatus.ON_SALE));
        products.add(product("데스크 매트", "문구", 19900, 40, ProductStatus.ON_SALE));
        products.add(product("캠핑 의자", "스포츠", 45000, 25, ProductStatus.ON_SALE));
        products.add(product("요가 매트", "스포츠", 28000, 35, ProductStatus.ON_SALE));
        products.add(product("등산 양말 3켤레", "스포츠", 11900, 100, ProductStatus.ON_SALE));
        products.add(product("블루투스 스피커 (구형)", "전자기기", 39000, 15, ProductStatus.STOPPED));
        products.add(product("스마트워치 밴드", "전자기기", 15000, 0, ProductStatus.SOLD_OUT));
        products.add(product("디퓨저", "생활", 22000, 45, ProductStatus.ON_SALE));
        products.add(product("핸드크림 세트", "생활", 13750, 70, ProductStatus.ON_SALE));
        products.add(product("수건 5장 세트", "생활", 18000, 55, ProductStatus.ON_SALE));
        productRepository.saveAll(products);

        // 쿠폰 시드
        LocalDate nextYear = LocalDate.now().plusYears(1);
        couponRepository.save(coupon("WELCOME3000", CouponType.FIXED, 3000, nextYear));
        couponRepository.save(coupon("SAVE10", CouponType.PERCENT, 10, nextYear));
        couponRepository.save(coupon("OLD2024", CouponType.FIXED, 3000, LocalDate.of(2024, 12, 31)));

        LocalDateTime now = LocalDateTime.now();

        // 1번 주문: 결제 대기 상태 주문
        Order order1 = order(members.get(0), OrderStatus.CREATED, now.minusMinutes(5));
        addItem(order1, products.get(1), 1);
        addItem(order1, products.get(2), 2);
        finish(order1);

        // 2번 주문: 결제 완료 주문
        Order order2 = order(members.get(1), OrderStatus.PAID, now.minusHours(2));
        addItem(order2, products.get(0), 1);
        finish(order2);
        pay(order2, false);

        // 3번 주문: 배송 완료된 주문
        Order order3 = order(members.get(2), OrderStatus.DELIVERED, now.minusDays(20));
        addItem(order3, products.get(15), 1);
        addItem(order3, products.get(5), 1);
        finish(order3);
        pay(order3, false);

        // 과거 주문 데이터
        Random random = new Random(42);
        OrderStatus[] statuses = {
                OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED,
                OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.CONFIRMED,
                OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, OrderStatus.CANCELED
        };
        int[] candidates = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18, 19};

        for (int i = 0; i < 300; i++) {
            Member member = members.get(random.nextInt(members.size()));
            OrderStatus status = statuses[random.nextInt(statuses.length)];
            LocalDateTime createdAt = now.minusDays(random.nextInt(90)).minusMinutes(random.nextInt(600) + 60);

            Order order = order(member, status, createdAt);
            int itemCount = random.nextInt(3) + 1;
            List<Integer> used = new ArrayList<>();
            for (int j = 0; j < itemCount; j++) {
                int idx = candidates[random.nextInt(candidates.length)];
                if (used.contains(idx)) {
                    continue;
                }
                used.add(idx);
                addItem(order, products.get(idx), random.nextInt(3) + 1);
            }
            finish(order);
            pay(order, status == OrderStatus.CANCELED);
        }

        log.info("초기 데이터 생성 완료: 회원 {}명, 상품 {}개, 주문 {}건",
                memberRepository.count(), productRepository.count(), orderRepository.count());
    }

    private Coupon coupon(String code, CouponType type, int value, LocalDate expiresAt) {
        Coupon c = new Coupon();
        c.setCode(code);
        c.setType(type);
        c.setDiscountValue(value);
        c.setExpiresAt(expiresAt);
        c.setUsed(false);
        return c;
    }

    private Member member(String email, String name, String phone, MemberGrade grade, int point) {
        Member m = new Member();
        m.setEmail(email);
        m.setName(name);
        m.setPhone(phone);
        m.setGrade(grade);
        m.setPoint(point);
        m.setCreatedAt(LocalDateTime.now().minusYears(1));
        return m;
    }

    private Product product(String name, String category, int price, int stock, ProductStatus status) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setStock(stock);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.now().minusMonths(6));
        p.setUpdatedAt(LocalDateTime.now().minusMonths(6));
        return p;
    }

    private Order order(Member member, OrderStatus status, LocalDateTime createdAt) {
        Order o = new Order();
        o.setOrdNo(createdAt.format(DateUtil.YYYYMMDD) + "-" + (seedSeq++));
        o.setMember(member);
        o.setStatus(status);
        o.setReceiverName(member.getName());
        o.setReceiverPhone(member.getPhone());
        o.setAddress("서울시 강남구 테헤란로 " + (100 + seedSeq % 300) + "길 12");
        o.setCreatedAt(createdAt);
        o.setUpdatedAt(createdAt);
        if (status == OrderStatus.CANCELED) {
            o.setCancelReason("단순 변심");
            o.setCanceledAt(createdAt.plusHours(1));
        }
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED || status == OrderStatus.CONFIRMED) {
            o.setTrackingNo("6" + String.format("%011d", seedSeq));
        }
        return o;
    }

    private void addItem(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(quantity);
        item.setCanceledQuantity(order.getStatus() == OrderStatus.CANCELED ? quantity : 0);
        item.setStatus(order.getStatus() == OrderStatus.CANCELED ? OrderItemStatus.CANCELED : OrderItemStatus.ORDERED);
        order.getItems().add(item);
    }

    private void finish(Order order) {
        int total = 0;
        for (OrderItem item : order.getItems()) {
            total += item.getUnitPrice() * item.getQuantity();
        }
        MemberGrade grade = order.getMember().getGrade();
        int rate = grade == MemberGrade.SILVER ? 3 : grade == MemberGrade.GOLD ? 5 : grade == MemberGrade.VIP ? 10 : 0;
        int discount = total * rate / 100;
        int shipping = PriceUtil.calcShippingFee(total - discount, grade);
        order.setTotalPrice(total);
        order.setDiscountPrice(discount);
        order.setShippingFee(shipping);
        order.setUsedPoint(0);
        order.setPayPrice(total - discount + shipping);
        if (order.getStatus() != OrderStatus.CREATED) {
            order.setPaidAt(order.getCreatedAt().plusMinutes(3));
        }
        orderRepository.save(order);
    }

    private void pay(Order order, boolean canceled) {
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setPgTid("TSEED" + order.getId());
        p.setAmount(order.getPayPrice());
        p.setMethod(PaymentMethod.CARD);
        p.setCardNo("9410-1234-5678-" + String.format("%04d", order.getId() % 10000));
        p.setCreatedAt(order.getCreatedAt().plusMinutes(3));
        p.setApprovedAt(order.getCreatedAt().plusMinutes(3));
        if (canceled) {
            p.setStatus(PaymentStatus.CANCELED);
            p.setCanceledAmount(order.getPayPrice());
            p.setCanceledAt(order.getCanceledAt());
        } else {
            p.setStatus(PaymentStatus.APPROVED);
        }
        paymentRepository.save(p);
    }
}
