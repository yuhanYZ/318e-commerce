# Domain Models Specification - Payment Service

Evolution from `specs/3-domain-models.md`:
- `Payment` and `Refund` promoted to aggregate roots; `amount` re-typed as `Money`, statuses re-typed as value objects.
- `Payment.method` fixed to a constant `"MOCK"` per the earlier decision (no real/sandbox gateway integration in this project) — kept as a field for schema stability, but no longer a meaningful variation point.
- `Refund.amount` constrained to always equal the original `Payment.amount` per the earlier decision (full refunds only, no partial refund support).

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `Payment` | Aggregate Root | Payment transaction for an order |
| `Refund` | Aggregate Root | Refund request against a `Payment` |
| `PaymentId` | Value Object | Typed, validated payment identifier |
| `OrderId` | Value Object | Typed reference to an `Order` (cross-service ID only) |
| `Money` | Value Object | Amount used for `Payment.amount` and `Refund.amount` |
| `PaymentStatus` | Value Object | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` |
| `RefundStatus` | Value Object | `REQUESTED`, `PROCESSED`, `REJECTED` |
| `PaymentInitiated` | Domain Event | Emitted by `PaymentAggregate` when a payment attempt begins (PAY1) |
| `PaymentSucceeded` | Domain Event | Emitted by `PaymentAggregate` on a successful payment |
| `PaymentFailed` | Domain Event | Emitted by `PaymentAggregate` on a failed payment |
| `RefundRequested` | Domain Event | Emitted by `RefundAggregate` when a refund is requested (PAY3) |
| `RefundProcessed` | Domain Event | Emitted by `RefundAggregate` when a refund is approved and completed |
| `RefundRejected` | Domain Event | Emitted by `RefundAggregate` when a refund request is denied |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **PAY1. Pay for an Order** | `Payment` | `PaymentInitiated`, `PaymentSucceeded` / `PaymentFailed` |
| **PAY2. View Payment Status** | `Payment` | None |
| **PAY3. Request a Refund** | `Payment`, `Refund` | `RefundRequested`, `RefundProcessed` / `RefundRejected` |
| **PAY4. View Payment / Refund History** | `Payment`, `Refund` | None |

---

## 2. DDD Building Blocks

### 2.1 Entities

*(none — all identity-bearing objects in this service are aggregate roots)*

---

### 2.2 Value Objects

#### 2.2.1 `PaymentId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `OrderId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the paid order |

#### 2.2.3 `Money`

| Field | Type | Description |
| :--- | :--- | :--- |
| `amount` | `BigDecimal` | Monetary amount (non-negative) |
| `currency` | `String` | ISO 4217 currency code (e.g., `"AUD"`) |

#### 2.2.4 `PaymentStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` |

#### 2.2.5 `RefundStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `REQUESTED`, `PROCESSED`, `REJECTED` |

---

### 2.3 Aggregates

#### 2.3.1 `PaymentAggregate` (root: `Payment`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `PaymentId` | Unique payment identifier | Primary identifier |
| `orderId` | `OrderId` | Reference to the `Order` | Required, cross-service ID only |
| `amount` | `Money` | Payment amount | Non-negative |
| `status` | `PaymentStatus` | Current payment status | Default: `PENDING` |
| `method` | `String` | Payment method | Fixed as `"MOCK"` — no real gateway integration |
| `createdAt` | `LocalDateTime` | Payment creation timestamp | Auto-generated |

**Invariants**
1. `amount.amount >= 0`, and `amount` must equal the linked order's `totalAmount` (validated by the application service against Order Service before `initiate`; mismatch → HTTP 400 `ValidationFailed`).
2. `status` follows `PENDING → SUCCESS | FAILED`; `SUCCESS → REFUNDED` only once an associated `Refund` is processed. `FAILED` and `REFUNDED` are terminal.
3. A payment may only be initiated for an order whose status is exactly `PENDING` (checked via `GET /orders/{id}`); otherwise HTTP 409 `PaymentStateViolation`.
4. At most one non-`FAILED` `Payment` per `orderId`.

