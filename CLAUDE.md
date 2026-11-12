# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun                 # run server (port 8080, in-memory H2)
./gradlew build                   # compile + test
./gradlew test                    # run tests (none exist yet; src/test is absent)
./gradlew test --tests 'com.example.shop.SomeTest.method'   # single test
```

- H2 console is enabled; the DB is `jdbc:h2:mem:shop` (MySQL mode), schema is recreated on every start (`ddl-auto: create`) and seeded by `config/DataInitializer`.
- `http/api-examples.http` has sample requests.
- Static shop UI is served from `src/main/resources/static` (`index.html`, `orders.html`, `admin.html`, `js/common.js`).

## README vs. reality

`README.md` is stale: it says Java 11 / Spring Boot 2.7 / MySQL 5.7 / port 8081 / `/api/v1/order` URLs. The actual setup (`build.gradle`, `application.yml`) is Spring Boot 4.1.x, Java 21, H2, port 8080, and endpoints under `/api/orders`, `/api/payments`, `/api/products`, `/api/members`, `/admin`. Trust the code.

## Architecture

Single-module Spring Boot app, package `com.example.shop`, classic layering: `controller` → `service` → `repository` (Spring Data JPA) over `domain` entities; `dto` for request/response; `common/ApiResponse` wraps all responses; `GlobalExceptionHandler` maps `IllegalArgumentException` → 400 (`E400`) and `IllegalStateException` → 400 (`E409`) — business rule violations are thrown as these two exception types throughout the services. Lombok is used.

Key points that span files:

- **OrderService is the large hub** (~all order logic: create, reorder, cancel, partial cancel, address change, confirm, price preview, expiring unpaid orders, admin search/stats/CSV). It uses field `@Autowired` injection and a class-level `@Transactional`. Price calculation (grade discount, shipping fee, point usage) is duplicated between `createOrder` and `reOrder`; keep them in sync when changing pricing.
- **Order lifecycle**: `OrderStatus` (CREATED → PAID → PREPARING → SHIPPED → DELIVERED → CONFIRMED, plus CANCELED / PARTIAL_CANCELED); each `OrderItem` has its own status and `canceledQuantity`. Stock is decremented at order creation (not payment) and restored on cancel via `ProductService.increaseStock`; points are deducted at order creation and restored/clawed back on cancel.
- **Payments**: `PaymentService` talks to the PG through the `PgClient` interface; the only implementation is `external/FakePgClient` (a `@Component`, no real network call despite `pg.api-url` in config).
- **Scheduler**: `OrderCleanupScheduler` (enabled by `@EnableScheduling`) calls `OrderService.expireUnpaidOrders()` every 60s.
- **Auth is placeholder**: customer endpoints identify the user via an `X-MEMBER-ID` header (no verification). In `/admin`, only prepare, ship and deliver check `X-ADMIN-TOKEN`. There is no Spring Security.
- `util/Constants` holds business constants (shipping threshold/fee, min point use, max quantity) and also hardcoded PG credentials and the admin token.

## Team rules

- After changing Java code, run `./gradlew build` and report the result.
- Write replies and commit messages in Korean.
