package com.example.shop.service;

import com.example.shop.domain.*;
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
 * PaymentService.pay / refund 분기별 테스트.
 * 기대값은 코드 출력이 아니라 업무 규칙에서 손으로 계산했다.
 *  - 등급 할인: SILVER 3%, GOLD 5%, VIP 10%. 배송비 3,000원(할인 후 금액이 등급별 기준 이상이면 무료: 브론즈 5만/실버 4만/골드 3만 원, 경계 포함. VIP 는 항상 무료)
 *  - 포인트 적립: 결제금액의 일반 1%, GOLD 이상 2%
 *  - 환불은 고객이 낸 돈(결제금액)을 넘을 수 없다
 *
 * pay() 분기
 *   P1 주문 없음 / P2 상태가 CREATED 아님 / P3 금액 불일치 / P4 PG 승인 거절 / P5 승인 성공
 *   P6~P9 적립률: BRONZE 1%, SILVER 1%, GOLD 2%, VIP 2%
 * refund() 분기
 *   R1 결제 내역 없음 / R2 PG 취소 실패 / R3 누적 환불 == 결제금액 → CANCELED
 *   R4 누적 환불 < 결제금액 → PARTIAL_CANCELED / R5 누적 환불 합계가 결제금액 도달(2회)
 *   R6 [규칙] 1회 환불이 결제금액 초과 / R7 [규칙] 누적 환불이 결제금액 초과
 */
@SpringBootTest
@Transactional
class PaymentServiceTest {

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

    // ------------------------------------------------------------------ pay()

    @Test
    void P1_존재하지_않는_주문은_결제할_수_없다() {
        assertThatThrownBy(() -> paymentService.pay(NO_ORDER, "CARD", "1234-5678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    void P2_이미_결제된_주문은_다시_결제할_수_없다() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});
        paymentService.pay(order.getId(), "CARD", "1234-5678");

        assertThatThrownBy(() -> paymentService.pay(order.getId(), "CARD", "1234-5678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 가능한 주문 상태가 아닙니다");
    }

    @Test
    void P3_주문_금액이_위변조되면_결제가_거절되고_결제내역이_남지_않는다() {
        // 브론즈 10,000원 → 정답 13,000원. 저장된 금액을 100원으로 조작한다.
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});
        order.setPayPrice(100);

        assertThatThrownBy(() -> paymentService.pay(order.getId(), "CARD", "1234-5678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 금액이 일치하지 않습니다.");
        assertThat(paymentRepository.findByOrderId(order.getId())).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void P4_PG가_승인을_거절하면_FAILED로_기록되고_주문은_CREATED_그대로_포인트_적립_없음() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});

        assertThatThrownBy(() -> paymentService.pay(order.getId(), "CARD", "4000-1111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 실패: 카드사 승인 거절");

        assertThat(paymentRepository.findByOrderId(order.getId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getMember().getPoint()).isZero();
    }

    @Test
    void P5_승인_성공시_결제금액은_13000원_APPROVED_주문은_PAID() {
        // 브론즈 10,000원 + 배송비 3,000원 = 13,000원
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});

        Payment payment = paymentService.pay(order.getId(), "CARD", "1234-5678");

        assertThat(payment.getAmount()).isEqualTo(13000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTid()).isNotBlank();
        assertThat(payment.getCanceledAmount()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
    }

    @Test
    void P6_브론즈는_결제금액의_1퍼센트_130점_적립() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1}); // 13,000원
        paymentService.pay(order.getId(), "CARD", "1234-5678");
        assertThat(order.getMember().getPoint()).isEqualTo(130);
    }

    @Test
    void P7_실버는_결제금액의_1퍼센트_127점_적립() {
        // 10,000 - 300(3%) + 3,000 = 12,700원 → 127점
        Order order = fixture.place(MemberGrade.SILVER, 0, 0, new int[]{10000, 1});
        paymentService.pay(order.getId(), "CARD", "1234-5678");
        assertThat(order.getMember().getPoint()).isEqualTo(127);
    }

    @Test
    void P8_골드는_결제금액의_2퍼센트_250점_적립() {
        // 10,000 - 500(5%) + 3,000 = 12,500원 → 250점
        Order order = fixture.place(MemberGrade.GOLD, 0, 0, new int[]{10000, 1});
        paymentService.pay(order.getId(), "CARD", "1234-5678");
        assertThat(order.getMember().getPoint()).isEqualTo(250);
    }

    @Test
    void P9_VIP는_결제금액의_2퍼센트_180점_적립() {
        // 10,000 - 1,000(10%) + 배송비 0 = 9,000원 → 180점
        Order order = fixture.place(MemberGrade.VIP, 0, 0, new int[]{10000, 1});
        paymentService.pay(order.getId(), "CARD", "1234-5678");
        assertThat(order.getMember().getPoint()).isEqualTo(180);
    }

    // ------------------------------------------------------------------ refund()

    @Test
    void R1_결제내역이_없으면_환불할_수_없다() {
        assertThatThrownBy(() -> paymentService.refund(NO_ORDER, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 내역이 없습니다");
    }

    @Test
    void R2_PG가_취소를_실패시키면_환불이_반영되지_않는다() {
        // FakePgClient 는 거래번호가 null 이면 취소 실패를 돌려준다
        Payment payment = savePayment(NO_ORDER, null, 10000);

        assertThatThrownBy(() -> paymentService.refund(NO_ORDER, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("환불 실패: 거래번호 없음");
        assertThat(payment.getCanceledAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void R3_결제금액_전액을_환불하면_CANCELED() {
        Payment payment = savePayment(NO_ORDER, "T-1", 10000);

        paymentService.refund(NO_ORDER, 10000);

        assertThat(payment.getCanceledAmount()).isEqualTo(10000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isNotNull();
    }

    @Test
    void R4_일부만_환불하면_PARTIAL_CANCELED() {
        Payment payment = savePayment(NO_ORDER, "T-1", 10000);

        paymentService.refund(NO_ORDER, 4000);

        assertThat(payment.getCanceledAmount()).isEqualTo(4000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
    }

    @Test
    void R5_나눠서_환불해_합계가_결제금액에_도달하면_CANCELED() {
        Payment payment = savePayment(NO_ORDER, "T-1", 10000);

        paymentService.refund(NO_ORDER, 4000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        paymentService.refund(NO_ORDER, 6000);

        assertThat(payment.getCanceledAmount()).isEqualTo(10000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void R6_결제금액보다_많은_금액은_한번에_환불할_수_없다() {
        Payment payment = savePayment(NO_ORDER, "T-1", 10000);

        assertThatThrownBy(() -> paymentService.refund(NO_ORDER, 10001))
                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class);
        assertThat(payment.getCanceledAmount()).isZero();
    }

    @Test
    void R7_누적_환불액이_결제금액을_넘을_수_없다() {
        Payment payment = savePayment(NO_ORDER, "T-1", 10000);
        paymentService.refund(NO_ORDER, 6000);

        assertThatThrownBy(() -> paymentService.refund(NO_ORDER, 5000))
                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class);
        assertThat(payment.getCanceledAmount()).isEqualTo(6000);
    }

    private Payment savePayment(long orderId, String tid, int amount) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setPgTid(tid);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setMethod(PaymentMethod.CARD);
        payment.setCreatedAt(java.time.LocalDateTime.now());
        return paymentRepository.save(payment);
    }
}
