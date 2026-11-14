package com.example.shop.service;

import com.example.shop.domain.Order;
import com.example.shop.domain.MemberGrade;
import com.example.shop.domain.OrderStatus;
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

/**
 * 버그 재현: 결제 전(CREATED, Payment 없음) 주문을 취소하면
 * OrderService.cancelOrder 에서 payment 가 null 이라 NullPointerException 이 난다.
 * 기대 동작은 "예외 없이 주문이 CANCELED 가 되는 것"이다.
 */
@SpringBootTest
@Transactional
class OrderCancelUnpaidTest {

    @Autowired OrderService orderService;
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
    void 결제_전_CREATED_주문도_취소할_수_있다() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 2});
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(paymentRepository.findByOrderId(order.getId())).isNull();

        orderService.cancelOrder(order.getId(), order.getMember().getId(), "단순 변심");

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELED);
    }
}
