# User Stories — Inventory Service

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review
**Source:** Split from [2-user-stories.md](./2-user-stories.md)

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| Inventory Service | I1 | Check Stock Availability | Customer |
| Inventory Service | I2 | Deduct Stock on Order Created | System |
| Inventory Service | I3 | Adjust Stock (Restock) | Admin |
| Inventory Service | I4 | Receive Low-Stock Alert | Admin |
| Inventory Service | I5 | Release Stock on Order Cancelled | System |

---

## Inventory Service

### User Story I1: Check Stock Availability
- **As a** Customer, **I want to** see whether a product is in stock before ordering.
- **Scenario:**
  i. The user (directly, or via Product Service) queries Inventory Service for a product's stock level.
  ii. Inventory Service returns the current quantity, or an "out of stock" indicator if it is zero.

### User Story I2: Deduct Stock on Order Created
- **As the** Inventory Service (System), **I want to** deduct stock automatically when an order is created.
- **Scenario:**
  i. Order Service publishes an `OrderCreatedEvent` to Kafka after creating an order.
  ii. Inventory Service consumes the event and executes `StockItemAggregate.deduct(quantity)` for each item.
  iii. On success, the aggregate emits `StockDeducted` and the event is appended to the event store. If a race condition leaves stock insufficient (or the `productId` is not yet tracked), the deduction is rejected and logged — no compensating event is published. The synchronous stock check performed by Order Service before order creation (O1) is the primary safeguard for this milestone.

### User Story I3: Adjust Stock (Restock)
- **As an** Admin, **I want to** manually adjust stock quantities.
- **Scenario:**
  i. The Admin submits a stock adjustment (positive or negative) for a product to Inventory Service.
  ii. Inventory Service validates the adjustment would not make stock negative (rejects with HTTP 409 `StockInvariantViolation` otherwise; a malformed body is HTTP 400 `ValidationFailed`).
  iii. Inventory Service executes `StockItemAggregate.adjust(delta, reason, adjustedBy)`, emitting `StockAdjusted`. The first adjustment for a previously untracked `productId` starts that product's event stream (see `3-domain-models-inventory-service-ddd.md` §2.3.1).

### User Story I4: Receive Low-Stock Alert
- **As an** Admin, **I want to** be alerted when stock falls below a configured threshold.
- **Scenario:**
  i. Each stock deduction is evaluated by a Spring Cloud Stream processing job (real-time stream analysis).
  ii. If the resulting quantity falls below the configured threshold, a low-stock alert is generated.
  iii. No further alert is raised until stock is replenished above the threshold and later drops below it again.

### User Story I5: Release Stock on Order Cancelled
- **As the** Inventory Service (System), **I want to** restore stock when an order is cancelled or a payment fails.
- **Scenario:**
  i. Order Service publishes an `OrderCancelledEvent` (or Payment Service publishes a `PaymentFailedEvent`) to Kafka.
  ii. Inventory Service consumes the event.
  iii. Inventory Service restores the previously deducted quantity back to available stock.
