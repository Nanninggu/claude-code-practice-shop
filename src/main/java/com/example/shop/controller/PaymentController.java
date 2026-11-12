package com.example.shop.controller;

import com.example.shop.domain.Payment;
import com.example.shop.dto.PaymentRequest;
import com.example.shop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public Map<String, Object> pay(@RequestBody PaymentRequest req) {
        Payment payment = paymentService.pay(req.getOrderId(), req.getMethod(), req.getCardNo());
        Map<String, Object> result = new HashMap<>();
        result.put("result", "SUCCESS");
        result.put("paymentId", payment.getId());
        result.put("tid", payment.getPgTid());
        result.put("amount", payment.getAmount());
        return result;
    }
}
