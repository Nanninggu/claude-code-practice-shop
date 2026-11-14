package com.example.shop.service;

import com.example.shop.domain.MemberGrade;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderStatus;
import com.example.shop.domain.Payment;
import com.example.shop.domain.PaymentStatus;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.util.PriceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙: (상품 금액 - 할인) 이 50,000원 이상이면 배송비는 무료다. (경계값 50,000원 포함)
 * 기대값은 코드가 아니라 이 규칙에서 손으로 계산했다.
 */
@SpringBootTest
@Transactional
class FreeShippingThresholdTest {

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    @Test
    void 할인_뒤_금액이_정확히_50000원이면_배송비는_0원이다() {
        assertThat(PriceUtil.calcShippingFee(50000, MemberGrade.BRONZE)).isZero();
    }

    @Test
    void 브론즈가_50000원_상품을_주문하고_결제하면_배송비_없이_50000원으로_성공한다() {
        // 브론즈 할인 0, 50,000 >= 50,000 이므로 배송비 0 → 결제금액 50,000원
        OrderFixture fixture = new OrderFixture(orderService, memberRepository, productRepository, orderRepository);
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{50000, 1});

        Payment payment = paymentService.pay(order.getId(), "CARD", "1234-5678");

        assertThat(payment.getAmount()).isEqualTo(50000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
