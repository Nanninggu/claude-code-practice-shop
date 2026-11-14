package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.PaymentRepository;
import com.example.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderService.partialCancel 분기별 테스트. 기대값은 업무 규칙에서 계산했다.
 *  - 환불은 고객이 낸 돈을 넘을 수 없다
 *  - 등급 할인을 받은 주문은 할인 몫을 뺀 금액을 돌려준다
 *      (취소 항목 금액 - 등급할인액 × 취소항목금액 / 상품합계)
 *
 * 분기
 *   C1 주문 없음 / C2 상태가 PAID·PARTIAL_CANCELED 아님(CREATED, CANCELED) / C3 PAID 허용 / C4 PARTIAL_CANCELED 허용
 *   C5 항목 없음 / C6 수량 0 이하 / C7 수량이 남은 수량 초과
 *   C8 항목 전량 취소 → 항목 CANCELED / C9 일부만 취소 → 항목 ORDERED 유지
 *   C10 모든 항목 취소 → 주문 CANCELED / C11 일부 항목만 취소 → 주문 PARTIAL_CANCELED
 *   C12 [규칙] 할인 몫 제외 환불 / C13 [규칙] 전량 취소 시 할인 몫 제외 / C14 [규칙] 누적 환불이 결제금액 초과 불가
 */
@SpringBootTest
@Transactional
class OrderPartialCancelTest {

    private static final long NO_ORDER = 987_654_321L;

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;

    OrderFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OrderFixture(orderService, memberRepository, productRepository, orderRepository);
    }

    @Test
    void C1_존재하지_않는_주문은_부분취소할_수_없다() {
        assertThatThrownBy(() -> orderService.partialCancel(NO_ORDER, 1L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    void C2a_결제_전_CREATED_주문은_부분취소할_수_없다() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 2});

        assertThatThrownBy(() -> orderService.partialCancel(order.getId(), order.getItems().get(0).getId(), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 완료된 주문만 부분 취소할 수 있습니다.");
    }

    @Test
    void C2b_이미_전체취소된_주문은_부분취소할_수_없다() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 1});
        Long itemId = order.getItems().get(0).getId();
        orderService.partialCancel(order.getId(), itemId, 1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

        assertThatThrownBy(() -> orderService.partialCancel(order.getId(), itemId, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 완료된 주문만 부분 취소할 수 있습니다.");
    }

    @Test
    void C3_PAID_주문은_부분취소할_수_있고_재고가_복구된다() {
        // 브론즈 10,000원 × 2 (재고 10 → 8). 1개 취소하면 환불 10,000원, 재고 9.
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 2});
        OrderItem item = order.getItems().get(0);
        assertThat(item.getProduct().getStock()).isEqualTo(8);

        orderService.partialCancel(order.getId(), item.getId(), 1);

        assertThat(paymentRepository.findByOrderId(order.getId()).getCanceledAmount()).isEqualTo(10000);
        assertThat(item.getCanceledQuantity()).isEqualTo(1);
        assertThat(item.getProduct().getStock()).isEqualTo(9);
    }

    @Test
    void C4_PARTIAL_CANCELED_주문도_추가로_부분취소할_수_있다() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 3});
        Long itemId = order.getItems().get(0).getId();

        orderService.partialCancel(order.getId(), itemId, 1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
        orderService.partialCancel(order.getId(), itemId, 1);

        assertThat(order.getItems().get(0).getCanceledQuantity()).isEqualTo(2);
        assertThat(paymentRepository.findByOrderId(order.getId()).getCanceledAmount()).isEqualTo(20000);
    }

    @Test
    void C5_주문에_없는_상품은_부분취소할_수_없다() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 2});

        assertThatThrownBy(() -> orderService.partialCancel(order.getId(), -1L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문 상품을 찾을 수 없습니다");
    }

    @Test
    void C6_취소_수량이_0이하면_거절된다() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 2});
        Long itemId = order.getItems().get(0).getId();

        assertThatThrownBy(() -> orderService.partialCancel(order.getId(), itemId, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.partialCancel(order.getId(), itemId, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(paymentRepository.findByOrderId(order.getId()).getCanceledAmount()).isZero();
    }

    @Test
    void C7_남은_수량보다_많이_취소하면_거절된다() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 2});
        Long itemId = order.getItems().get(0).getId();

        assertThatThrownBy(() -> orderService.partialCancel(order.getId(), itemId, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("남은 수량=2");
        assertThat(paymentRepository.findByOrderId(order.getId()).getCanceledAmount()).isZero();
    }

    @Test
    void C8_항목의_남은_수량을_모두_취소하면_항목이_CANCELED() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 2}, new int[]{5000, 1});
        OrderItem first = order.getItems().get(0);

        orderService.partialCancel(order.getId(), first.getId(), 2);

        assertThat(first.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(first.getCanceledQuantity()).isEqualTo(2);
    }

    @Test
    void C9_항목의_일부만_취소하면_항목은_ORDERED_유지() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 2});
        OrderItem item = order.getItems().get(0);

        orderService.partialCancel(order.getId(), item.getId(), 1);

        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
        assertThat(item.getCanceledQuantity()).isEqualTo(1);
    }

    @Test
    void C10_모든_항목이_취소되면_주문도_CANCELED() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 1}, new int[]{5000, 1});

        orderService.partialCancel(order.getId(), order.getItems().get(0).getId(), 1);
        orderService.partialCancel(order.getId(), order.getItems().get(1).getId(), 1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
    }

    @Test
    void C11_일부_항목만_취소되면_주문은_PARTIAL_CANCELED() {
        Order order = paidOrder(MemberGrade.BRONZE, new int[]{10000, 1}, new int[]{5000, 1});

        orderService.partialCancel(order.getId(), order.getItems().get(0).getId(), 1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
        assertThat(order.getCanceledAt()).isNull();
    }

    // ------------------------------------------------------------ 업무 규칙 검증

    @Test
    void C12_할인받은_주문을_일부_취소하면_할인_몫을_뺀_9700원을_환불한다() {
        // 할인 600 = 20,000 × 3%. 1개(10,000원)의 할인 몫 = 600 × 10,000 / 20,000 = 300 → 환불 9,700원
        Order order = paidOrder(MemberGrade.SILVER, new int[]{10000, 2});
        assertThat(order.getPayPrice()).isEqualTo(22400);

        orderService.partialCancel(order.getId(), order.getItems().get(0).getId(), 1);

        assertThat(paymentRepository.findByOrderId(order.getId()).getCanceledAmount()).isEqualTo(9700);
    }

    @Test
    void C13_할인받은_주문을_전량_취소하면_상품금액에서_할인액을_뺀_19000원을_환불한다() {
        // 20,000 - 1,000(5%) + 배송비 3,000 = 22,000원 결제. 배송비 환불 정책은 규칙에 없으므로 상품분만 검증한다.
        Order order = paidOrder(MemberGrade.GOLD, new int[]{20000, 1});
        assertThat(order.getPayPrice()).isEqualTo(22000);

        orderService.partialCancel(order.getId(), order.getItems().get(0).getId(), 1);

        assertThat(paymentRepository.findByOrderId(order.getId()).getCanceledAmount()).isEqualTo(19000);
    }

    @Test
    void C14_누적_환불액이_실결제금액을_넘을_수_없다() {
        // 40,000 + 배송비 3,000 - 포인트 20,000 = 23,000원 결제
        Order order = fixture.place(MemberGrade.BRONZE, 20000, 20000, new int[]{20000, 2});
        assertThat(order.getPayPrice()).isEqualTo(23000);
        paymentService.pay(order.getId(), "CARD", "1234-5678");
        Long itemId = order.getItems().get(0).getId();

        orderService.partialCancel(order.getId(), itemId, 1);
        try {
            orderService.partialCancel(order.getId(), itemId, 1);
        } catch (IllegalArgumentException | IllegalStateException expectedRejection) {
            // 거절되어도 규칙은 지켜진 것이다.
        }

        Payment payment = paymentRepository.findByOrderId(order.getId());
        assertThat(payment.getCanceledAmount()).isLessThanOrEqualTo(payment.getAmount());
    }

    // ------------------------------------------------------------ helpers

    /** 쿠폰/포인트 없이 주문하고 결제까지 마친 PAID 주문. */
    private Order paidOrder(MemberGrade grade, int[]... lines) {
        Order order = fixture.place(grade, 0, 0, lines);
        paymentService.pay(order.getId(), "CARD", "1234-5678");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        return order;
    }
}
