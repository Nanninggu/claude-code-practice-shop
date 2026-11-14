package com.example.shop.dto;

import com.example.shop.domain.Coupon;
import com.example.shop.domain.CouponType;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class CouponResponse {

    private final String code;
    private final CouponType type;
    private final int discountValue;
    private final LocalDate expiresAt;

    public CouponResponse(Coupon coupon) {
        this.code = coupon.getCode();
        this.type = coupon.getType();
        this.discountValue = coupon.getDiscountValue();
        this.expiresAt = coupon.getExpiresAt();
    }
}
