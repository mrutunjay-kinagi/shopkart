# ShopKart: E-commerce Backend

Capstone project for the Master of Science in Computer Science (Scaler Neovarsity / Woolf), Backend Specialisation.
Author: **Mrutunjay Kinagi**

ShopKart is a production-style e-commerce backend built with **Java 21 and Spring Boot 4**. It covers the full buying journey in the PRD: accounts and sessions, catalogue browsing and search, a Redis-backed cart, transactional checkout, payments, receipts, order tracking and e-mail notifications. It is a **modular monolith**: each HLD microservice (user, catalogue, cart, order, payment, notification) is a module with its own package, data and public interface. Modules talk to each other through **Kafka events** published via a **transactional outbox**, so any module can later be split out into its own service.

| Concern | Implementation |
|---|---|
| API | Spring MVC, Bean Validation, RFC 9457 problem responses, OpenAPI 3.1 / Swagger UI |
| Security | Spring Security resource server, HS256 JWT access tokens (15 min), rotating refresh tokens with reuse detection, Redis token denylist for instant logout, BCrypt, login rate limiting |
| Data | MySQL 8.4, Spring Data JPA / Hibernate 7, Flyway migrations, InnoDB FULLTEXT search, pessimistic row locks for stock |
| Cache / state | Redis 7.4: carts (hash per user), product-page cache, token denylist, rate limits, payment locks |
| Messaging | Apache Kafka (KRaft): transactional outbox, idempotent consumers, retries + dead-letter topics |
| Payments | `PaymentGateway` port with a MockPay sandbox (card / netbanking synchronous, UPI async via HMAC-signed webhook, COD) |
| Ops | Actuator health probes, Prometheus metrics, request-id correlation, Docker, Docker Compose, GitHub Actions |
| Tests | JUnit 6, Testcontainers (real MySQL, Redis, Kafka), Awaitility, JaCoCo |

## Quick start

Prerequisites: Docker, plus JDK 21+ if you want to run outside Docker.

```bash
docker compose up -d --build        # MySQL, Redis, Kafka, Mailpit and the API
scripts/demo.sh                     # end-to-end walkthrough with curl + jq
```

* API docs: <http://localhost:8080/swagger-ui.html>
* E-mails sent by the app: <http://localhost:8025> (Mailpit)
* Seeded admin: `admin@shopkart.local` / `Admin@12345`, plus a demo catalogue of 21 products (`dev` profile)

To run the app from your IDE or the command line instead, start only the infrastructure:

```bash
docker compose up -d mysql redis kafka mailpit
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or run with throw-away Testcontainers and no Compose at all: `./mvnw spring-boot:test-run`.

## Tests and benchmark

```bash
./mvnw verify            # unit + integration tests (Testcontainers) + JaCoCo report in target/site/jacoco
./mvnw test -Pbenchmark  # performance benchmark on 100k products -> target/benchmark/results.json
```

Results measured on an Apple M2 (8 GB) are in `docs/benchmark/`:

| Optimisation | Before (p50) | After (p50) | Speed-up |
|---|---|---|---|
| Keyword search, FULLTEXT vs `LIKE` (100k rows) | 110–160 ms | 12–77 ms | 2.1–11.1x |
| Category browse, composite index | 19.9 ms | 0.56 ms | 35x |
| Product page over HTTP, Redis cache | 3.85 ms | 0.58 ms | 6.6x |
| Checkout (lock, reserve, persist, outbox) | | 8.0 ms | |

A flash-sale test checks that 25 concurrent checkouts for 5 units produce exactly 5 orders and zero oversell.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/password/forgot`, `/password/reset` |
| Profile | `GET/PATCH /api/v1/users/me`, `GET/POST/PUT/DELETE /api/v1/users/me/addresses` |
| Catalogue | `GET /api/v1/categories`, `GET /api/v1/products?q=&category=&brand=&minPrice=&maxPrice=&inStock=&sort=&page=&size=`, `GET /api/v1/products/{id}`, `GET /api/v1/products/slug/{slug}` |
| Cart | `GET/DELETE /api/v1/cart`, `POST /api/v1/cart/items`, `PUT/DELETE /api/v1/cart/items/{productId}` |
| Orders | `POST /api/v1/orders/checkout` (`Idempotency-Key` header), `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, `GET /api/v1/orders/{id}/tracking`, `POST /api/v1/orders/{id}/cancel` |
| Payments | `POST/GET /api/v1/orders/{id}/payments`, `GET /api/v1/payments/{id}/receipt`, `POST /api/v1/payments/webhooks/mockpay` |
| Admin | `POST /api/v1/admin/categories`, `POST/PUT/DELETE /api/v1/admin/products[/{id}]`, `PATCH /api/v1/admin/products/{id}/stock`, `GET /api/v1/admin/orders`, `PATCH /api/v1/admin/orders/{id}/status` |

MockPay test instruments: card token `tok_visa_4242` (any `tok_<brand>_<last4>`) succeeds and `tok_fail_*` is declined; netbanking bank code `FAIL` is declined; any valid UPI id returns `PENDING` until a signed webhook arrives (see `scripts/demo.sh`).

## Repository layout

```
src/main/java/com/mkinagi/shopkart/
  common/        BaseEntity, errors, pricing, outbox + Kafka event infrastructure
  config/        security, Kafka topics, Redis cache, OpenAPI, demo data seeder
  user/          registration, login, tokens, profile, addresses
  catalog/       categories, products, FULLTEXT search, inventory reservation
  cart/          Redis cart store and pricing
  order/         checkout, order lifecycle, tracking, expiry job, payment-event consumer
  payment/       payment gateway port, MockPay, webhooks, receipts, order-event consumer
  notification/  e-mail templates and the Kafka notification consumer
src/main/resources/db/migration/   Flyway schema
src/test/java/                     unit, integration, concurrency and benchmark tests
docs/diagrams/                     PlantUML sources and rendered diagrams
docs/benchmark/                    benchmark results
scripts/demo.sh                    end-to-end API walkthrough
```

## Configuration

All settings have local defaults. Override them in deployed environments with environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET` (base64, at least 256 bits), `MOCKPAY_WEBHOOK_SECRET`, `MAIL_HOST`, `FRONTEND_BASE_URL`, `ALLOWED_ORIGINS`, `SEED_DATA`.
