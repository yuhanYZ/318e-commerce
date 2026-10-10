# Domain Models Specification - Order Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

Evolution from `specs/iter1/3-domain-models.md` (Iteration 1, archived for history):
- `Order` promoted to an aggregate root; `OrderItem` remains composed inside it (not an independent aggregate, unchanged from the original design).
- `status` transitions made explicit as aggregate behaviors, each emitting its own domain event instead of a single generic "status changed" update.
- `userId`/`productId` fields re-typed as value objects to make the cross-service, ID-only nature of these references explicit.

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `Order` | Aggregate Root | Customer's order; the most state-heavy entity in the system |
| `OrderItem` | Entity | Line item within an `Order`; composition, not an independent aggregate |
| `OrderId` | Value Object | Typed, validated order identifier |
| `UserId` | Value Object | Typed reference to a `User` (cross-service ID only) |
| `ProductId` | Value Object | Typed reference to a `Product` (cross-service ID only) |
| `Money` | Value Object | Amount used for `totalAmount` and `unitPrice` |
| `Quantity` | Value Object | Non-negative validated quantity |
| `OrderStatus` | Value Object | `PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED` |
| `OrderPlaced` | Domain Event | Emitted by `OrderAggregate` on successful order placement (O1) |
| `OrderPaid` | Domain Event | Emitted by `OrderAggregate` on consuming `PaymentConfirmedEvent` (O5) |
| `OrderPaymentFailed` | Domain Event | Emitted by `OrderAggregate` on consuming `PaymentFailedEvent` (O5) |
| `OrderCancelled` | Domain Event | Emitted by `OrderAggregate` when a customer cancels an order (O4) |
| `OrderAnalyticsWindow` | Read Model (Projection) | Rolling one-minute buckets of order count / revenue, derived from `OrderPlaced`; powers O6 |
| `OrderAnalyticsService` | Domain Service | Pure calculation that sums the buckets falling inside a requested sliding window into `orderCount` / `totalRevenue` / `averageOrderValue` |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **O1. Place an Order** | `Order`, `OrderItem` | `OrderPlaced` |
| **O2. View Order Status** | `Order` | None |
| **O3. View Order History** | `Order` | None |
| **O4. Cancel an Order** | `Order` | `OrderCancelled` |
| **O5. Update Order Status on Payment Event** | `Order` | `OrderPaid`, `OrderPaymentFailed` |
| **O6. View Real-Time Order Volume & Revenue Analytics** | `OrderAnalyticsWindow`, `OrderAnalyticsService` | Reads `OrderPlaced` (no new event emitted) |

---

## 2. DDD Building Blocks

### 2.1 Entities

#### 2.1.1 `OrderItem`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique order item identifier | Primary identifier within the aggregate |
| `productId` | `ProductId` | Reference to the `Product` | Required, cross-service ID only |
| `quantity` | `Quantity` | Quantity ordered | Positive |
| `unitPrice` | `Money` | Price at time of order | Snapshot value, unaffected by later price changes |

---

### 2.2 Value Objects

#### 2.2.1 `OrderId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `UserId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the ordering user |

#### 2.2.3 `ProductId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the ordered product |

#### 2.2.4 `Money`

| Field | Type | Description |
| :--- | :--- | :--- |
| `amount` | `BigDecimal` | Monetary amount (non-negative) |
| `currency` | `String` | ISO 4217 currency code (e.g., `"AUD"`) |

#### 2.2.5 `Quantity`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `value` | `int` | Number of units | `value > 0` |

#### 2.2.6 `OrderStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED` |

---

### 2.3 Aggregates

#### 2.3.1 `OrderAggregate` (root: `Order`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `OrderId` | Unique order identifier | Primary identifier |
| `userId` | `UserId` | Reference to the ordering `User` | Required, cross-service ID only |
| `items` | `List<OrderItem>` | Line items | At least one item |
| `status` | `OrderStatus` | Current order status | Default: `PENDING` |
| `totalAmount` | `Money` | Total order value | Non-negative; equals the sum of `item.unitPrice × item.quantity` |
| `createdAt` | `LocalDateTime` | Order creation timestamp | Auto-generated |
| `updatedAt` | `LocalDateTime` | Last status change timestamp | Auto-updated |

