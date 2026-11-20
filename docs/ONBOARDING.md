# 온보딩 가이드 (첫날 읽기)

이 문서는 소스 코드를 직접 읽고 확인한 내용만 적었습니다. 확인하지 못한 것은 "확인하지 못함"으로 표시했습니다.

> 확인하지 못함: 이 문서를 쓰는 동안 서버를 실제로 기동하거나 `./gradlew build`, API 호출을 실행해 보지는 않았습니다. 아래 실행 방법은 `build.gradle`, `application.yml`, `CLAUDE.md`를 읽고 정리한 것입니다.

## 1. 프로젝트 한 줄 요약

쇼핑몰의 주문/결제 REST API를 제공하는 Spring Boot 단일 모듈 앱입니다. 정적 쇼핑몰 화면(상품/주문, 내 주문, 관리자)도 같은 서버에서 함께 제공합니다.

- 빌드/런타임: Gradle, Spring Boot 4.1.2, Java 21 (`build.gradle`)
- 의존성: webmvc, data-jpa, validation, h2console, Lombok, H2
- 패키지: `com.example.shop`

`README.md`는 오래된 내용(Java 11, Spring Boot 2.7, MySQL, 8081 포트, `/api/v1/order`)이라 현재 코드와 다릅니다. 코드와 `application.yml`을 기준으로 보세요.

## 2. 실행 방법

```bash
./gradlew bootRun     # 서버 실행 (포트 8080)
./gradlew build       # 컴파일 + 테스트
./gradlew test        # 테스트 실행
./gradlew test --tests 'com.example.shop.SomeTest.method'   # 단일 테스트
```

- 포트: 8080 (`server.port`)
- DB: 인메모리 H2, `jdbc:h2:mem:shop;MODE=MySQL;DB_CLOSE_DELAY=-1`, 사용자 `sa`, 비밀번호 없음
- H2 콘솔이 켜져 있습니다 (`spring.h2.console.enabled: true`). 접속 경로는 설정에 적혀 있지 않아 확인하지 못했습니다.
- `ddl-auto: create`라서 서버를 시작할 때마다 스키마가 새로 만들어지고, `show-sql: true`로 SQL이 로그에 출력됩니다.
- 로그 레벨: `com.example.shop`은 DEBUG
- 테스트: `src/test` 디렉터리가 없습니다. 현재 테스트 코드는 없습니다.
- 샘플 요청: `http/api-examples.http`
- 화면: `src/main/resources/static`의 `index.html`(상품/주문), `orders.html`(내 주문), `admin.html`(관리자), `js/common.js`, `css/style.css`. 서버 실행 후 `http://localhost:8080/`에서 `index.html`이 열리는지는 직접 확인하지 못했습니다.

### 시드 데이터

서버 시작 시 `config/DataInitializer`(`CommandLineRunner`)가 개발용 데이터를 넣습니다.

- 회원 5명 (등급 BRONZE / SILVER / GOLD / VIP)
- 상품 20개 (ON_SALE, STOPPED, SOLD_OUT 상태 포함)
- 앞쪽 3건은 직접 만든 주문(1번: 결제 대기, 2번: 결제 완료, 3번: 배송 완료), 이어서 난수 시드 42로 만든 과거 주문 300건
- 결제 내역도 함께 생성

## 3. 디렉터리 구조

`src/main/java/com/example/shop` 아래:

| 패키지 | 역할 |
|---|---|
| `controller` | REST 엔드포인트 5개: `OrderController`, `PaymentController`, `ProductController`, `MemberController`, `AdminController` |
| `service` | 비즈니스 로직: `OrderService`, `PaymentService`, `ProductService`, `MemberService`, `NotificationService` |
| `repository` | Spring Data JPA 리포지토리 (Member, Order, OrderItem, Payment, Product) |
| `domain` | JPA 엔티티와 enum |
| `dto` | 요청/응답 객체 |
| `common` | `ApiResponse`(응답 래퍼), `GlobalExceptionHandler` |
| `external` | PG 연동: `PgClient` 인터페이스, `FakePgClient` 구현, `PgResponse` |
| `scheduler` | `OrderCleanupScheduler` |
| `config` | `DataInitializer` |
| `util` | `Constants`, `DateUtil`, `PriceUtil` |

