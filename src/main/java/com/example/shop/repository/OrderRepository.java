package com.example.shop.repository;

import com.example.shop.domain.Order;
import com.example.shop.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrdNo(String ordNo);

    List<Order> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAt);

    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