**Mock Gateway Rule (deterministic, for testability)**
- `method` is always `"MOCK"`. The gateway succeeds by default.
- The gateway simulates a failure when the fractional part of `amount.amount` is exactly `.99` (e.g., `19.99`), producing `PaymentFailed`. This rule exists solely so that the `PaymentFailedEvent` path (O5, I5) can be exercised in integration tests.

**Behaviors**
- `initiate(orderId, amount)` — emits `PaymentInitiated`
- `markSucceeded()` — only from `PENDING`; emits `PaymentSucceeded`
- `markFailed()` — only from `PENDING`; emits `PaymentFailed`
- `markRefunded()` — only from `SUCCESS`; sets `status = REFUNDED` when the linked `Refund` completes (no separate event; see `RefundProcessed`)

#### 2.3.2 `RefundAggregate` (root: `Refund`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique refund identifier | Primary identifier |
| `paymentId` | `PaymentId` | Reference to the `Payment` | Required |
| `amount` | `Money` | Refund amount | Must equal the original `Payment.amount` — full refunds only |
| `status` | `RefundStatus` | Current refund status | Default: `REQUESTED` |
| `requestedAt` | `LocalDateTime` | Refund request timestamp | Auto-generated |
| `processedAt` | `LocalDateTime?` | Refund processed timestamp | Null until processed |

**Invariants**
1. `amount` must equal the associated `Payment.amount` (no partial refunds).
2. At most one active (non-`REJECTED`) `Refund` per `Payment`.
3. A refund may only be requested when the `Payment` is `SUCCESS` **and** the linked order is `CANCELLED` (checked by the application service via `GET /orders/{id}`); otherwise HTTP 409 `PaymentStateViolation`.
4. With the mock gateway a requested refund is always processed immediately (`REQUESTED → PROCESSED` in the same command). `REJECTED` / `RefundRejected` are reserved for a future real-gateway integration and are never produced in this iteration.

**Behaviors**
- `request(paymentId, amount)` — emits `RefundRequested`
- `process(now)` — only from `REQUESTED`; emits `RefundProcessed`
- `reject(now)` — only from `REQUESTED`; emits `RefundRejected` *(reserved, unused)*

**Integration Events (Kafka)**

| Domain Event | Published Kafka Event | Consumer(s) |
| :--- | :--- | :--- |
| `PaymentSucceeded` | `PaymentConfirmedEvent` (`paymentId`, `orderId`) | Order Service (O5) |
| `PaymentFailed` | `PaymentFailedEvent` (`paymentId`, `orderId`) | Order Service (O5), Inventory Service (I5) |
| `RefundProcessed` | `RefundProcessedEvent` (`refundId`, `paymentId`, `orderId`) | *(no consumer in this iteration; published for audit/analytics)* |
| `PaymentInitiated`, `RefundRequested`, `RefundRejected` | *(none — internal only)* | — |

---

### 2.4 Domain Services

*(none — payment and refund rules are simple enough to live entirely on the aggregates)*

---

### 2.5 Domain Events

#### 2.5.1 `PaymentInitiated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `paymentId` | `PaymentId` | The initiated payment |
| `orderId` | `OrderId` | The associated order |
| `amount` | `Money` | Payment amount |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `PaymentSucceeded`

| Field | Type | Description |
| :--- | :--- | :--- |
| `paymentId` | `PaymentId` | The successful payment |
| `orderId` | `OrderId` | The associated order |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `PaymentFailed`

| Field | Type | Description |
| :--- | :--- | :--- |
| `paymentId` | `PaymentId` | The failed payment |
| `orderId` | `OrderId` | The associated order |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.4 `RefundRequested`

| Field | Type | Description |
| :--- | :--- | :--- |
| `refundId` | `String` | The requested refund |
| `paymentId` | `PaymentId` | The associated payment |
| `amount` | `Money` | Refund amount |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.5 `RefundProcessed`

| Field | Type | Description |
| :--- | :--- | :--- |
| `refundId` | `String` | The processed refund |
| `paymentId` | `PaymentId` | The associated payment |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.6 `RefundRejected`

| Field | Type | Description |
| :--- | :--- | :--- |
| `refundId` | `String` | The rejected refund |
| `paymentId` | `PaymentId` | The associated payment |
| `occurredAt` | `LocalDateTime` | Event timestamp |
