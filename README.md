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
- Auth: JWT (HS512) with a secret shared across all services; each service validates tokens locally
  (the issuer derives the HMAC algorithm from the secret length — the 69-byte dev default selects
  HS512, while `1-technical-architecture-v2.md` still says HS256; giving all six services a 32-byte
  `JWT_SECRET` selects HS256)
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

### Full-coverage manual suite

[`postman/318-full-coverage.postman_collection.json`](postman/318-full-coverage.postman_collection.json) exercises all 34 iteration-2 requirements (U1–U5, P1–P4, O1–O6, I1–I4, PAY1–PAY4, AI1–AI4) in 102 requests / 147 assertions, including the negative paths (400/401/403/404/409/423), the `.99` payment-failure branch, the I4 alert open/resolve lifecycle and cross-user isolation. The expected result for every step, the run order, and the findings from the recorded runs are in [`postman/318-full-coverage-expected-results.md`](postman/318-full-coverage-expected-results.md).

Two steps need input from you: folder 5 needs `resetToken` (read it from the H2 console — the API never returns it) and folder 6 needs a real `OPENAI_API_KEY` in the `ai-customer-service` run configuration. Both are explained in the companion markdown. Run the collection **whole** rather than folder-by-folder: later steps depend on the ids and stock levels created by folder 1.

## Gotchas

