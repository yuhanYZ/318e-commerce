# 318 E-Commerce - Full Manual Test Expectations

Companion document for [`postman/318-full-coverage.postman_collection.json`](318-full-coverage.postman_collection.json) (102 requests).

**Assumptions.** All six services are running; `order-service`, `inventory-Service` and `payment-service` run with the `kafka` profile. Folder 0 has generated this run's `runTag`, and folder 1 created its product in this same run (stock starts at 0 and is raised to 50), which is what makes the "48" numbers below hold. Error bodies are uniformly `{code, message, timestamp, details}`; only the status code and the key fields are listed.

---

## Folder 0 - Setup

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 0.1 | `GET /products?page=0&size=1` | none | `200`, body has `content` / `page` / `totalElements`; console prints this run's `runTag` |
| 0.2 | `GET /products?page=0&size=1`, issued a second time from inside a test script via `pm.sendRequest` | none | `200`; console prints `err / code / bodyType / body`. **Prerequisite for the ten `[KAFKA]` polling steps**: if this fails, the sandbox cannot issue scripted requests and those polls fail too |

## Folder 1 - Core flow

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 1.1 | `POST /users/login` (admin) | none | `200` `{token, userId}` |
| 1.2 | `GET /orders/analytics/realtime?windowMinutes=1440` | admin | `200` `{windowMinutes:1440, windowStart, windowEnd, orderCount, totalRevenue:{amount,currency:"AUD"}, averageOrderValue, generatedAt}` |
| 1.3 | `POST /products` | admin | `201` `{id:"PRD-...", status:"ACTIVE", price:{amount:20.00,currency:"AUD"}, categoryId:"CAT-TEST"}` |
| 1.4 | `POST /inventory/{productId}/adjust` `delta=50` | admin | `200` `{productId, quantityAvailable:50, isAvailable:true, version}` |
| 1.5 | `GET /inventory/{productId}` | none | `200` `quantityAvailable=50`, `isAvailable=true` |
| 1.6 | `POST /users/register` | none | `201` `{id, email, role:"CUSTOMER"}` (the `Role` enum only has `CUSTOMER` / `ADMIN`) |
| 1.7 | `POST /users/login` (customer) | none | `200` `{token, userId}` |
| 1.8 | `GET /products?page=0&size=100` | none | `200`, `content` contains this run's `productId` |
| 1.9 | `POST /orders`, 2 units | customer | `201` `{id:"ORD-...", status:"PENDING", totalAmount:{amount:40.00,currency:"AUD"}, items[0].unitPrice.amount:20.00}` |
| 1.10 | `GET /inventory/{productId}` | none | `200` `quantityAvailable=48` (auto-polls for about 9 seconds) |
| 1.11 | `POST /payments` 40.00 | customer | `201` `{id:"PAY-...", orderId, amount:{40.00,AUD}, status:"SUCCESS", method, createdAt}` |
| 1.12 | `GET /orders/{orderId}` | customer | `200` `status="PAID"` |
| 1.13 | `GET /payments/order/{orderId}` | customer | `200` `{id=paymentId, orderId, status:"SUCCESS"}` |
| 1.14 | `GET /payments?page=0&size=100` | admin | `200` `{content:[...contains paymentId], page, totalElements, refunds:[...], totalRefundElements}` |
| 1.15 | `POST /orders`, 1 unit | customer | `201` `status="PENDING"`, `totalAmount.amount=20.00` |
| 1.16 | `POST /payments` 20.00 | customer | `201` `status="SUCCESS"` |
| 1.17 | polls `GET /orders/{order2Id}` then `POST .../cancel` | customer | reads `PAID` first; cancel returns `200` `status="CANCELLED"` |
| 1.18 | `GET /inventory/{productId}` | none | `200` `quantityAvailable=48` (the cancel released the unit) |
| 1.19 | `POST /payments/{payment2Id}/refund` | customer | `201` `{id:"REF-...", paymentId, amount:{20.00,AUD}, status, requestedAt}` |
| 1.20 | `GET /orders/analytics/realtime?windowMinutes=1440` | admin | `200`; **after the poll converges**, `orderCount` is `+2` versus 1.2, `totalRevenue` is `+60.00`, and `averageOrderValue = totalRevenue / orderCount`. Transient values are allowed: under the `kafka` profile O6 is maintained only by the Kafka Streams topology (the inline projection is short-circuited by `app.kafka.enabled`), and readings wait for the state-store cache / commit cycle (30 s by default), so this step polls for about 64 s and logs its first reading |

