# User Stories — Order Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10
**Source:** Split from [iter1/2-user-stories.md](../iter1/2-user-stories.md) — Iteration 1, archived for history.

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| Order Service | O1 | Place an Order | Customer |
| Order Service | O2 | View Order Status | Customer |
| Order Service | O3 | View Order History | Customer |
| Order Service | O4 | Cancel an Order | Customer |
| Order Service | O5 | Update Order Status on Payment Event | System |
| Order Service | O6 | View Real-Time Order Volume & Revenue Analytics | Admin |

---

## Order Service

### User Story O1: Place an Order
- **As a** Customer, **I want to** place an order for one or more products.
- **Scenario:**
  i. The user submits an order (product IDs and quantities) to Order Service.
  ii. **Inter-Service Validation:** For every item, Order Service sends a REST request to Product Service (`GET /products/{id}`) to confirm the product exists and is `ACTIVE` and to snapshot its current `price` as the item's `unitPrice`, and a REST request to Inventory Service (`GET /inventory/{productId}`) to confirm sufficient stock. A product that is missing, `INACTIVE`, or not tracked by Inventory (HTTP 404) is treated as unavailable.
  iii. If every item is available, Order Service creates the order with status `PENDING` (`totalAmount` = Σ `unitPrice × quantity`), emits `OrderPlaced`, and publishes an `OrderCreatedEvent` to Kafka; otherwise the order is rejected (HTTP 409 `StockInvariantViolation`).

### User Story O2: View Order Status
- **As a** Customer, **I want to** check the current status of my order.
- **Scenario:**
  i. The user requests an order by ID from Order Service.
  ii. Order Service verifies the order belongs to the requesting user (HTTP 403/404 otherwise).
  iii. Order Service returns the current status (`PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`).

### User Story O3: View Order History
- **As a** Customer, **I want to** view my past orders.
- **Scenario:**
  i. The user requests their order history from Order Service.
  ii. Order Service returns a paginated, most-recent-first list of the user's orders (empty list if none).

### User Story O4: Cancel an Order
- **As a** Customer, **I want to** cancel an order that has not been finalised.
- **Scenario:**
  i. The user requests cancellation of an order still in `PENDING` or `PAID` status (rejected with HTTP 409 `OrderStateViolation` if already `CANCELLED` or `PAYMENT_FAILED`).
  ii. Order Service executes `OrderAggregate.cancel()`, sets the status to `CANCELLED`, emits `OrderCancelled`, and publishes an `OrderCancelledEvent` to Kafka.
  iii. **Inter-Service Note:** Inventory Service consumes this event to release the previously deducted stock (I5). Refunds are **not** automatic — a `PAID` order that is cancelled becomes eligible for a customer-initiated refund via PAY3.

### User Story O5: Update Order Status on Payment Event
- **As the** Order Service (System), **I want to** update order status automatically when payment events are received.
- **Scenario:**
  i. Payment Service publishes a `PaymentConfirmedEvent` or `PaymentFailedEvent` to Kafka after processing a payment.
  ii. Order Service consumes the event.
  iii. On `PaymentConfirmedEvent`, `OrderAggregate.markPaid()` moves the status `PENDING` → `PAID` (emits `OrderPaid`); on `PaymentFailedEvent`, `markPaymentFailed()` moves it `PENDING` → `PAYMENT_FAILED` (emits `OrderPaymentFailed`). `PAYMENT_FAILED` is terminal — the order cannot be re-paid or cancelled. Stock release for a failed payment is handled by Inventory Service consuming the same `PaymentFailedEvent` directly (I5), not by Order Service.

### User Story O6: View Real-Time Order Volume & Revenue Analytics
- **As an** Admin, **I want to** see a rolling, real-time view of order volume and revenue over a recent sliding time window, so that I can monitor business performance and spot unusual spikes or drops as they happen.
- **Scenario (Real-Time Analysis — Stream Processing):**
  i. Every `OrderPlaced` event (the same domain event that triggers `OrderCreatedEvent` for Inventory Service) is also fed to a Spring Cloud Stream tumbling-window function inside Order Service that maintains a one-minute tumbling bucket (`orderCount`, `totalRevenue`) keyed by the event's occurrence minute.
  ii. The Admin requests the current analytics snapshot from Order Service, specifying a sliding window size in minutes (e.g., the last 15 minutes).
  iii. Order Service sums all buckets whose `windowStart` falls within `[now - windowMinutes, now]`, discarding older buckets, and returns the aggregated `orderCount`, `totalRevenue`, and derived `averageOrderValue` for that rolling window.
  iv. No historical/day-level reporting is provided in this iteration — only the current rolling window (default 15 minutes, configurable via `app.analytics.window-minutes`).
