package com.example.shop.repository;

import com.example.shop.domain.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 동시 사용 방지를 위해 비관적 락으로 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Coupon> findByCode(String code);

    // 미리보기용 (락 없음)
    Optional<Coupon> findFirstByCode(String code);
}