## Folder 2 - Uncovered queries and commands

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 2.1 | `GET /products/{productId}` | none | `200` `{status:"ACTIVE", stockAvailable:48, isInStock:true, categoryId:"CAT-TEST"}` (the stock value is a live call to inventory-service) |
| 2.2 | `GET /products/PRD-NOPE-{runTag}` | none | `404` `code="ResourceNotFound"` |
| 2.3 | `GET /products?categoryId=CAT-TEST&page=0&size=50` | none | `200`, every item in `content` has `categoryId` `CAT-TEST` |
| 2.4 | `GET /products/search?q={runTag}` | none | `200`, array containing this run's `productId` |
| 2.5 | `GET /products/search?q=zzz{runTag}` | none | `200` with an empty array (not 404) |
| 2.6 | `GET /users/me` | customer | `200` `{id, email, name:null, address:null, phone:null, role:"CUSTOMER", status:"ACTIVE"}` |
| 2.7 | `PUT /users/me` `{name,address,phone}` | customer | `200` `name="Yuhan"`, `address="Sydney"`, `phone="0400000000"` |
| 2.8 | `PUT /users/me` with `name` only | customer | `200` `name="Renamed {runTag}"`, `address` / `phone` unchanged from 2.7 (partial update) |
| 2.9 | `GET /users/me` | customer | `200` `name="Renamed {runTag}"` (persisted) |
| 2.10 | `GET /orders?page=0&size=20` | customer | `200` `totalElements>=2`, every item `userId=customerId`, sorted by `createdAt` descending |
| 2.11 | `GET /orders/analytics/realtime?windowMinutes=0` | admin | `400` `code="ValidationFailed"` (window must be 1..1440) |
| 2.12 | `GET /inventory/PRD-NOPE-{runTag}` | none | `404` `code="ResourceNotFound"` (an untracked product is 404, never a zero quantity) |

## Folder 3 - Failure branch and low-stock alerts

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 3.1 | `POST /products` 9.99 | admin | `201` `price.amount=9.99` |
| 3.2 | `POST /inventory/{failProductId}/adjust` `delta=10` | admin | `200` `quantityAvailable=10` |
| 3.3 | `POST /orders`, 1 unit of that product | customer | `201` `totalAmount.amount=9.99`, `status="PENDING"` |
| 3.4 | `GET /inventory/{failProductId}` | none | `200` `quantityAvailable=9` |
| 3.5 | `POST /payments` 9.99 | customer | `201` but `status="FAILED"` (the mock gateway always fails on a `.99` fraction) |
| 3.6 | `GET /orders/{failOrderId}` | customer | `200` `status="PAYMENT_FAILED"` |
| 3.7 | `GET /inventory/{failProductId}` | none | `200` `quantityAvailable=10` (`payment.failed` released the stock) |
| 3.8 | `GET /inventory/{productId}` | none | `200`; sets `deltaToFour = 4 - current stock` and prints it |
| 3.9 | `POST /inventory/{productId}/adjust` `delta={deltaToFour}` | admin | `200` `quantityAvailable=4` (threshold is 5, the rule is strictly less than 5) |
| 3.10 | `GET /inventory/alerts` | admin | `200`, array containing `{stockItemId=productId, triggeredAt, resolvedAt:null}` |
| 3.11 | `POST /inventory/{productId}/adjust` `delta=50` | admin | `200` `quantityAvailable=54` |
| 3.12 | `GET /inventory/alerts` | admin | `200`, array that **does not** contain that `productId` (the alert auto-resolved) |

