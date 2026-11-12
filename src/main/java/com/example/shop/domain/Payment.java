package com.example.shop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관관계 대신 id만 저장 (2021 결제팀 요청)
    private Long orderId;

    // PG 거래번호
    private String pgTid;

    private int amount;

    private int canceledAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    // 카드번호
    private String cardNo;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;
}
