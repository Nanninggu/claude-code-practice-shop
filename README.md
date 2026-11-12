# ShopNow 주문 API

ShopNow 쇼핑몰 주문/결제 백엔드

## 환경

- Java 11
- Spring Boot 2.7
- MySQL 5.7

## 실행

```bash
./gradlew bootRun
```

서버: http://localhost:8081

## 주요 API

| 기능 | Method | URL |
|---|---|---|
| 주문 생성 | POST | /api/v1/order |
| 주문 조회 | GET | /api/v1/order/{id} |
| 결제 | POST | /api/v1/payment |
| 주문 취소 | DELETE | /api/v1/order/{id} |

## 참고

- 결제는 PG사 테스트 모드로 연동되어 있음
- 배포는 젠킨스에서 수동 실행
- 문의: 주문개발팀
