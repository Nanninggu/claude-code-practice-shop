# ShopNow 주문 API

ShopNow 쇼핑몰 주문/결제 백엔드

## 환경

- Java 21
- Spring Boot 4.1.2
- H2 인메모리 DB (MySQL 호환 모드, 서버 시작 시마다 스키마 재생성 및 시드 데이터 적재)

## 실행

```bash
./gradlew bootRun
```

서버: http://localhost:8080

## 주요 API

| 기능 | Method | URL |
|---|---|---|
| 주문 생성 | POST | /api/orders |
| 주문 조회 | GET | /api/orders/{orderId} |
| 결제 | POST | /api/payments |
| 주문 취소 | POST | /api/orders/{orderId}/cancel |

그 외 엔드포인트(상품, 회원, 관리자 등)는 `http/api-examples.http`와 `docs/ONBOARDING.md`를 참고하세요.

## 참고

- 결제는 실제 PG 호출 없이 가짜 구현(`FakePgClient`)으로 동작함
- 배포는 젠킨스에서 수동 실행
- 문의: 주문개발팀
