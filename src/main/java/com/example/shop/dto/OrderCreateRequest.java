package com.example.shop.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderCreateRequest {

    private List<Item> items;
    private int usePoint;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String memo;

    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private int quantity;
    }
}
