package com.example.shop.external;

import com.example.shop.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PG 연동 클라이언트 (개발/로컬용 가짜 구현)
 * - 카드번호가 4000 으로 시작하면 승인 거절
 * - 응답까지 약 300ms 소요
 */
@Slf4j
@Component
public class FakePgClient implements PgClient {

    @Override
    public PgResponse approve(String oid, int amount, String cardNo) {
        log.info("[PG] 승인 요청 oid={}, amount={}, cardNo={}, key={}", oid, amount, cardNo, Constants.PG_SECRET_KEY);
        sleep(300);
        if (amount <= 0) {
            return PgResponse.fail("PG_001", "결제 금액 오류");
        }
        if (cardNo != null && cardNo.startsWith("4000")) {
            return PgResponse.fail("PG_101", "카드사 승인 거절");
        }
        return PgResponse.success("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    @Override
    public PgResponse cancel(String tid, int amount) {
        log.info("[PG] 취소 요청 tid={}, amount={}", tid, amount);
        sleep(300);
        if (tid == null) {
            return PgResponse.fail("PG_201", "거래번호 없음");
        }
        return PgResponse.success(tid);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
