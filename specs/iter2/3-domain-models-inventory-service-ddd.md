# Domain Models Specification - Inventory Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

Evolution from `specs/iter1/3-domain-models.md` (Iteration 1, archived for history):
- `StockItem` promoted to an aggregate root; `quantityAvailable` re-typed as a validated `Quantity` value object.
- `StockItem` is the only **event-sourced** aggregate in the system (see `5-event-sourcing.md`). Its identity `StockItemId` takes the same value as `ProductId` — one product maps to exactly one event stream — so `StockItemId` and `ProductId` are interchangeable for stream lookup.
- `lowStockThreshold` **removed from `StockItem`** — per the earlier decision, the threshold is a single global system-wide value rather than a per-product field. It now lives as configuration read by `InventoryPolicyService`.
- `StockAdjustmentLog` kept as a plain `Entity` (append-only audit record, no lifecycle of its own).
- `LowStockAlert` promoted to its own small aggregate, since its open/resolved lifecycle is a real invariant (no duplicate open alerts per stock item).

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `StockItem` | Aggregate Root | Tracks the stock level of a specific product |
| `StockAdjustmentLog` | Entity | Append-only record of a manual stock adjustment made by an Admin |
| `LowStockAlert` | Aggregate Root | An open or resolved low-stock alert for a `StockItem` |
| `StockItemId` | Value Object | Typed, validated stock item identifier |
| `ProductId` | Value Object | Typed reference to a `Product` (cross-service ID only) |
| `Quantity` | Value Object | Non-negative validated quantity |
| `StockDeducted` | Domain Event | Emitted by `StockItemAggregate` on consuming `OrderCreatedEvent` (I2) |
| `StockReleased` | Domain Event | Emitted by `StockItemAggregate` on consuming `OrderCancelledEvent` (I5) |
| `StockAdjusted` | Domain Event | Emitted by `StockItemAggregate` on a manual restock/adjustment (I3) |
| `LowStockAlertTriggered` | Domain Event | Emitted by `LowStockAlertAggregate` when available stock drops below the global threshold (I4) |
| `LowStockAlertResolved` | Domain Event | Emitted by `LowStockAlertAggregate` when stock rises back above the threshold |
| `InventoryPolicyService` | Domain Service | Reads the global `lowStockThreshold` config and checks a `StockItem` against it |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **I1. Check Stock Availability** | `StockItem` | None |
| **I2. Deduct Stock on Order Created** | `StockItem`, `InventoryPolicyService` | `StockDeducted`, `LowStockAlertTriggered` (if threshold crossed) |
| **I3. Adjust Stock (Restock)** | `StockItem`, `StockAdjustmentLog` | `StockAdjusted`, `LowStockAlertResolved` (if threshold cleared) |
| **I4. Receive Low-Stock Alert** | `StockItem`, `LowStockAlert`, `InventoryPolicyService` | `LowStockAlertTriggered` |
| **I5. Release Stock on Order Cancelled** | `StockItem` | `StockReleased` |

---

## 2. DDD Building Blocks

### 2.1 Entities

#### 2.1.1 `StockAdjustmentLog`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique log entry identifier | Primary identifier |
| `stockItemId` | `StockItemId` | Reference to the `StockItem` | Required |
| `adjustedBy` | `String` / `UUID` | User ID of the Admin performing the adjustment | Required |
| `delta` | `int` | Adjustment amount | Positive = restock, negative = deduction |
| `reason` | `String` | Optional adjustment note | Optional |
| `createdAt` | `LocalDateTime` | Adjustment timestamp | Auto-generated |

---

### 2.2 Value Objects

#### 2.2.1 `StockItemId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `ProductId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the tracked product |

#### 2.2.3 `Quantity`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `value` | `int` | Stock count | `value >= 0` |

---

### 2.3 Aggregates

#### 2.3.1 `StockItemAggregate` (root: `StockItem`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `StockItemId` | Unique stock item identifier | Primary identifier |
| `productId` | `ProductId` | Reference to the `Product` | Required, unique |
| `quantityAvailable` | `Quantity` | Current available stock | `>= 0` |
| `updatedAt` | `LocalDateTime` | Last stock change timestamp | Auto-updated |

*Note: `lowStockThreshold` is no longer a field here — it is a single global value read by `InventoryPolicyService` (see 2.4).*