**Invariants**
1. `totalAmount` equals the sum of all `OrderItem.unitPrice × OrderItem.quantity`. Each `unitPrice` is a snapshot of the Product Service price at placement time.
2. Status transitions are restricted to: `PENDING → PAID`, `PENDING → PAYMENT_FAILED`, `{PENDING, PAID} → CANCELLED`.
3. `PAYMENT_FAILED` and `CANCELLED` are terminal statuses — no further transitions (no re-payment, no cancellation) are possible once reached.
4. Shipping is out of scope for this iteration; there is no `SHIPPED` status.

**Behaviors**
- `place(userId, items)` — emits `OrderPlaced`
- `markPaid()` — only from `PENDING`; emits `OrderPaid`
- `markPaymentFailed()` — only from `PENDING`; emits `OrderPaymentFailed`
- `cancel()` — only from `PENDING` or `PAID`; emits `OrderCancelled`

**Integration Events (Kafka)**

| Domain Event | Published Kafka Event | Consumer(s) |
| :--- | :--- | :--- |
| `OrderPlaced` | `OrderCreatedEvent` (`orderId`, `userId`, `items[{productId, quantity}]`) | Inventory Service (I2) |
| `OrderCancelled` | `OrderCancelledEvent` (`orderId`, `items[{productId, quantity}]`) | Inventory Service (I5) |
| `OrderPaid` / `OrderPaymentFailed` | *(none — internal only)* | — |

---

### 2.4 Domain Services

#### 2.4.1 `OrderAnalyticsService`

| Method | Parameters | Returns | Description |
| :--- | :--- | :--- | :--- |
| `summarize` | `buckets: List<OrderAnalyticsWindow>`, `windowMinutes: int`, `now: LocalDateTime` | `OrderAnalyticsSummary { orderCount, totalRevenue, averageOrderValue }` | Sums the buckets whose `windowStart` falls within `[now - windowMinutes, now]`; buckets outside the range are ignored, not deleted. Pure calculation — no persistence or I/O. |

*(Order placement's status transitions and total-amount calculation remain simple enough to live entirely on the `OrderAggregate` and need no domain service.)*

---

### 2.5 Domain Events

#### 2.5.1 `OrderPlaced`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The newly placed order |
| `userId` | `UserId` | The ordering user |
| `totalAmount` | `Money` | Total order value |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `OrderPaid`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The paid order |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `OrderPaymentFailed`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The order whose payment failed |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.4 `OrderCancelled`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The cancelled order |
| `occurredAt` | `LocalDateTime` | Event timestamp |

---

### 2.6 Read Models (CQRS Projections)

#### 2.6.1 `OrderAnalyticsWindow`

Not part of the `OrderAggregate` write model — a CQRS read-side projection maintained by the Spring Cloud Stream tumbling-window function backing O6 (see `6-cqrs-command-query.md` §4.2 and §7).

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `windowStart` | `LocalDateTime` | Start of this bucket's minute (event timestamp truncated to the minute) | Primary identifier |
| `orderCount` | `int` | Number of orders placed within this one-minute bucket | `>= 0` |
| `totalRevenue` | `Money` | Sum of `totalAmount` for orders placed within this bucket | `>= 0` |
| `updatedAt` | `LocalDateTime` | Last time this bucket was incremented | Auto-updated |

**Notes**
- One row per minute; a bucket is created on the first `OrderPlaced` event in that minute and incremented on each subsequent one.
- `GetRealtimeOrderAnalyticsQuery(windowMinutes)` (O6) reads the buckets covering the requested trailing window and passes them to `OrderAnalyticsService.summarize()` — it never reads or mutates the `OrderAggregate` write model directly.
- Buckets older than the maximum configurable window (e.g. 24h) may be purged by a periodic housekeeping job; this is a storage-management detail, not a domain rule.
