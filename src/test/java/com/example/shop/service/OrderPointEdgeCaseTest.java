package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포인트를 쓰는 주문/결제의 엣지 케이스. 기대값은 코드가 아니라 업무 상식에서 적었다.
 *
 * - 기대와 다르게 나온 테스트는 이름 앞에 QUIRK_ 를 붙이고 @Disabled 로 건너뛴다.
 * - 업무 규칙이 없어서 맞고 틀림을 정할 수 없는 케이스는 여기서 단정하지 않는다(결과 보고서에 따로 모음).
 */
@SpringBootTest
@Transactional
class OrderPointEdgeCaseTest {

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    OrderFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OrderFixture(orderService, memberRepository, productRepository, orderRepository);
    }

    // ------------------------------------------------ 보유 포인트와 사용 포인트가 맞닿는 경계

    @Test
    void E1_보유_포인트와_똑같이_사용하면_주문되고_잔액은_0() {
        // 20,000원 + 배송비 3,000원 - 5,000점 = 18,000원
        Order order = fixture.place(MemberGrade.BRONZE, 5000, 5000, new int[]{20000, 1});

        assertThat(order.getUsedPoint()).isEqualTo(5000);
        assertThat(order.getPayPrice()).isEqualTo(18000);
        assertThat(order.getMember().getPoint()).isZero();
    }

    @Test
    void E2_보유_포인트보다_1점_더_쓰면_거절되고_포인트는_그대로() {
        assertThatThrownBy(() -> fixture.place(MemberGrade.BRONZE, 5000, 5001, new int[]{20000, 1}))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------ 최소 사용 포인트(1,000점) 경계

    @Test
    void E3_999점은_거절된다() {
        assertThatThrownBy(() -> fixture.place(MemberGrade.BRONZE, 5000, 999, new int[]{20000, 1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void E4_1000점은_사용할_수_있다() {
        Order order = fixture.place(MemberGrade.BRONZE, 5000, 1000, new int[]{20000, 1});

        assertThat(order.getUsedPoint()).isEqualTo(1000);
        assertThat(order.getPayPrice()).isEqualTo(22000);
        assertThat(order.getMember().getPoint()).isEqualTo(4000);
    }

    // ------------------------------------------------ 결제 금액 0원

    @Disabled("규칙 위반: 포인트가 결제 대상 금액 이상이면 payPrice 가 0원이 되는데 주문은 만들어지고, "
            + "pay() 는 FakePgClient 가 0원을 '결제 금액 오류'로 거절해 IllegalStateException. 주문은 CREATED 로 남아 결제 불가.")
    @Test
    void QUIRK_E5_포인트로_전액_충당한_주문이_결제도_못하고_주문도_거절되지_않아_막다른_길() {
        // 업무 상식: 주문이 만들어졌다면 결제할 수 있어야 한다. (포인트 전액 결제를 막는 거라면 주문 단계에서 거절해야 한다.)
        Order order;
        try {
            order = fixture.place(MemberGrade.BRONZE, 20000, 20000, new int[]{10000, 1});
        } catch (RuntimeException rejectedAtOrder) {
            return; // 주문 단계에서 거절하는 것도 허용되는 설계
        }

        Payment payment = paymentService.pay(order.getId(), "CARD", "1234-5678");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ------------------------------------------------ 미결제 주문과 포인트

    @Test
    void E6_29분_된_미결제_주문은_아직_만료되지_않는다() {
        Order order = fixture.place(MemberGrade.BRONZE, 20000, 5000, new int[]{20000, 1});
        order.setCreatedAt(LocalDateTime.now().minusMinutes(29));

        orderService.expireUnpaidOrders();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getMember().getPoint()).isEqualTo(15000);
    }

    @Test
    void QUIRK_E7_31분_지나_만료된_주문은_쓴_포인트를_돌려줘야_한다() {
        Order order = fixture.place(MemberGrade.BRONZE, 20000, 5000, new int[]{20000, 1});
        order.setCreatedAt(LocalDateTime.now().minusMinutes(31));

        orderService.expireUnpaidOrders();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getMember().getPoint()).isEqualTo(20000);
    }

    @Disabled("규칙 위반: 결제 전(CREATED) 주문을 cancelOrder 하면 Payment 가 null 인데 payment.getStatus() 를 바로 호출해 "
            + "NullPointerException. 취소도 포인트 복구도 되지 않는다.")
    @Test
    void QUIRK_E8_결제_전_주문도_취소할_수_있고_쓴_포인트가_돌아온다() {
        Order order = fixture.place(MemberGrade.BRONZE, 20000, 5000, new int[]{20000, 1});

        orderService.cancelOrder(order.getId(), order.getMember().getId(), "단순 변심");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getMember().getPoint()).isEqualTo(20000);
    }

    // ------------------------------------------------ 결제 후 전체 취소

    @Test
    void E9_포인트를_쓰고_결제한_주문을_전체_취소하면_보유_포인트가_원래대로_돌아온다() {
        // 결제로 적립된 포인트는 회수되고, 쓴 포인트는 복구된다.
        Order order = fixture.place(MemberGrade.BRONZE, 20000, 5000, new int[]{20000, 1});
        paymentService.pay(order.getId(), "CARD", "1234-5678");

        orderService.cancelOrder(order.getId(), order.getMember().getId(), "단순 변심");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getMember().getPoint()).isEqualTo(20000);
    }

    // ------------------------------------------------ 가격 미리보기 vs 실제 주문

    @Disabled("규칙 위반: previewPrice 는 usePoint 를 min(요청, 보유) 로만 제한하고 결제 대상 금액 상한을 적용하지 않아 "
            + "10,000원 상품에 99,999점을 요청하면 payPrice 가 -86,999 로 나온다.")
    @Test
    void QUIRK_E10_결제금액을_넘는_포인트를_요청하면_미리보기도_0원이_되어야_하고_음수가_나오면_안_된다() {
        Order seed = fixture.place(MemberGrade.BRONZE, 100000, 0, new int[]{10000, 1});
        OrderCreateRequest req = requestFor(seed, 99999);

        Map<String, Integer> preview = orderService.previewPrice(seed.getMember().getId(), req);

        assertThat(preview.get("payPrice")).isGreaterThanOrEqualTo(0);
    }

    @Disabled("규칙 위반: 같은 입력에서 미리보기는 payPrice -86,999 / usePoint 99,999, 실제 주문은 payPrice 0 / usedPoint 13,000 "
            + "(createOrder 는 maxUsable 로 깎는다). 미리보기가 실제 주문과 다른 금액을 보여 준다.")
    @Test
    void QUIRK_E11_같은_입력이면_미리보기_금액과_실제_주문_결제금액이_같아야_한다() {
        Order seed = fixture.place(MemberGrade.BRONZE, 100000, 0, new int[]{10000, 1});
        OrderCreateRequest req = requestFor(seed, 99999);

        Map<String, Integer> preview = orderService.previewPrice(seed.getMember().getId(), req);
        Long orderId = orderService.createOrder(seed.getMember().getId(), req).getOrderId();
        Order created = orderRepository.findById(orderId).orElseThrow();

        assertThat(preview.get("payPrice")).isEqualTo(created.getPayPrice());
        assertThat(preview.get("usePoint")).isEqualTo(created.getUsedPoint());
    }

    private OrderCreateRequest requestFor(Order seed, int usePoint) {
        OrderCreateRequest.Item item = new OrderCreateRequest.Item();
        item.setProductId(seed.getItems().get(0).getProduct().getId());
        item.setQuantity(1);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(item));
        req.setUsePoint(usePoint);
        req.setReceiverName("홍길동");
        req.setReceiverPhone("010-1234-5678");
        req.setAddress("서울시 어딘가 1");
        return req;
    }
}
