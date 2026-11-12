package com.example.shop.service;

import com.example.shop.domain.Member;
import com.example.shop.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 알림 발송 (SMS / 알림톡)
 * 외부 발송 대행사 API 호출. 로컬에서는 로그만 남김
 */
@Slf4j
@Service
public class NotificationService {

    public void sendOrderCreated(Member member, Order order) {
        send(member.getPhone(), "[ShopNow] " + member.getName() + "님, 주문(" + order.getOrdNo() + ")이 접수되었습니다.");
    }

    public void sendPaymentCompleted(Member member, Order order) {
        send(member.getPhone(), "[ShopNow] 결제가 완료되었습니다. 결제금액: " + order.getPayPrice() + "원");
    }

    public void sendOrderCanceled(Member member, Order order) {
        send(member.getPhone(), "[ShopNow] 주문(" + order.getOrdNo() + ")이 취소되었습니다.");
    }

    public void sendShipped(Member member, Order order) {
        send(member.getPhone(), "[ShopNow] 상품이 발송되었습니다. 송장번호: " + order.getTrackingNo());
    }

    private void send(String phone, String message) {
        // 발송 대행사 API 응답 대기 (평균 200ms)
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[알림] to={}, message={}", phone, message);
    }
}
