# User Stories — Payment Service

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review
**Source:** Split from [2-user-stories.md](./2-user-stories.md)

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| Payment Service | PAY1 | Pay for an Order | Customer |
| Payment Service | PAY2 | View Payment Status | Customer |
| Payment Service | PAY3 | Request a Refund | Customer |
| Payment Service | PAY4 | View Payment / Refund History | Admin |

---

## Payment Service

### User Story PAY1: Pay for an Order
- **As a** Customer, **I want to** pay for a placed order.
- **Scenario:**
  i. The user submits payment details for a `PENDING` order to Payment Service.
  ii. **Inter-Service Validation:** Payment Service sends a REST request to Order Service to confirm the order exists and is unpaid (rejects with HTTP 409 if already paid).
  iii. Payment Service processes the payment and publishes a `PaymentConfirmedEvent` (or `PaymentFailedEvent`) to Kafka.

### User Story PAY2: View Payment Status
- **As a** Customer, **I want to** check the payment status of my order.
- **Scenario:**
  i. The user requests payment status for an order from Payment Service.
  ii. Payment Service returns the current status (`Pending`, `Success`, `Failed`, `Refunded`).

### User Story PAY3: Request a Refund
- **As a** Customer, **I want to** request a refund for a paid order.
- **Scenario:**
  i. The user requests a refund for a successfully paid order.
  ii. **Inter-Service Validation:** Payment Service sends a REST request to Order Service to confirm the order is eligible (e.g., linked to a `Cancelled`/`Returned` order).
  iii. If eligible, Payment Service processes the refund, updates status to `REFUNDED`, and publishes a `RefundProcessedEvent`; otherwise it rejects with HTTP 409.

### User Story PAY4: View Payment / Refund History
- **As an** Admin, **I want to** view all payment and refund transactions.
- **Scenario:**
  i. The Admin requests a transaction report from Payment Service.
  ii. Payment Service returns all payment and refund records for reconciliation.
