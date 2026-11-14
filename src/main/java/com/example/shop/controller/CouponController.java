package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CouponResponse;
import com.example.shop.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    public ApiResponse<List<CouponResponse>> getUsableCoupons() {
        return ApiResponse.ok(couponService.getUsableCoupons());
    }
}