**Invariants**
1. `quantityAvailable >= 0` at all times.
2. A deduction cannot take `quantityAvailable` below zero.
3. There is no explicit "initialize" event. A `StockItem` comes into existence with the **first `StockAdjusted`** event on its stream (an Admin restock, I3), starting from `quantityAvailable = 0`. `deduct()` or `release()` on a `productId` with no stream is a violation of invariant 2 (rejected as HTTP 409 `StockInvariantViolation` / logged for event handlers); `GET /inventory/{productId}` on such a product returns HTTP 404.

**Behaviors**
- `deduct(quantity, orderId)` — emits `StockDeducted`
- `release(quantity, orderId)` — emits `StockReleased`
- `adjust(delta, reason, adjustedBy)` — appends a `StockAdjustmentLog` entry, emits `StockAdjusted`

**Event-Sourcing Note**
- The aggregate is rehydrated by replaying its event stream; `quantityAvailable` is never persisted directly on the write side. The `inventory_read_model` projection (I1) and the low-stock evaluation (I4) are updated from the same events.
- `StockAdjustmentLog` is retained as a convenience audit entity but is fully derivable from `StockAdjusted` events.

#### 2.3.2 `LowStockAlertAggregate` (root: `LowStockAlert`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique alert identifier | Primary identifier |
| `stockItemId` | `StockItemId` | Reference to the `StockItem` | Required |
| `triggeredAt` | `LocalDateTime` | Alert trigger timestamp | Auto-generated |
| `resolvedAt` | `LocalDateTime?` | Timestamp when stock rose back above threshold | Null until resolved |

**Invariants**
1. At most one unresolved (`resolvedAt = null`) alert may exist per `stockItemId` at a time — prevents duplicate alerts.

**Behaviors**
- `trigger(stockItemId, now)` — emits `LowStockAlertTriggered`
- `resolve(now)` — emits `LowStockAlertResolved`

---

### 2.4 Domain Services

#### 2.4.1 `InventoryPolicyService`

| Method | Parameters | Returns | Description |
| :--- | :--- | :--- | :--- |
| `isBelowThreshold` | `quantityAvailable: Quantity` | `boolean` | Compares available stock against the single global `lowStockThreshold` configuration (`app.inventory.low-stock-threshold`, default `5`) |

---

### 2.4.2 Integration Events (Kafka)

| Direction | Kafka Event | Handled By | Aggregate Behavior |
| :--- | :--- | :--- | :--- |
| Consumed | `OrderCreatedEvent` (from Order Service) | I2 handler | `deduct(quantity, orderId)` per item |
| Consumed | `OrderCancelledEvent` (from Order Service) | I5 handler | `release(quantity, orderId)` per item |
| Consumed | `PaymentFailedEvent` (from Payment Service) | I5 handler | `release(quantity, orderId)` per item — Inventory resolves the order's items from its own `StockDeducted` history for that `orderId`, so `PaymentFailedEvent` only needs to carry `orderId` |
| Published | *(none)* | — | `StockDeducted` / `StockReleased` / `StockAdjusted` / `LowStockAlert*` are internal domain events; the `StockUpdatedEvent` from Iteration 1 (archived) has no consumer and is not published |

Both I2 and I5 handlers are idempotent per (`orderId`, `productId`): a redelivered Kafka message must not deduct or release twice.

---

### 2.5 Domain Events

#### 2.5.1 `StockDeducted`

| Field | Type | Description |
| :--- | :--- | :--- |
| `stockItemId` | `StockItemId` | The affected stock item |
| `quantity` | `Quantity` | Amount deducted |
| `orderId` | `String` | The order that triggered the deduction |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `StockReleased`

| Field | Type | Description |
| :--- | :--- | :--- |
| `stockItemId` | `StockItemId` | The affected stock item |
| `quantity` | `Quantity` | Amount released |
| `orderId` | `String` | The cancelled order that triggered the release |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `StockAdjusted`

| Field | Type | Description |
| :--- | :--- | :--- |
| `stockItemId` | `StockItemId` | The adjusted stock item |
| `delta` | `int` | Change in stock (positive = added, negative = removed) |
| `reason` | `String` | Adjustment note |
| `adjustedBy` | `String` | Admin who performed the adjustment |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.4 `LowStockAlertTriggered`

| Field | Type | Description |
| :--- | :--- | :--- |
| `alertId` | `String` | The triggered alert |
| `stockItemId` | `StockItemId` | The affected stock item |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.5 `LowStockAlertResolved`

| Field | Type | Description |
| :--- | :--- | :--- |
| `alertId` | `String` | The resolved alert |
| `stockItemId` | `StockItemId` | The affected stock item |
| `occurredAt` | `LocalDateTime` | Event timestamp |
