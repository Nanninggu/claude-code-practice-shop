package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.external.PgClient;
import com.example.shop.external.PgResponse;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.PaymentRepository;
import com.example.shop.util.PriceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgClient pgClient;
    private final NotificationService notificationService;

    public Payment pay(Long orderId, String method, String cardNo) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + orderId));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("결제 가능한 주문 상태가 아닙니다. status=" + order.getStatus());
        }

        // 결제 금액 위변조 검증
        int expected = PriceUtil.calculatePayPrice(order);
        if (expected != order.getPayPrice()) {
            log.error("결제 금액 불일치 orderId={}, expected={}, actual={}", orderId, expected, order.getPayPrice());
            throw new IllegalStateException("결제 금액이 일치하지 않습니다.");
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(order.getPayPrice());
        payment.setMethod(PaymentMethod.valueOf(method));
        payment.setCardNo(cardNo);
        payment.setStatus(PaymentStatus.READY);
        payment.setCreatedAt(LocalDateTime.now());

        PgResponse res = pgClient.approve(order.getOrdNo(), order.getPayPrice(), cardNo);
        if (!res.isSuccess()) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new IllegalStateException("결제 실패: " + res.getErrorMessage());
        }

        payment.setPgTid(res.getTid());
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setApprovedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 포인트 적립 (일반 1%, GOLD 이상 2%)
        Member member = order.getMember();
        int rate = (member.getGrade() == MemberGrade.GOLD || member.getGrade() == MemberGrade.VIP) ? 2 : 1;
        member.setPoint(member.getPoint() + order.getPayPrice() * rate / 100);

        notificationService.sendPaymentCompleted(member, order);
        return payment;
    }

    public void refund(Long orderId, int amount) {
        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment == null) {
            throw new IllegalStateException("결제 내역이 없습니다. orderId=" + orderId);
        }

        if (amount > payment.getAmount() - payment.getCanceledAmount()) {
            throw new IllegalArgumentException("환불 가능 금액을 초과했습니다. 남은 금액="
                    + (payment.getAmount() - payment.getCanceledAmount()));
        }

        PgResponse res = pgClient.cancel(payment.getPgTid(), amount);
        if (!res.isSuccess()) {
            throw new IllegalStateException("환불 실패: " + res.getErrorMessage());
        }

        payment.setCanceledAmount(payment.getCanceledAmount() + amount);
        if (payment.getCanceledAmount() >= payment.getAmount()) {
            payment.setStatus(PaymentStatus.CANCELED);
        } else {
            payment.setStatus(PaymentStatus.PARTIAL_CANCELED);
        }
        payment.setCanceledAt(LocalDateTime.now());
    }
}
