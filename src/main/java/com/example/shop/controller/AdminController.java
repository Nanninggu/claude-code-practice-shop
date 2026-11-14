package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.dto.OrderResponse;
import com.example.shop.service.AdminOrderService;
import com.example.shop.service.OrderService;
import com.example.shop.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderService orderService;
    private final AdminOrderService adminOrderService;

    @GetMapping("/orders/search")
    public ApiResponse<List<OrderResponse>> search(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to) {
        return ApiResponse.ok(adminOrderService.searchOrdersForAdmin(status, keyword, from, to));
    }

    @PostMapping("/orders/{orderId}/prepare")
    public ApiResponse<Void> prepare(@RequestHeader("X-ADMIN-TOKEN") String token, @PathVariable Long orderId) {
        checkToken(token);
        orderService.startPreparing(orderId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/orders/{orderId}/ship")
    public ApiResponse<Void> ship(@RequestHeader("X-ADMIN-TOKEN") String token,
                                  @PathVariable Long orderId,
                                  @RequestParam String trackingNo) {
        checkToken(token);
        orderService.ship(orderId, trackingNo);
        return ApiResponse.ok(null);
    }

    @PostMapping("/orders/{orderId}/deliver")
    public ApiResponse<Void> deliver(@RequestHeader("X-ADMIN-TOKEN") String token, @PathVariable Long orderId) {
        checkToken(token);
        orderService.deliver(orderId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/orders/{orderId}/force-cancel")
    public ApiResponse<Void> forceCancel(@PathVariable Long orderId, @RequestParam String reason) {
        orderService.adminForceCancel(orderId, reason);
        return ApiResponse.ok(null);
    }

    @GetMapping("/stats/daily")
    public Map<String, Object> daily(@RequestParam String date) {
        return adminOrderService.getDailySales(LocalDate.parse(date));
    }

    @GetMapping("/stats/monthly")
    public Map<String, Object> monthly(@RequestParam int year, @RequestParam int month) {
        return adminOrderService.getMonthlySales(year, month);
    }

    @GetMapping("/stats/top-products")
    public List<Map<String, Object>> topProducts(@RequestParam(defaultValue = "10") int limit) {
        return adminOrderService.getTopProducts(limit);
    }

    @GetMapping("/stats/grade")
    public Map<String, Object> grade() {
        return adminOrderService.getGradeStatistics();
    }

    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> export(@RequestParam String from, @RequestParam String to) {
        String csv = adminOrderService.exportOrdersCsv(LocalDate.parse(from), LocalDate.parse(to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private void checkToken(String token) {
        if (!Constants.ADMIN_TOKEN.equals(token)) {
            throw new IllegalArgumentException("관리자 인증 실패");
        }
    }
}
