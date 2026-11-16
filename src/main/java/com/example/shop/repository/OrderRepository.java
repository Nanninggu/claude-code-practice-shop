package com.example.shop.repository;

import com.example.shop.domain.Order;
import com.example.shop.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrdNo(String ordNo);

    List<Order> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 내 주문 목록용: 회원/쿠폰/주문상품/상품을 한 번에 조회 (N+1 방지)
    @Query("select distinct o from Order o"
            + " join fetch o.member"
            + " left join fetch o.coupon"
            + " left join fetch o.items i"
            + " left join fetch i.product"
            + " where o.member.id = :memberId"
            + " order by o.createdAt desc")
    List<Order> findWithItemsByMemberId(@Param("memberId") Long memberId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAt);

    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
