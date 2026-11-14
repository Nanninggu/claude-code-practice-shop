package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 결제/환불/부분취소 테스트가 공유하는 주문 생성 도우미 (실제 OrderService.createOrder 를 통해 만든다). */
class OrderFixture {

    private final OrderService orderService;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    OrderFixture(OrderService orderService, MemberRepository memberRepository,
                 ProductRepository productRepository, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    /** {단가, 수량} 쌍들로 주문을 만든다. 쿠폰 없음. */
    Order place(MemberGrade grade, int memberPoint, int usePoint, int[]... lines) {
        Member member = new Member();
        member.setEmail(UUID.randomUUID() + "@test.example");
        member.setName("테스터");
        member.setPhone("010-0000-0000");
        member.setGrade(grade);
        member.setPoint(memberPoint);
        member.setCreatedAt(LocalDateTime.now());
        memberRepository.save(member);

        List<OrderCreateRequest.Item> items = new ArrayList<>();
        for (int[] line : lines) {
            Product product = new Product();
            product.setName("테스트 상품 " + line[0]);
            product.setCategory("test");
            product.setPrice(line[0]);
            product.setStock(10);
            product.setStatus(ProductStatus.ON_SALE);
            product.setCreatedAt(LocalDateTime.now());
            product.setUpdatedAt(LocalDateTime.now());
            productRepository.save(product);

            OrderCreateRequest.Item item = new OrderCreateRequest.Item();
            item.setProductId(product.getId());
            item.setQuantity(line[1]);
            items.add(item);
        }

        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(items);
        req.setUsePoint(usePoint);
        req.setReceiverName("홍길동");
        req.setReceiverPhone("010-1234-5678");
        req.setAddress("서울시 어딘가 1");

        Long orderId = orderService.createOrder(member.getId(), req).getOrderId();
        return orderRepository.findById(orderId).orElseThrow();
    }
}
