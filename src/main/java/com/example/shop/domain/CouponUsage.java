package com.example.shop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 쿠폰 사용 이력. 주문이 취소되어도 삭제하지 않는다. */
@Entity
@Table(name = "coupon_usages", indexes = @Index(name = "idx_coupon_usage_code", columnList = "couponCode"))
@Getter
@Setter
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String couponCode;

    @Column(nullable = false)
    private String ordNo;

    private int discountAmount;

    private LocalDateTime usedAt;
}
