package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.*;
import com.example.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@RequestHeader("X-MEMBER-ID") Long memberId,
                                                  @RequestBody OrderCreateRequest req) {
        return ApiResponse.ok(orderService.createOrder(memberId, req));
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Integer>> preview(@RequestHeader("X-MEMBER-ID") Long memberId,
                                                     @RequestBody OrderCreateRequest req) {
        return ApiResponse.ok(orderService.previewPrice(memberId, req));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.getOrder(orderId));
    }

    @GetMapping("/number/{ordNo}")
    public ApiResponse<OrderResponse> getOrderByNumber(@PathVariable String ordNo) {
        return ApiResponse.ok(orderService.getOrderByOrdNo(ordNo));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> getMyOrders(@RequestHeader("X-MEMBER-ID") Long memberId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.getMyOrders(memberId, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Integer>> getSummary(@RequestHeader("X-MEMBER-ID") Long memberId) {
        return ApiResponse.ok(orderService.getOrderStatusSummary(memberId));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Void> cancel(@RequestHeader("X-MEMBER-ID") Long memberId,
                                    @PathVariable Long orderId,
                                    @RequestBody CancelRequest req) {
        orderService.cancelOrder(orderId, memberId, req.getReason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{orderId}/partial-cancel")
    public ApiResponse<Void> partialCancel(@PathVariable Long orderId,
                                           @RequestBody PartialCancelRequest req) {
        orderService.partialCancel(orderId, req.getItemId(), req.getQuantity());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{orderId}/address")
    public ApiResponse<OrderResponse> changeAddress(@PathVariable Long orderId,
                                                    @RequestBody AddressChangeRequest req) {
        return ApiResponse.ok(orderService.changeAddress(orderId, req));
    }

    @PostMapping("/{orderId}/reorder")
    public ApiResponse<OrderResponse> reOrder(@RequestHeader("X-MEMBER-ID") Long memberId,
                                              @PathVariable Long orderId) {
        return ApiResponse.ok(orderService.reOrder(memberId, orderId));
    }

    @PostMapping("/{orderId}/confirm")
    public ApiResponse<Void> confirm(@RequestHeader("X-MEMBER-ID") Long memberId,
                                     @PathVariable Long orderId) {
        orderService.confirmPurchase(orderId, memberId);
        return ApiResponse.ok(null);
    }
}
