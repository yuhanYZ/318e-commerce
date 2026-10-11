# User Stories — Payment Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10
**Source:** Split from [iter1/2-user-stories.md](../iter1/2-user-stories.md) — Iteration 1, archived for history.

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
  i. The user submits payment details (`orderId`, `amount`) for a `PENDING` order to Payment Service. `amount` must equal the order's `totalAmount` (rejects with HTTP 400 `ValidationFailed` otherwise).
  ii. **Inter-Service Validation:** Payment Service sends a REST request to Order Service (`GET /orders/{id}`, forwarding the caller's bearer token) to confirm the order exists and its status is exactly `PENDING`. Any other status — including `PAYMENT_FAILED`, which cannot be retried — is rejected with HTTP 409 `PaymentStateViolation`.
  iii. Payment Service processes the payment through the mock gateway (`method = "MOCK"`; succeeds by default — see `3-domain-models-payment-service-ddd.md` §2.3.1 for the deterministic failure rule) and publishes a `PaymentConfirmedEvent` (on `PaymentSucceeded`) or `PaymentFailedEvent` (on `PaymentFailed`) to Kafka.

### User Story PAY2: View Payment Status
- **As a** Customer, **I want to** check the payment status of my order.
- **Scenario:**
  i. The user requests payment status for an order from Payment Service.
  ii. **Inter-Service Validation:** Payment Service does not store the buyer, so it sends a REST request to Order Service (`GET /orders/{id}`, forwarding the caller's bearer token) to confirm the caller can read the order their payment belongs to. A payment whose order is not the caller's is rejected with HTTP 403 `Forbidden` — never 404, so the endpoint does not confirm whether the payment exists.
  iii. Payment Service returns the current status (`Pending`, `Success`, `Failed`, `Refunded`).

### User Story PAY3: Request a Refund
- **As a** Customer, **I want to** request a refund for a paid order.
- **Scenario:**
  i. The user requests a refund for a payment whose status is `SUCCESS`. Refunds are always for the full `Payment.amount` (no partial refunds). This is the only refund path — cancelling an order does not refund automatically.
  ii. **Inter-Service Validation:** Payment Service sends a REST request to Order Service (`GET /orders/{id}`, forwarding the caller's bearer token) to confirm the linked order's status is `CANCELLED`.
  iii. If eligible, Payment Service processes the refund, marks the `Refund` as `PROCESSED` and the `Payment` as `REFUNDED`, and publishes a `RefundProcessedEvent`; otherwise (payment not `SUCCESS`, order not `CANCELLED`, or an active refund already exists) it rejects with HTTP 409 `PaymentStateViolation`.

### User Story PAY4: View Payment / Refund History
- **As an** Admin, **I want to** view all payment and refund transactions.
- **Scenario:**
  i. The Admin requests a transaction report from Payment Service.
  ii. Payment Service returns all payment and refund records for reconciliation.
