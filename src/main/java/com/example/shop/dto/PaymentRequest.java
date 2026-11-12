package com.example.shop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRequest {

    private Long orderId;
    private String method;  // CARD, BANK_TRANSFER
    private String cardNo;
}