계층은 `controller → service → repository → domain` 순서입니다. Lombok을 사용합니다.

## 4. 요청 흐름

### 4.1 공통

1. 요청이 컨트롤러에 도착합니다.
2. 컨트롤러가 서비스 메서드를 호출하고, 결과를 `ApiResponse.ok(...)`로 감싸 반환합니다. 성공 응답은 `code: "0000"`, `message: "success"`, `data`입니다.
3. 서비스에서 업무 규칙 위반은 `IllegalArgumentException` 또는 `IllegalStateException`으로 던집니다.
4. `GlobalExceptionHandler`가 두 예외를 모두 HTTP 400으로 변환합니다. 본문의 `code`는 `IllegalArgumentException`이면 `E400`, `IllegalStateException`이면 `E409`입니다. 코드 내 `// TODO 나머지 예외 처리 추가`가 있어 그 밖의 예외는 이 핸들러가 처리하지 않습니다.

응답 형태가 컨트롤러마다 다릅니다.
- `ApiResponse`로 감싸는 곳: `OrderController`
- 감싸지 않고 그대로 반환하는 곳: `PaymentController`(Map), `ProductController`(엔티티), `MemberController`(엔티티), `AdminController`의 통계 API(Map)
- `AdminController`의 주문 검색, 준비/발송/배송완료/강제취소는 `ApiResponse`, CSV 내보내기는 `byte[]` 응답

### 4.2 인증/사용자 식별

Spring Security는 없습니다.

- 고객 API: `X-MEMBER-ID` 헤더 값을 회원 ID로 사용합니다. 이 값을 받는 엔드포인트는 주문 생성, 미리보기, 내 주문 목록, 요약, 취소, 재주문, 구매확정입니다.
- 관리자 API: `prepare`, `ship`, `deliver` 세 개만 `X-ADMIN-TOKEN` 헤더를 환경 변수 `ADMIN_TOKEN`과 비교합니다. 불일치하면 `IllegalArgumentException`("관리자 인증 실패")이 발생합니다. 나머지 `/admin` 엔드포인트에는 토큰 확인 코드가 없습니다.
- 화면(`js/common.js`)은 `localStorage`의 `memberId`(없으면 4)를 `X-MEMBER-ID`로 보냅니다. `admin.html`은 입력창의 값을 `X-ADMIN-TOKEN`으로 보냅니다.

### 4.3 엔드포인트 목록

**`/api/orders`** (`OrderController`)

| 메서드 | 경로 | 헤더 | 서비스 |
|---|---|---|---|
| POST | `/api/orders` | X-MEMBER-ID | `createOrder` |
| POST | `/api/orders/preview` | X-MEMBER-ID | `previewPrice` |
| GET | `/api/orders/{orderId}` | | `getOrder` |
| GET | `/api/orders/number/{ordNo}` | | `getOrderByOrdNo` |
| GET | `/api/orders` | X-MEMBER-ID | `getMyOrders` |
| GET | `/api/orders/summary` | X-MEMBER-ID | `getOrderStatusSummary` |
| POST | `/api/orders/{orderId}/cancel` | X-MEMBER-ID | `cancelOrder` |
| POST | `/api/orders/{orderId}/partial-cancel` | | `partialCancel` |
| PUT | `/api/orders/{orderId}/address` | | `changeAddress` |
| POST | `/api/orders/{orderId}/reorder` | X-MEMBER-ID | `reOrder` |
| POST | `/api/orders/{orderId}/confirm` | X-MEMBER-ID | `confirmPurchase` |

**그 외 고객 API**