- **Mock payment failure hook:** `payment-service` deterministically fails any payment whose amount has a fractional part of exactly `.99` (`app.payment.mock-failure-fraction`). Avoid `x.99` totals in tests unless you want to exercise the failure path.
- All data is in-memory H2; restarting a service resets its data (the Admin account is re-seeded on startup).
- Order ownership is enforced per user, so even the Admin cannot read a customer's order by ID.
- **Start Kafka before the services.** `order-service`, `inventory-Service` and `payment-service` must reach the broker while booting — wait for `docker ps` to report `318-kafka` as `healthy` first.
- **Kafka Streams state directories.** Both stream-processing topologies (`order-analytics` for O6, `inventory-alerts` for I4) keep a RocksDB state directory under the JVM temp directory — `order-analytics-state` and `inventory-alerts-state` (override with `ORDER_ANALYTICS_STATE_DIR` / `INVENTORY_ALERTS_STATE_DIR`). If that directory is left behind by an abrupt kill (IntelliJ's Stop) **or the broker is recreated** (`docker compose down -v`, which wipes the Kafka volume), the topology can fail to start **silently**: the consumer group never appears, no analytics rows are written, no alert is ever raised, and the API keeps answering `200`. Symptom check: `docker exec 318-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group order-analytics` (or `--group inventory-alerts`) prints `has no active members`. Fix: delete both directories and restart the two services — the state is rebuilt from the topics.

## Fresh clone checklist

Everything the services need at run time lives in the git-ignored `.idea/` run configurations and in the JVM temp directory, so a fresh clone needs this once:

1. `docker compose up -d`, then wait until `docker ps` reports `318-kafka` as `healthy` — Kafka must be up **before** the services.
2. Set the IntelliJ project SDK to **JDK 21** (`Project Structure → Project`). The project targets 21, and Lombok 1.18.34 does not support JDK 25 (symptom: `com.sun.tools.javac.code.TypeTag :: UNKNOWN`).
3. Set **Active profiles: `kafka`** on the `order-service`, `inventory-Service` and `payment-service` run configurations. Without it their Kafka producers/consumers are not created at all, so every event-driven step fails.
4. Add **`OPENAI_API_KEY`** to the `ai-customer-service` run configuration (any OpenAI-compatible key; `OPENAI_BASE_URL` / `OPENAI_MODEL` default to DeepSeek).
5. Clear the two Kafka Streams state directories if the broker was ever recreated:
   ```powershell
   Remove-Item -Recurse -Force "$env:TEMP\order-analytics-state","$env:TEMP\inventory-alerts-state" -ErrorAction SilentlyContinue
   ```
6. Start the six services and confirm the four consumer groups exist:
   ```powershell
   docker exec 318-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
   ```
   Expected: `inventory-service`, `order-service`, `order-analytics`, `inventory-alerts`.

## Troubleshooting

Notes from the recorded test runs. The consumer-group list is the single most useful diagnostic —
most "the API works but the tests fail" situations show up here:

```powershell
docker exec 318-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
# expected: inventory-service, order-service, order-analytics, inventory-alerts
```

### A whole family of event-driven assertions fails at once (eight to ten simultaneously)

- **Symptom**: login, catalogue, order placement and payment all pass, but every step that waits for a
  Kafka hop times out ("not PAID within 8s", "not back to 48"), orders stay `PENDING`, and refunds that
  should be rejected unexpectedly succeed.
- **Check**: compare the topics. If `order.created` has messages while `payment.confirmed` and
  `payment.failed` have none, one service is not publishing at all.
- **Cause**: `order-service`, `inventory-Service` or `payment-service` was started **without** the
  `kafka` profile. Their Kafka beans exist only under that profile — deliberately, so that `mvn test`
  needs no broker — which turns publishing and consuming into silent no-ops. The service still starts,
  every REST endpoint still answers, and nothing in the HTTP layer hints at the problem.
- **Fix**: set **Active profiles: `kafka`** on those three run configurations and restart them (see the
  fresh-clone checklist), then confirm with the consumer-group list above.

### `GET /inventory/alerts` is always empty, or the `inventory-alerts` group never appears

- **Symptom**: requirement I4 looks unimplemented — no alert is ever raised and
  `.../inventory/alerts` keeps returning `[]` — while O6 analytics works normally.
- **Check**: `--describe --group inventory-alerts` reports `has no active members`; the group may
  disappear entirely, because Kafka deletes an empty group that never committed an offset.
- **Cause**: a Kafka Streams state directory left over from an earlier run, or from an earlier broker
  generation (`docker compose down -v` wipes the Kafka volume while the local RocksDB state survives).
  Kafka Streams reports the failure **only to the service console**; the API keeps answering `200`, so
  it looks as if the feature does not exist.
- **Fix**: stop the service, delete `%TEMP%\inventory-alerts-state` (override the location with
  `INVENTORY_ALERTS_STATE_DIR`) and start it again with Kafka already running. Do **not** delete
  `order-analytics-state` while `order-service` is running — that topology is alive and using it.

### IntelliJ reports `No JDK for module ...` or `com.sun.tools.javac.code.TypeTag :: UNKNOWN`

- **Cause**: the IDE's project SDK drifted to JDK 25 (the newest JDK installed on the machine). The
  project targets 21, and Lombok 1.18.34 does not support JDK 25.
- **Fix**: `Project Structure → Project → SDK = 21`, then rebuild. Maven is unaffected — it uses
  `JAVA_HOME` — which is why `mvn test` can be green while the IDE build fails.

### Postman Runner reports `Errors: 1` although everything passes

- `Errors` counts **script** errors, not failed requests. A JavaScript error in a request's
  post-response script makes Postman skip that request's assertions — they are then neither passed nor
  failed, so an apparently green summary can hide assertions that never ran (this happened to step 6.1
  and silently skipped four session-dependent AI assertions).
- **Check**: `Console log` → `Script: SyntaxError`, or click into the request and read the `SCRIPT`
  badge.
- `Failed` is the number that describes the API contract; `Errors` describes the test scripts.

### Two working copies of the repository at the same time

- Two checkouts both binding ports 8081-8086 and both joining the same consumer groups fight over the
  broker and over the same local state directories. The resulting failures look random: a service that
  "is running" may not be the one you edited, or a topology may die because another instance holds its
  state directory.
- **Fix**: work in exactly one checkout and close the other project in the IDE.