## Folder 4 - Negative and permission cases

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 4.1 | `GET /orders/{orderId}`, no header | none | `401` `code="Unauthorized"` |
| 4.2 | `GET /orders/{orderId}` with `Bearer not-a-real-jwt` | none | `401` `code="Unauthorized"` |
| 4.3 | `POST /products` | customer | `403` `code="Forbidden"` |
| 4.4 | `POST /inventory/{productId}/adjust` | customer | `403` `code="Forbidden"` |
| 4.5 | `GET /inventory/alerts` | customer | `403` `code="Forbidden"` |
| 4.6 | `GET /payments?page=0&size=10` | customer | `403` `code="Forbidden"` |
| 4.7 | `GET /orders/analytics/realtime?windowMinutes=15` | customer | `403` `code="Forbidden"` |
| 4.8 | `POST /users/register` with an existing email | none | `409` `code="EmailAlreadyExists"` |
| 4.9 | `POST /users/register` with password `123` | none | `400` `code="ValidationFailed"` |
| 4.10 | `POST /users/login` with body `{oops` | none | `400` `code="ValidationFailed"` (Malformed request body) |
| 4.11 | `GET /orders/ORD-NOPE-{runTag}` | customer | `404` `code="ResourceNotFound"` |
| 4.12 | `POST /orders/{order2Id}/cancel` | customer | `409` `code="OrderStateViolation"` |
| 4.13 | `POST /payments` for an already-PAID order | customer | `409` `code="PaymentStateViolation"` |
| 4.14a | `POST /orders`, 1 unit | customer | `201` `status="PENDING"` |
| 4.14b | `POST /payments` with amount 1.00 (order is 20.00) | customer | `400` `code="ValidationFailed"` (the amount must equal the order total) |
| 4.14c | `GET /inventory/{productId}` | none | `200`; records `stockBeforeCancel` |
| 4.14d | `POST /orders/{pendingOrderId}/cancel` | customer | `200` `status="CANCELLED"` (PENDING orders can be cancelled too) |
| 4.14e | `GET /inventory/{productId}` | none | `200` `quantityAvailable = stockBeforeCancel + 1` (an unpaid order releases stock as well) |
| 4.15 | `POST /inventory/{productId}/adjust` `delta=-99999` | admin | `409` `code="StockInvariantViolation"` |
| 4.16 | `POST /payments/{failPaymentId}/refund` | customer | `409` `code="PaymentStateViolation"` (only SUCCESS can be refunded) |
| 4.17 | `POST /payments/{payment2Id}/refund` a second time | customer | `409` `code="PaymentStateViolation"` (an active refund already exists) |
| 4.18 | `POST /payments/{paymentId}/refund` while the order is not cancelled | customer | `409` `code="PaymentStateViolation"` (a refund requires a CANCELLED order) |
| 4.19 | `POST /users/register` user B | none | `201` |
| 4.20 | `POST /users/login` user B | none | `200` `{token, userId}` |
| 4.21 | `GET /orders/{orderId}` (B reads A's order) | userB | `403` `code="Forbidden"` |
| 4.22 | `GET /payments/order/{orderId}` (B reads A's payment) | userB | `403` `code="Forbidden"` |
| 4.23 | `POST /products` (the product to deactivate) | admin | `201` |
| 4.24 | `PATCH /products/{deadProductId}/deactivate` | admin | `200` `status="INACTIVE"` |
| 4.25 | `GET /products/{deadProductId}` | none | `200` `status="INACTIVE"`, `stockAvailable=0`, `isInStock=false` |
| 4.26 | `PUT /products/{deadProductId}` | admin | `409` `code="ProductStateViolation"` (INACTIVE is terminal) |
| 4.27 | `GET /products?page=0&size=100` | none | `200`, `content` does **not** contain `deadProductId` |
| 4.28 | `POST /orders` for the deactivated product | customer | `409` `code="StockInvariantViolation"` |

## Folder 5 - Account lifecycle

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 5.1 | `POST /users/password-reset/request` for an existing email | none | `202` with an empty body; console prints the H2 recipe for the token |
| 5.2 | `POST /users/password-reset/request` for an unknown email | none | also `202` (no enumeration, the two are indistinguishable) |
| 5.3 | `POST /users/password-reset/confirm` | none | `200` when `resetToken` is filled in, otherwise the script logs a skip |
| 5.4 | confirm again with the same token | none | `400` `code="ResetTokenInvalidOrExpired"` (single use) |
| 5.5 | `POST /users/login` with the new password | none | `200` |
| 5.6 | `POST /users/login` with the old password | none | `401` `code="InvalidCredentials"` |
| 5.7 | `POST /users/register` throwaway account | none | `201` |
| 5.8 | `POST /users/login` for that account | none | `200` |
| 5.9 | `DELETE /users/me` | throwaway | `204` with an empty body |
| 5.10 | `POST /users/login` after deactivation | none | `401` `code="InvalidCredentials"` |
| 5.11 | `GET /users/me` with the pre-deactivation token | throwaway | `200` with `status="DEACTIVATED"` (deactivation does not revoke an issued JWT) |
| 5.12 | `POST /users/register` for the lockout test | none | `201` |
| 5.13 | wrong password, attempt 1 | none | `401` `code="InvalidCredentials"` |
| 5.14 | wrong password, attempt 2 | none | `401` `code="InvalidCredentials"` |
| 5.15 | wrong password, attempt 3 | none | `401` `code="InvalidCredentials"` |
| 5.16 | wrong password, attempt 4 | none | `401` `code="InvalidCredentials"` |
| 5.17 | wrong password, attempt 5 | none | still `401` `code="InvalidCredentials"` (the account is LOCKED internally at this point) |
| 5.18 | login attempt 6 with the correct password | none | `423` `code="AccountLocked"` (locked for 10 minutes) |

## Folder 6 - AI customer service

| # | Request | Auth | Expected |
| :--- | :--- | :--- | :--- |
| 6.1 | `POST /ai-chat/sessions/{uuid}/messages` | customer | with a working key: `200` `{sessionId, messageId, sender:"AI", content, createdAt}`; without a usable key: `401`, **but that 401 is disguised** (see below) and the step is treated as a skip |
| 6.2 | `GET /ai-chat/sessions?page=0&size=50` | admin | `200` `{content:[...], page, totalElements}` |
| 6.3 | `GET /ai-chat/sessions/{uuid}/messages` | admin | `200`, array with at least 2 entries (USER + AI) |
| 6.4 | `POST /ai-chat/sessions/{uuid}/escalate` | customer | `200` `{sessionId, status:"ESCALATED", updatedAt}` |
| 6.5 | `GET /ai-chat/sessions?page=0&size=10` | customer | `403` `code="Forbidden"` |
| 6.6 | `POST /ai-chat/sessions/{uuid}/messages` with B's token | userB | `404` `code="ResourceNotFound"` (not 403, to avoid leaking session existence) |

Note on session ids: the client generates the UUID as part of the path, and the service creates the session on first use. A malformed id is answered with `404` (`UUID.fromString` fails), and reusing a UUID that already belongs to somebody else is also answered with `404`.

---

## First run, 2026-10-11 (runTag 755719): where the 26 failures came from

| Category | Count | Steps | Nature |
| :--- | :--- | :--- | :--- |
| Poll scripts never read the response body (they reported "not changed within 8s, current null") | 9 | 1.10, 1.12, 1.17, 1.18, 3.4, 3.6, 3.7, 3.10, 4.14e | Collection defect: scripted requests returned no usable body. Hardened to prefer `res.json()`, plus a 0.2 self-test and first-retry diagnostics |
| Cascades of the above | 5 | 1.19 (x2), 2.1, 4.12, 4.17 | Because 1.17 never really cancelled the order, later state assertions no longer matched; all disappear once polling works |
| Wrong assertions | 4 | 1.6, 2.6 (the role is `CUSTOMER`), 2.8, 2.9 (`'Renamed {{runTag}}'` is not substituted inside a script) | Collection defect, fixed |
| AI had no usable key | 6 | 6.1 (x2), 6.2, 6.3, 6.4, 6.6 | Environment: no usable `OPENAI_API_KEY`, and the resulting failure is disguised as 401; 401/500 are now treated as a skip |
| O6 readings delayed by the Streams commit cycle | 2 | 1.20 | Implementation timing characteristic, not a defect (see below) |

> **Retractions.** Two conclusions in the first version of this document were wrong and have been removed. (1) "O6 has two writers that overwrite each other" - `OrderAnalyticsProjectionService.recordOrderPlaced()` starts with `if (streamTopologyActive) return;`, so under the `kafka` profile there is only one writer, the Streams topology. (2) "One stock deduction was lost" - the terminal stock 55 was correct for that run, because 1.17 failed to cancel and the later 4.12 cancel supplied the missing `+1` release. With polling fixed, the next run ended at exactly 54.

## Second run, 2026-10-11 (runTag 517561): 3 failures

| Step | Symptom | Attribution |
| :--- | :--- | :--- |
| 1.17 O4 cancel order | `expected undefined to deeply equal 'CANCELLED'` | Collection defect: the nested callback still used `JSON.parse(res2.body)` (the outer polls had been hardened, this one was missed). The cancel itself succeeded - that run's product ended at exactly **54**, which only holds if 1.17 really cancelled and released one unit. Fixed |
| 1.20 O6 | delta = 0 orders / 0 within 10 s | The poll window was shorter than the topology commit cycle. Under the `kafka` profile O6 is maintained only by the Kafka Streams topology and the window aggregate reaches the read model only after the state-store cache / commit cycle (default commit interval 30 s). Minutes later the same endpoint returned **10 orders / 239.98**, i.e. two runs of four orders each plus the two orders of the older collection - the topology is healthy, just slow. The window is now about 64 s |
| 3.10 I4 alert | no alert within 9 s | **Real defect: the I4 topology is not running at all** (see below) |

### Real finding: I4 (low-stock alerts) is unavailable under the kafka profile

`docker exec 318-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list` lists only three groups:

```
inventory-service     <- I2/I5 Spring Cloud Stream consumers, healthy
order-service         <- O5 consumer, healthy
order-analytics       <- O6 Kafka Streams topology, healthy (LAG=0)
```

**`inventory-alerts` is missing.** `LowStockAlertTopologyConfig` is `@Configuration @Profile("kafka")`, so a successful `KafkaStreams.start()` must create that consumer group (the O6 group is proof of the mechanism). The I4 Streams instance is therefore not running healthily - it either never started or dies immediately - and consequently `GET /inventory/alerts` can never return an alert: **requirement I4 is effectively unavailable under the kafka profile**, which is exactly the "automatic low-stock alert" feature that has to be demonstrated.

Supporting evidence from the local state directory: `%TEMP%\inventory-alerts-state\inventory-alerts\` contains only `.lock` and `kafka-streams-process-metadata`, with no task directory, while the working `order-analytics` state dir contains a task directory that is still being written to. That means the client was constructed at some point but has never created a task, i.e. it never joined the group.

Suggested order of investigation: (1) grep the inventory-Service console for `KafkaStreams`, `StreamsException`, `state directory`, `ERROR`; (2) delete `%TEMP%\inventory-alerts-state` and restart with the kafka profile (a stale or incompatible state directory can abort startup before the first join); (3) rebuild the service first (Build -> Rebuild Project) in case the running process predates the topology fix; (4) after restarting, confirm the group appears with the command above and re-run 3.10 / 3.12.

### Real finding: the AI service disguises downstream failures as 401

When the model call fails, `POST /ai-chat/sessions/{id}/messages` answers with

```
{"code":"Unauthorized","message":"Missing or invalid authentication token","timestamp":1791702297.166458000,"details":null}
```

which looks like a bad token but is an error-masking bug. The chain is: the LLM call throws, no `@ExceptionHandler` covers that exception, Spring Boot forwards to `/error`, that ERROR dispatch also passes through the security chain, but `JwtAuthenticationFilter` extends `OncePerRequestFilter` (`shouldNotFilterErrorDispatch` defaults to true) and skips it - so the internal dispatch is anonymous, `anyRequest().authenticated()` denies it, and the entry point writes the 401. Any model-layer failure (bad key, unknown model, network error) is compressed into the same 401.

Three discriminating experiments, all with one valid admin token:

| Experiment | Result |
| :--- | :--- |
| `GET /ai-chat/sessions` | `200` -> the auth layer is fine, the token is not the problem |
| `POST .../messages` with body `{}` | `400 ValidationFailed` -> the request does reach the business code |
| `POST .../messages` with a valid body | `401` with the entry-point body -> only the actual model call fails |

A reproducible fingerprint: that 401 carries a **numeric** `timestamp` (`1791702297.166...`) whereas the 400 carries an **ISO string** (`"2026-10-11T07:05:49.709...Z"`), because `RestAuthenticationEntryPoint` constructs its own `new ObjectMapper()` (Jackson writes instants as numbers by default) while business exceptions go through Spring's ObjectMapper. The AI service is the only one of the six that builds its own mapper, which confirms the 401 was written there.

Two-line fix: allow `/error` and ERROR dispatches in `SecurityConfig` (`.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` and `.requestMatchers("/error").permitAll()`), and add an `@ExceptionHandler` for the LLM exceptions that returns 502/500 with the provider's message. Without it, every model failure looks like "invalid token", which cost several rounds of debugging here.

**Status: fixed** on branch `fix/ai-error-masking` (code + two regression tests). Verified against a real container, not just mocks:

| Scenario | Before (measured) | After (measured) |
| :--- | :--- | :--- |
| `POST /api/v1/ai-chat/nope` (unmatched path) | `401` | `404` |
| Real model call with an invalid API key | `401 Missing or invalid authentication token` | `502` `{"code":"ModelCallFailed","message":"Upstream model call failed (HTTP 401): ... Your api key: z is invalid ..."}` |

Regression tests added: `AiCustomerServiceModelFailureTests` asserts the 502 mapping (with the provider message) and that a failed model call leaves no partially created session behind, per the invariant documented in `ConversationCommandService`.

Root cause of this run, from the service console: `dev.ai4j.openai4j.OpenAiHttpException: {"error":{"message":"Authentication Fails, Your api key: z is invalid"...}}` - the running process was still using the default placeholder key `z`, because `OPENAI_API_KEY` was not present in the process environment (the model bean is built once at startup, so the service must be restarted after setting it).

**Status after the fix:** with `OPENAI_API_KEY` added to the run configuration and the service restarted, `POST .../messages` returns `200` and both tools are demonstrably grounded - asking about `PRD-DB9A672D` returned `20.00 AUD` and the live stock `48` (both matching the real values), and asking with the order owner's token about `ORD-D08E9549` returned `PENDING` / `20.00 AUD`, while asking about the same order with a different user's token returns "not found" (the tool forwards the caller's token, so order-service ownership still applies).

### Two timing characteristics (not defects, but worth documenting)

- **O6 and I4 are eventually consistent.** Both Kafka Streams topologies publish through the state-store cache and the commit cycle (30 s by default), so second-level readings can stay unchanged or be too low. If fresher readings are required, lower `commit.interval.ms` (e.g. 100 ms) or set `cache.max.bytes.buffering=0` on those topologies.
- **`order.created` messages carry no key** (StreamBridge default), so partition round-robin lets the topology see a later order first; a window reading can therefore be briefly lower than the true value.

---

## Worth calling out in the report

| Phenomenon | Why it matters |
| :--- | :--- |
| 1.10 / 1.12 / 1.18 / 3.6 / 3.7 / 4.14e | These six only pass when Kafka really delivers and consumes, so they are direct evidence of the event-driven flow (`order.created`, `payment.confirmed`, `order.cancelled`, `payment.failed`) |
| 1.20 | O6 counts `OrderPlaced` events, so cancelled or refunded orders still count in the window - which is why the step asserts a delta rather than an absolute value |
| 4.25 | P2 degrades to `stockAvailable=0, isInStock=false` when inventory has no record, instead of failing the whole detail request |
| 5.11 | A deactivated account can still read its own profile with the old JWT (each service verifies only the signature) - a deliberate trade-off of this iteration |
| 5.18 | The lockout takes effect on the *next* login: attempt 5 still returns 401, attempt 6 returns 423 |
| 4.21 / 4.22 / 6.6 | Cross-user isolation: orders and payments answer 403, AI sessions answer 404 to avoid leaking existence |
| AI service 401 masking | Any model failure was reported as "invalid token", hiding the real cause; **fixed** on `fix/ai-error-masking` (see the before/after measurements above) |
| I4 topology missing | The alert feature cannot work at all while consumer group `inventory-alerts` does not exist - the strongest defect found by this test suite |
