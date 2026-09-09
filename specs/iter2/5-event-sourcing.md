# Event Sourcing Specification

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review

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
- Each `productId` maps to exactly one `StockItem` stream (1:1, per `specs/3-domain-models.md` §8) — there is no per-warehouse or per-location dimension in the current architecture.

### 3.3 Event Types for `StockItemAggregate`

| Event Type | Triggering Behavior | Key Payload Fields |
| :--- | :--- | :--- |
| `StockItemInitialized` | First-time creation of a stock record for a `productId` not yet tracked (lazy-initialized on the first `adjust()` or `deduct()` call) | `productId`, `initialQuantity` |
| `StockDeducted` | `deduct(quantity)` — consumes `OrderCreatedEvent` (I2) | `productId`, `quantity`, `orderId` |
| `StockReleased` | `release(quantity)` — consumes `OrderCancelledEvent` / `PaymentFailedEvent` (I5) | `productId`, `quantity`, `orderId` |
| `StockAdjusted` | `adjust(delta, reason, adjustedBy)` — manual restock/correction by an Admin (I3) | `productId`, `delta`, `reasonCode`, `adjustedBy` |

> **Note:** `StockItemInitialized` is not yet listed as a domain event in `specs/iter2/3-domain-models-inventory-service-ddd.md` §2.5 — it is introduced here because an event-sourced aggregate needs an explicit first event to start its stream. This should be confirmed with the team and folded back into that domain model spec.

> **Note:** `LowStockAlert` is intentionally out of scope for event sourcing — its open/resolved lifecycle (`LowStockAlertTriggered` / `LowStockAlertResolved`) is simple enough to remain a CRUD aggregate, consistent with §1.
