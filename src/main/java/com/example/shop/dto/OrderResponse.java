package com.example.shop.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderResponse {

    private Long orderId;
    private String orderNumber;
    private String status;
    private String memberName;
    private int totalPrice;
    private int discountPrice;
    private int shippingFee;
    private int usedPoint;
    private int payPrice;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String memo;
    private String trackingNo;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
