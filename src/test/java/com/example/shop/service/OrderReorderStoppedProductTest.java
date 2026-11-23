package com.example.shop.service;

import com.example.shop.domain.MemberGrade;
import com.example.shop.domain.Order;
import com.example.shop.domain.Product;
import com.example.shop.domain.ProductStatus;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 판매중지(STOPPED) 상품이 든 주문은 재주문할 수 없다. */
@SpringBootTest
@Transactional
class OrderReorderStoppedProductTest {

    @Autowired OrderService orderService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    OrderFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OrderFixture(orderService, memberRepository, productRepository, orderRepository);
    }

    @Test
    void 판매중지_상품이_든_주문은_재주문할_수_없다() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});
        Product product = order.getItems().get(0).getProduct();
        product.setStatus(ProductStatus.STOPPED);
        productRepository.save(product);
        int stockBefore = product.getStock();
        long orderCountBefore = orderRepository.count();

        assertThatThrownBy(() -> orderService.reOrder(order.getMember().getId(), order.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("판매중지");

        assertThat(orderRepository.count()).isEqualTo(orderCountBefore);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(stockBefore);
    }

    @Test
    void 판매중인_상품만_든_주문은_재주문된다() {
        Order order = fixture.place(MemberGrade.BRONZE, 0, 0, new int[]{10000, 1});

        var res = orderService.reOrder(order.getMember().getId(), order.getId());

        assertThat(res.getOrderId()).isNotEqualTo(order.getId());
    }
}
