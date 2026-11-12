package com.example.shop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemResponse {

    private Long itemId;
    private Long productId;
    private String productName;
    private int unitPrice;
    private int quantity;
    private int canceledQuantity;
    private String status;
}
