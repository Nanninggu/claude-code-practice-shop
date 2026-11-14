package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.CouponUsageResponse;
import com.example.shop.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponUsageRepository couponUsageRepository;

    @GetMapping("/{code}/usages")
    public ApiResponse<List<CouponUsageResponse>> usages(@PathVariable String code) {
        return ApiResponse.ok(couponUsageRepository.findByCouponCodeOrderByUsedAtDescIdDesc(code.trim()).stream()
                .map(CouponUsageResponse::new)
                .toList());
    }
}
