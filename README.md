# 318 E-Commerce Microservices

CSCI318 capstone: an event-driven e-commerce backend built as six Spring Boot microservices, developed with an AI-driven, spec-first workflow (specs in `specs/`, implementation guided by `agent-prompts/dev-agent.md`).

## Architecture

| Service | Port | Responsibility |
| :--- | :--- | :--- |
| `user-service` | 8081 | Registration, login (JWT), profile, password reset; seeds the single Admin account |
| `product-service` | 8082 | Catalog browsing/search, admin product management |
| `order-service` | 8083 | Place/cancel/query orders, real-time order analytics (Admin) |
| `inventory-Service` | 8084 | Stock queries and adjustments (event-sourced), low-stock alerts |
| `payment-service` | 8085 | Mock payment processing, refunds, transaction list (Admin) |
| `ai-customer-service` | 8086 | LangChain4j chat agent (DeepSeek/OpenAI-compatible) with product/order tools |

- Java 21, Spring Boot 3.3.x, Spring Cloud Stream (Kafka binder), H2 in-memory DB per service (database-per-service)
- Auth: JWT (HS256) with a secret shared across all services; each service validates tokens locally
- Async flows over Kafka: `order.created` / `order.cancelled` → Inventory; `payment.confirmed` / `payment.failed` → Order (and Inventory)
- Design docs: [`specs/iter2/`](specs/iter2) (architecture, user stories, DDD domain models, API list, event sourcing, CQRS). API contract: [`specs/iter2/openapi-contract-v2.yaml`](specs/iter2/openapi-contract-v2.yaml)
- Iteration 1 specs are kept in [`specs/iter1/`](specs/iter1) as an archived historical record — superseded by `specs/iter2/`, not the current design of record.

## Prerequisites

- JDK 21, Maven
- Docker Desktop (for the local Kafka broker)
- Node.js 18+ (only for the end-to-end script)

## Running locally

1. Start Kafka (broker on `localhost:9092`, Kafka UI on http://localhost:8090):
   ```bash
   docker compose up -d
   ```
2. Start the services (each in its own terminal, or as IntelliJ Spring Boot run configurations).
   **`order-service`, `inventory-Service` and `payment-service` must run with the `kafka` Spring profile**, otherwise Kafka publishing/consuming is disabled by design (this keeps `mvn test` broker-free) and stock will not be deducted / orders will not move to `PAID`:
   ```bash
   cd user-service      && mvn spring-boot:run
   cd product-service   && mvn spring-boot:run
   cd order-service     && mvn spring-boot:run -Dspring-boot.run.profiles=kafka
   cd inventory-Service && mvn spring-boot:run -Dspring-boot.run.profiles=kafka
   cd payment-service   && mvn spring-boot:run -Dspring-boot.run.profiles=kafka
   cd ai-customer-service && mvn spring-boot:run   # optional
   ```
3. Run unit/contract tests (no Kafka needed): `mvn test`

### Configuration

| Env var | Default | Purpose |
| :--- | :--- | :--- |
| `ADMIN_EMAIL` | `admin@318ecommerce.local` | Seeded Admin account email |
| `ADMIN_PASSWORD` | `ChangeMe123!` | Seeded Admin account password |
| `JWT_SECRET` | dev placeholder | Must be identical across all services |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` | DeepSeek endpoint | LLM used by `ai-customer-service` |

## End-to-end testing

With all services running (Kafka profile enabled), run the 17-step walkthrough (login → create product → stock → order → pay → cancel → refund):

```bash
node scripts/e2e-walkthrough.mjs
```

The same flow is available as a Postman collection: import [`postman/318-ecommerce-e2e.postman_collection.json`](postman/318-ecommerce-e2e.postman_collection.json) and run it with the Collection Runner. Tokens and IDs are chained via collection variables, so no Environment is needed. Re-import the file after any change to it — Postman does not sync with disk.

## Gotchas

- **Mock payment failure hook:** `payment-service` deterministically fails any payment whose amount has a fractional part of exactly `.99` (`app.payment.mock-failure-fraction`). Avoid `x.99` totals in tests unless you want to exercise the failure path.
- All data is in-memory H2; restarting a service resets its data (the Admin account is re-seeded on startup).
- Order ownership is enforced per user, so even the Admin cannot read a customer's order by ID.
