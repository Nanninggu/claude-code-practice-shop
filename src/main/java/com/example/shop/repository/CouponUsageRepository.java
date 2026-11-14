package com.example.shop.repository;

import com.example.shop.domain.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    List<CouponUsage> findByCouponCodeOrderByUsedAtDescIdDesc(String couponCode);
}
