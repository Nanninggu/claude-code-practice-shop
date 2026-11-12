package com.example.shop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주문번호 (예: 20240101-000001)
    private String ordNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private int totalPrice;     // 상품 금액 합계
    private int discountPrice;  // 등급 할인 금액
    private int shippingFee;    // 배송비
    private int usedPoint;      // 사용 포인트
    private int payPrice;       // 실 결제 금액

    private String receiverName;
    private String receiverPhone;
    private String address;
    private String memo;

    private String trackingNo;
    private String cancelReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime canceledAt;
}
