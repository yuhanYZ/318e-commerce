# User Stories

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review
**Related docs:** [1-technical-architecture.md](./1-technical-architecture.md)

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| User Service | U1 | Register an Account | Customer |
| User Service | U2 | Login | Customer |
| User Service | U3 | View / Update Profile | Customer |
| User Service | U4 | Reset Password | Customer |
| User Service | U5 | Deactivate Account | Customer |
| Product Service | P1 | Browse Product Catalog | Customer |
| Product Service | P2 | View Product Details | Customer |
| Product Service | P3 | Search Products | Customer |
| Product Service | P4 | Create / Update / Deactivate a Product | Admin |
| Order Service | O1 | Place an Order | Customer |
| Order Service | O2 | View Order Status | Customer |
| Order Service | O3 | View Order History | Customer |
| Order Service | O4 | Cancel an Order | Customer |
| Order Service | O5 | Update Order Status on Payment Event | System |
| Inventory Service | I1 | Check Stock Availability | Customer |
| Inventory Service | I2 | Deduct Stock on Order Created | System |
| Inventory Service | I3 | Adjust Stock (Restock) | Admin |
| Inventory Service | I4 | Receive Low-Stock Alert | Admin |
| Inventory Service | I5 | Release Stock on Order Cancelled | System |
| Payment Service | PAY1 | Pay for an Order | Customer |
| Payment Service | PAY2 | View Payment Status | Customer |
| Payment Service | PAY3 | Request a Refund | Customer |
| Payment Service | PAY4 | View Payment / Refund History | Admin |
| AI-Customer Service | AI1 | Ask a Product / Order Question | Customer |
| AI-Customer Service | AI2 | Get Return / Exchange Help | Customer |
| AI-Customer Service | AI3 | Escalate to Human Agent | Customer |
| AI-Customer Service | AI4 | Review AI Conversation Logs | Admin |

---

## User Service

### User Story U1: Register an Account
- **As a** Customer, **I want to** register an account using my email and password.
- **Scenario:**
  i. The user submits an email and password to User Service.
  ii. User Service validates the email format and password complexity, and checks the email is not already registered.
  iii. If valid, User Service creates the account and returns the new user ID; if the email is already taken, the request is rejected (HTTP 409).

### User Story U2: Login
- **As a** Customer, **I want to** log in with my email and password.
- **Scenario:**
  i. The user submits email and password to User Service.
  ii. User Service verifies the credentials against the stored account.
  iii. If valid, User Service issues a JWT auth token; after 5 consecutive failures within 10 minutes, the account is temporarily locked.

### User Story U3: View / Update Profile
- **As a** Customer, **I want to** view and update my profile (name, address, phone).
- **Scenario:**
  i. The authenticated user requests their profile from User Service.
  ii. User Service returns the current profile data.
  iii. To update, the user submits changed fields; User Service validates and persists them.

### User Story U4: Reset Password
- **As a** Customer, **I want to** reset my password when I forget it.
- **Scenario:**
  i. The user requests a password reset for their registered email.
  ii. User Service issues a time-limited reset token.
  iii. The user submits the token with a new password; User Service validates the token and updates the password (rejects if expired/invalid).

### User Story U5: Deactivate Account
- **As a** Customer, **I want to** deactivate my account without losing my order history.
- **Scenario:**
  i. The authenticated user requests account deactivation.
  ii. User Service marks the account inactive (soft delete) rather than removing it.
  iii. **Inter-Service Note:** Order Service order history referencing this user ID remains intact and queryable.

---

## Product Service

### User Story P1: Browse Product Catalog
- **As a** Customer, **I want to** browse products by category.
- **Scenario:**
  i. The user requests products for a given category from Product Service.
  ii. Product Service returns a paginated list of active products in that category (empty list if none).

### User Story P2: View Product Details
- **As a** Customer, **I want to** view a single product's details.
- **Scenario:**
  i. The user requests a product by ID from Product Service.
  ii. **Inter-Service Validation:** Product Service sends a REST request to Inventory Service to retrieve the current stock level for that product.
  iii. Product Service returns price, description, images, and the stock indicator; if the product ID does not exist, it returns HTTP 404.

### User Story P3: Search Products
- **As a** Customer, **I want to** search products by keyword.
- **Scenario:**
  i. The user submits a keyword to Product Service.
  ii. Product Service matches the keyword against product names/descriptions and returns ranked results (empty list if none, not an error).

### User Story P4: Create / Update / Deactivate a Product
- **As an** Admin, **I want to** create, update, and deactivate products.
- **Scenario:**
  i. The Admin submits product data (create, update, or deactivate) to Product Service.
  ii. Product Service verifies the caller has the Admin role (rejects with HTTP 403 otherwise).
  iii. Product Service persists the change; Customers immediately see the updated/removed product on their next browse or search.

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
  ii. Inventory Service consumes the event and deducts the ordered quantity from stock.
  iii. If sufficient, Inventory Service publishes a `StockUpdatedEvent`; if a race condition leaves stock insufficient, it publishes a `StockReservationFailedEvent` so Order Service can roll back the order.

### User Story I3: Adjust Stock (Restock)
- **As an** Admin, **I want to** manually adjust stock quantities.
- **Scenario:**
  i. The Admin submits a stock adjustment (positive or negative) for a product to Inventory Service.
  ii. Inventory Service validates the adjustment would not make stock negative (rejects with HTTP 400 otherwise).
  iii. Inventory Service updates the stock quantity and logs the change.

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

---

## AI-Customer Service

### User Story AI1: Ask a Product / Order Question
- **As a** Customer, **I want to** ask natural-language questions about products or my orders.
- **Scenario:**
  i. The user sends a question to AI-Customer Service.
  ii. **Inter-Service Validation:** AI-Customer Service (via LangChain4j tool calls) sends REST requests to Product Service and/or Order Service to retrieve relevant, grounded data.
  iii. The LLM (GPT-4o-mini) composes an answer from the retrieved data, or states it cannot answer confidently if no grounded data is found.

### User Story AI2: Get Return / Exchange Help
- **As a** Customer, **I want to** ask about return/exchange eligibility and process.
- **Scenario:**
  i. The user asks about returning or exchanging an order.
  ii. **Inter-Service Validation:** AI-Customer Service queries Order Service for the order's status and date to determine eligibility per policy.
  iii. If eligible, the agent explains the process and can flag/initiate the return; if not, it explains why.

### User Story AI3: Escalate to Human Agent
- **As a** Customer, **I want to** escalate to a human agent when the AI can't resolve my issue.
- **Scenario:**
  i. The AI agent is unable to resolve the query after reasonable attempts, or the user explicitly requests a human.
  ii. AI-Customer Service flags/routes the conversation for human follow-up.

### User Story AI4: Review AI Conversation Logs
- **As an** Admin, **I want to** review past AI customer service conversations.
- **Scenario:**
  i. The Admin requests conversation logs from AI-Customer Service.
  ii. AI-Customer Service returns stored conversation history for quality/audit review.

---

## Open Questions for Team Review

- Payment Service: are we simulating a payment gateway (mock) or integrating a real/sandbox provider?
- Order Service: what's the exact status state machine (all valid transitions)?
- AI-Customer Service: what data sources/tools does the agent have read access to (Product, Order, both)? Any write actions (e.g., initiating a return), or read-only for MVP?
- Admin role: is there a separate Admin auth flow, or is it a role flag on User Service accounts?
- Inventory low-stock threshold: fixed value or configurable per product?
