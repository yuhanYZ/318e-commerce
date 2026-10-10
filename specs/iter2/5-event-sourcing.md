# Event Sourcing Specification

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

## 1. Scope
- This specification defines event sourcing for the **318 E-Commerce Platform**, where only `StockItemAggregate` (Inventory Service) is event-sourced.
- Other aggregates (`UserAggregate`, `PasswordResetTokenAggregate`, `ProductAggregate`, `OrderAggregate`, `LowStockAlertAggregate`, `PaymentAggregate`, `RefundAggregate`, `ConversationSessionAggregate`) remain CRUD-based.

---

## 2. Goals
- Preserve inventory state transitions (deductions, releases, manual adjustments) as immutable events.
- Support inventory availability queries via a projected read model (`inventory_read_model`).
- Provide a replayable stock history for auditing and analysis, superseding the manually-written `StockAdjustmentLog` with a full event-derived audit trail.

---

## 3. Event Model

### 3.1 Event Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `eventId` | UUID | Unique event identity. Used for idempotency deduplication. |
| `aggregateId` | String | Identity of the aggregate instance — the tracked `productId` (e.g., `"PROD-00123"`) |
| `aggregateType` | String | Always `"StockItemAggregate"` |
| `eventType` | String | One of the event types listed in §3.3 |
| `aggregateVersion` | long | Monotonically increasing version of the aggregate stream after this event |
| `occurredAt` | timestamp | Wall-clock time the event was produced |
| `payload` | JSON | Event-specific data (state change fields) |

### 3.2 Event Stream Identity
- Stream key = `aggregateType` + `aggregateId`, e.g. `"StockItemAggregate:PROD-00123"`.
- Each `productId` maps to exactly one `StockItem` stream (1:1, per `specs/iter1/3-domain-models.md` §8) — there is no per-warehouse or per-location dimension in the current architecture.

### 3.3 Event Types for `StockItemAggregate`

| Event Type | Triggering Behavior | Key Payload Fields |
| :--- | :--- | :--- |
| `StockDeducted` | `deduct(quantity)` — consumes `OrderCreatedEvent` (I2) | `productId`, `quantity`, `orderId` |
| `StockReleased` | `release(quantity)` — consumes `OrderCancelledEvent` / `PaymentFailedEvent` (I5) | `productId`, `quantity`, `orderId` |
| `StockAdjusted` | `adjust(delta, reason, adjustedBy)` — manual restock/correction by an Admin (I3) | `productId`, `delta`, `reason`, `adjustedBy` |

### 3.4 Persistence and Replay
- Event store table (H2 in development): `stock_event(event_id PK, aggregate_id, aggregate_type, event_type, aggregate_version, occurred_at, payload)` with a unique constraint on (`aggregate_id`, `aggregate_version`) for optimistic concurrency — a concurrent append with a stale version is retried by reloading the stream.
- Rehydration: load all events for `aggregate_id` ordered by `aggregate_version` and apply them in order; no snapshots in this iteration.
- Idempotency: I2/I5 handlers derive a deterministic `eventId` from (`orderId`, `productId`, `eventType`) so a redelivered Kafka message is a no-op insert.

### 3.5 Read Model
- `inventory_read_model(product_id PK, quantity_available, version, updated_at)` is updated synchronously in the same transaction as the event append (single-service, single-database), so I1 reads are effectively consistent; the "eventual (bounded)" label in `6-cqrs-command-query.md` §7 reflects the architectural allowance, not a required delay.
- The low-stock evaluation (I4) runs on each `StockDeducted` / `StockReleased` / `StockAdjusted` via a Spring Cloud Stream function and updates the CRUD `LowStockAlert` aggregate.

> **Note:** There is no explicit initialization event. A stream starts with its first `StockAdjusted` (an Admin restock, I3) from an implicit quantity of `0` — see `3-domain-models-inventory-service-ddd.md` §2.3.1 invariant 3. A `deduct()` / `release()` against a `productId` with no stream is rejected.

> **Note:** `LowStockAlert` is intentionally out of scope for event sourcing — its open/resolved lifecycle (`LowStockAlertTriggered` / `LowStockAlertResolved`) is simple enough to remain a CRUD aggregate, consistent with §1.
