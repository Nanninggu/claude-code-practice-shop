package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.dto.*;
import com.example.shop.repository.*;
import com.example.shop.util.DateUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * 관리자 주문 검색 / 통계 / CSV 내보내기 (OrderService 에서 분리)
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;

    private final OrderItemRepository orderItemRepository;

    private final OrderService orderService;

    private final EntityManager em;

    /**
     * 관리자 주문 검색
     * status: 주문상태 (선택), keyword: 주문번호 또는 받는분 이름 (선택)
     */
    @SuppressWarnings("unchecked")
    public List<OrderResponse> searchOrdersForAdmin(String status, String keyword, String fromDate, String toDate) {
        StringBuilder sql = new StringBuilder("select * from orders o where 1=1");
        Map<String, Object> params = new HashMap<>();
        if (status != null && !status.isEmpty()) {
            // 허용된 enum 값만 통과 (잘못된 값은 IllegalArgumentException -> 400)
            sql.append(" and o.status = :status");
            params.put("status", OrderStatus.valueOf(status).name());
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" and (o.ord_no like :keyword or o.receiver_name like :keyword)");
            params.put("keyword", "%" + keyword + "%");
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append(" and o.created_at >= :fromDate");
            params.put("fromDate", DateUtil.startOfDay(LocalDate.parse(fromDate)));
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append(" and o.created_at <= :toDate");
            params.put("toDate", DateUtil.endOfDay(LocalDate.parse(toDate)));
        }
        sql.append(" order by o.id desc");

        log.debug("관리자 검색 SQL: {} params={}", sql, params);

        Query query = em.createNativeQuery(sql.toString(), Order.class);
        params.forEach(query::setParameter);
        List<Order> orders = query.getResultList();

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
        if (limit <= 0) {
            return new ArrayList<>();
        }
        List<Object[]> rows = orderItemRepository.aggregateSalesByProduct(
                List.of(OrderStatus.CREATED, OrderStatus.CANCELED),
                OrderItemStatus.CANCELED,
                PageRequest.of(0, limit));

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("productId", r[0]);
            row.put("productName", r[1]);
            row.put("quantity", ((Number) r[2]).intValue());
            row.put("amount", ((Number) r[3]).intValue());
            result.add(row);
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

    private OrderResponse toResponse(Order order) {
        return orderService.toResponse(order);
    }
}
