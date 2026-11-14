package com.example.shop.service;

import com.example.shop.dto.CouponResponse;
import com.example.shop.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;

    /** 지금 사용 가능한 쿠폰 목록 (판단 기준은 주문 시와 동일한 Coupon.isUsable) */
    public List<CouponResponse> getUsableCoupons() {
        LocalDate today = LocalDate.now();
        return couponRepository.findAll().stream()
                .filter(c -> c.isUsable(today))
                .map(CouponResponse::new)
                .toList();
    }
}
