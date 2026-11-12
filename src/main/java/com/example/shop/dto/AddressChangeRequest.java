package com.example.shop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressChangeRequest {

    private String receiverName;
    private String receiverPhone;
    private String address;
}
