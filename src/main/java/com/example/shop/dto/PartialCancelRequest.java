package com.example.shop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartialCancelRequest {

    private Long itemId;
    private int quantity;
}
