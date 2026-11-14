package com.example.shop.dto;

import com.example.shop.domain.CouponUsage;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CouponUsageResponse {

    private final String couponCode;
    private final String ordNo;
    private final int discountAmount;
    private final LocalDateTime usedAt;

    public CouponUsageResponse(CouponUsage usage) {
        this.couponCode = usage.getCouponCode();
        this.ordNo = usage.getOrdNo();
        this.discountAmount = usage.getDiscountAmount();
        this.usedAt = usage.getUsedAt();
    }
}
