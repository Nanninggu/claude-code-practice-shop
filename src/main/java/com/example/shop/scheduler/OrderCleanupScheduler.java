package com.example.shop.scheduler;

import com.example.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        orderService.expireUnpaidOrders();
    }
}
