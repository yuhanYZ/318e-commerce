# User Stories — Order Service

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review
**Source:** Split from [2-user-stories.md](./2-user-stories.md)

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| Order Service | O1 | Place an Order | Customer |
| Order Service | O2 | View Order Status | Customer |
| Order Service | O3 | View Order History | Customer |
| Order Service | O4 | Cancel an Order | Customer |
| Order Service | O5 | Update Order Status on Payment Event | System |

---

## Order Service

### User Story O1: Place an Order
- **As a** Customer, **I want to** place an order for one or more products.
- **Scenario:**
  i. The user submits an order (product IDs and quantities) to Order Service.
  ii. **Inter-Service Validation:** Order Service sends a REST request to Inventory Service to confirm sufficient stock for every item.
  iii. If stock is sufficient, Order Service creates the order with status `PENDING` and publishes an `OrderCreatedEvent` to Kafka; if not, the order is rejected (HTTP 409).

### User Story O2: View Order Status
- **As a** Customer, **I want to** check the current status of my order.
- **Scenario:**
  i. The user requests an order by ID from Order Service.
  ii. Order Service verifies the order belongs to the requesting user (HTTP 403/404 otherwise).
  iii. Order Service returns the current status (`Pending`, `Paid`, `Shipped`, `Cancelled`, etc.).

### User Story O3: View Order History
- **As a** Customer, **I want to** view my past orders.
- **Scenario:**
  i. The user requests their order history from Order Service.
  ii. Order Service returns a paginated, most-recent-first list of the user's orders (empty list if none).

### User Story O4: Cancel an Order
- **As a** Customer, **I want to** cancel an order before it ships.
- **Scenario:**
  i. The user requests cancellation of an order still in `PENDING` or `PAID` status (rejected with HTTP 409 if already `SHIPPED`/`CANCELLED`).
  ii. Order Service updates the order status to `CANCELLED` and publishes an `OrderCancelledEvent` to Kafka.
  iii. **Inter-Service Note:** Inventory Service and Payment Service consume this event to release reserved stock and process any refund.

### User Story O5: Update Order Status on Payment Event
- **As the** Order Service (System), **I want to** update order status automatically when payment events are received.
- **Scenario:**
  i. Payment Service publishes a `PaymentConfirmedEvent` or `PaymentFailedEvent` to Kafka after processing a payment.
  ii. Order Service consumes the event.
  iii. On `PaymentConfirmedEvent`, status changes `PENDING` → `PAID`; on `PaymentFailedEvent`, status changes to `PAYMENT_FAILED` and stock release is triggered.
