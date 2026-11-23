package com.example.shop.service;

import com.example.shop.domain.MemberGrade;
import com.example.shop.domain.Order;
import com.example.shop.dto.OrderResponse;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 관리자 주문 검색의 SQL 인젝션 방어 검증. */
@SpringBootTest
@Transactional
class AdminOrderSearchInjectionTest {

    @Autowired AdminOrderService adminOrderService;
    @Autowired OrderService orderService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    Order order;

    @BeforeEach
    void setUp() {
        OrderFixture fixture = new OrderFixture(orderService, memberRepository, productRepository, orderRepository);
        order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});
    }

    @Test
    void 정상_키워드_검색은_동작한다() {
        List<OrderResponse> r = adminOrderService.searchOrdersForAdmin(null, "홍길동", null, null);
        assertThat(r).extracting(OrderResponse::getOrderId).contains(order.getId());
    }

    @Test
    void keyword_인젝션은_전체조회로_우회되지_않는다() {
        List<OrderResponse> r = adminOrderService.searchOrdersForAdmin(null, "zzz%' or '1'='1", null, null);
        assertThat(r).isEmpty();
    }

    @Test
    void status_인젝션은_거부된다() {
        assertThatThrownBy(() -> adminOrderService.searchOrdersForAdmin("CREATED' or '1'='1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 날짜_인젝션은_거부된다() {
        assertThatThrownBy(() -> adminOrderService.searchOrdersForAdmin(null, null, "2020-01-01' or '1'='1", null))
                .isInstanceOf(RuntimeException.class);
    }
}