| 메서드 | 경로 | 비고 |
|---|---|---|
| POST | `/api/payments` | 본문 `orderId`, `method`(CARD / BANK_TRANSFER), `cardNo` |
| GET | `/api/products` | 선택 파라미터 `category` |
| GET | `/api/products/{id}` | |
| POST | `/api/products` | 상품 등록 |
| GET | `/api/members/{id}` | |

**`/admin`** (`AdminController`)

| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/admin/orders/search` | 파라미터 `status`, `keyword`, `from`, `to` (모두 선택) |
| POST | `/admin/orders/{orderId}/prepare` | X-ADMIN-TOKEN |
| POST | `/admin/orders/{orderId}/ship?trackingNo=` | X-ADMIN-TOKEN |
| POST | `/admin/orders/{orderId}/deliver` | X-ADMIN-TOKEN |
| POST | `/admin/orders/{orderId}/force-cancel?reason=` | |
| GET | `/admin/stats/daily?date=` | |
| GET | `/admin/stats/monthly?year=&month=` | |
| GET | `/admin/stats/top-products?limit=` | 기본 10 |
| GET | `/admin/stats/grade` | |
| GET | `/admin/orders/export?from=&to=` | CSV 다운로드 (`orders.csv`) |

### 4.4 주문 생성 → 결제 흐름

1. **주문 생성** `POST /api/orders`
   - `OrderService.createOrder`가 회원을 조회하고 `validateOrderRequest`로 요청을 검증합니다 (상품 1~20개, 상품당 수량 1~99, 중복 상품 불가, 받는 분 이름/전화번호 형식/주소/메모 길이 등).
   - 상품별로 판매 상태(ON_SALE)와 재고를 확인하고 **이 시점에 재고를 차감**합니다. 재고가 0이 되면 상품 상태를 SOLD_OUT으로 바꿉니다.
   - 금액 계산: 총액 → 등급 할인 → 배송비 → 포인트 사용 순서입니다. 포인트는 1,000점 이상부터 사용할 수 있고, **이 시점에 회원 포인트에서 차감**합니다.
   - 주문 상태는 `CREATED`로 저장되고, 접수 알림(`NotificationService`)을 보낸 뒤 `OrderResponse`를 반환합니다.
2. **결제** `POST /api/payments` → `PaymentService.pay`
   - 주문이 `CREATED`인지 확인하고, `PriceUtil.calculatePayPrice`로 다시 계산한 금액이 주문의 `payPrice`와 같은지 검증합니다.
   - `PgClient.approve`를 호출합니다. 실패하면 결제를 `FAILED`로 저장하고 예외를 던집니다.
   - 성공하면 결제를 `APPROVED`로 저장하고 주문을 `PAID`로 바꿉니다. 포인트를 적립하고(GOLD/VIP 2%, 그 외 1%) 결제 완료 알림을 보냅니다.
3. **관리자 처리** `prepare`(PAID → PREPARING) → `ship`(PREPARING → SHIPPED, 송장번호 필요) → `deliver`(SHIPPED → DELIVERED)
4. **구매확정** `confirm`: 본인 주문이고 DELIVERED일 때만 CONFIRMED로 바뀝니다.

### 4.5 취소 흐름

- **전체 취소** `cancelOrder`: SHIPPED/DELIVERED/CONFIRMED/CANCELED 상태는 취소할 수 없습니다. 결제가 APPROVED 또는 PARTIAL_CANCELED면 `PaymentService.refund`로 환불하고 적립 포인트를 회수합니다. 각 상품의 재고를 `ProductService.increaseStock`으로 복구하고, 사용 포인트를 돌려준 뒤 주문을 `CANCELED`로 바꿉니다.
- **부분 취소** `partialCancel`: PAID 또는 PARTIAL_CANCELED 주문만 가능합니다. 세부 로직은 이 문서에서 다루지 않았습니다.
- **관리자 강제 취소** `adminForceCancel`: 세부 로직은 확인하지 못했습니다.
- **미결제 자동 취소**: `OrderCleanupScheduler`가 60초 간격(`fixedDelay`, 첫 실행은 60초 뒤)으로 `expireUnpaidOrders`를 호출합니다. 생성 후 30분이 지난 `CREATED` 주문의 재고를 복구하고 사유 "결제 시간 초과"로 취소합니다. 스케줄링은 `ShopApplication`의 `@EnableScheduling`으로 켜집니다.

## 5. 도메인 모델

- **OrderStatus**: `CREATED → PAID → PREPARING → SHIPPED → DELIVERED → CONFIRMED`, 그리고 `CANCELED`, `PARTIAL_CANCELED`
- **OrderItemStatus**: `ORDERED`, `CANCELED` (주문 항목마다 `canceledQuantity`를 가집니다)
- **PaymentStatus**: `READY`, `APPROVED`, `FAILED`, `CANCELED`, `PARTIAL_CANCELED`
- **PaymentMethod**: `CARD`, `BANK_TRANSFER`
- **MemberGrade**: `BRONZE`, `SILVER`, `GOLD`, `VIP`
- **ProductStatus**: 시드 데이터에서 `ON_SALE`, `STOPPED`, `SOLD_OUT`이 쓰입니다.

가격 규칙 (`OrderService`의 private 메서드와 `PriceUtil`):
- 등급 할인율: SILVER 3%, GOLD 5%, VIP 10%, BRONZE 0%
- 배송비: `Constants`에 기준 금액(50,000원)과 배송비(3,000원)가 있습니다. VIP는 배송비가 없습니다.
- 그 외 상수: 최소 포인트 사용 1,000점, 상품당 최대 수량 99개

## 6. 외부 연동

- **PG**: `PgClient` 인터페이스(`approve`, `cancel`)의 유일한 구현은 `FakePgClient`입니다. 실제 네트워크 호출은 없고, 각 호출에 약 300ms 대기가 있습니다. 카드번호가 `4000`으로 시작하면 승인 거절, 금액이 0 이하이면 금액 오류를 반환합니다. `application.yml`의 `pg.api-url`이 코드에서 사용되는지는 확인하지 못했습니다(`FakePgClient`에서는 쓰이지 않음).
- **알림**: `NotificationService`는 실제 발송 없이 200ms 대기 후 로그만 남깁니다. 주문 접수, 결제 완료, 취소, 발송 시 호출됩니다.

## 7. 코드 작업 시 알아둘 점

- `OrderService`가 주문 관련 로직 대부분을 담고 있습니다(생성, 재주문, 취소, 부분 취소, 주소 변경, 확정, 가격 미리보기, 미결제 만료, 관리자 검색/통계/CSV). 클래스 레벨 `@Transactional`이고, 의존성은 필드 `@Autowired`로 주입합니다.
- 가격 계산이 `createOrder`와 `reOrder`에 각각 들어 있습니다. 가격 정책을 바꿀 때는 두 곳을 함께 확인하세요. 결제 검증용 계산은 `PriceUtil`에도 있습니다.
- 다른 서비스들은 생성자 주입(`@RequiredArgsConstructor`)을 씁니다.
- 팀 규칙 (`CLAUDE.md`): Java 코드를 바꾼 뒤에는 `./gradlew build`를 실행하고 결과를 보고합니다. 답변과 커밋 메시지는 한국어로 씁니다.

## 8. 처음 해 볼 것

1. `./gradlew bootRun`으로 서버를 띄웁니다.
2. `http/api-examples.http`의 요청을 순서대로 실행합니다 (상품 목록 → 주문 생성 → 결제 → 내 주문 → 취소). 샘플의 결제 `orderId`(304)는 시드 주문 303건 다음 번호를 가정한 값으로 보입니다. 주문 생성 응답의 ID로 바꿔 쓰세요.
3. `OrderController` → `OrderService.createOrder` → `PaymentController` → `PaymentService.pay` 순서로 코드를 읽으면 핵심 흐름을 따라갈 수 있습니다.
