package com.example.shop.repository;

import com.example.shop.domain.OrderItem;
import com.example.shop.domain.OrderItemStatus;
import com.example.shop.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /**
     * 상품별 판매 수량/금액 집계 (수량 내림차순, 동률은 상품 ID 오름차순).
     * 결과 행: [productId, productName, quantity, amount]
     */
    @Query("select p.id, p.name, sum(i.quantity - i.canceledQuantity),"
            + " sum((i.quantity - i.canceledQuantity) * i.unitPrice)"
            + " from OrderItem i join i.order o join i.product p"
            + " where o.status not in :excludedOrderStatuses and i.status <> :canceledItemStatus"
            + " group by p.id, p.name"
            + " order by sum(i.quantity - i.canceledQuantity) desc, p.id asc")
    List<Object[]> aggregateSalesByProduct(
            @Param("excludedOrderStatuses") List<OrderStatus> excludedOrderStatuses,
            @Param("canceledItemStatus") OrderItemStatus canceledItemStatus,
            org.springframework.data.domain.Pageable pageable);
}
