package com.example.shop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "coupons")
@Getter
@Setter
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    private CouponType type;

    private int discountValue;    // 정액: 원, 정률: %

    private LocalDate expiresAt;  // 이 날짜까지 사용 가능

    private boolean used;

    public boolean isExpired(LocalDate today) {
        return expiresAt != null && today.isAfter(expiresAt);
    }

    /** 할인 대상 금액을 넘지 않는 쿠폰 할인액 */
    public int calcDiscount(int amount) {
        int discount = (type == CouponType.PERCENT) ? amount * discountValue / 100 : discountValue;
        return Math.max(0, Math.min(discount, amount));
    }
}
