package com.example.shop.repository;

import com.example.shop.domain.Payment;
import com.example.shop.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 결제 내역이 없으면 null 반환
    Payment findByOrderId(Long orderId);

    List<Payment> findByStatus(PaymentStatus status);
}
