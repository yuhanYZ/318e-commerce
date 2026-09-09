# CQRS Command and Query Specification

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review

## 1. Purpose
This document defines how the 318 E-Commerce Platform applies CQRS, including:
1. Separation between command and query application services.
2. Write/read model responsibilities.
3. User story mapping (across all six services) to command, query, or event handler.
4. Command and query catalogs.
5. Handler wiring and read model update flow.
6. Consistency model and error model.

---

## 2. CQRS Principles
1. **Commands mutate state** and execute domain invariants through aggregates.
2. **Queries read state** from read models/projections and do not mutate domain state.
3. Write and read models may use different schemas optimized for their workloads.
4. Read side may be eventually consistent with the write side.

---

## 3. Application Service Split

### 3.1 Command Application Service
- Accepts command requests from API endpoints (or Kafka event handlers, for reactive commands).
- Routes to command handlers.
- Loads and executes aggregate behavior.
- Persists changes (event append for `StockItemAggregate`; CRUD write for all other aggregates).
- Publishes domain events for read model updates and cross-service integrations.

### 3.2 Query Application Service
- Accepts read requests from API endpoints.
- Routes to query handlers.
- Reads from projection/read stores only.
- Shapes response DTOs for API contracts.

### Constraint
- Queries must not call domain mutation methods.

---

## 4. Write and Read Models

### 4.1 Write Model (Aggregates)
- Event-sourced aggregate: `StockItemAggregate` (Inventory Service).
- CRUD aggregates: `UserAggregate`, `PasswordResetTokenAggregate` (User Service); `ProductAggregate` (Product Service); `OrderAggregate` (Order Service); `LowStockAlertAggregate` (Inventory Service); `PaymentAggregate`, `RefundAggregate` (Payment Service); `ConversationSessionAggregate` (AI-Customer Service).

### 4.2 Read Model (Projections)
- `inventory_read_model`: driven by `StockItemAggregate` events. Used by the I1 query.
- All other queries (U3-view, P1-P3, O2, O3, I4, PAY2, PAY4, AI4) read directly from their own service's CRUD entity store — there is no separate projection for CRUD aggregates.

---

## 5. User Story Classification

| Service | User Story ID | User Story | CQRS Type | Through Aggregate |
|:---|:---|:---|:---|:---|
| User Service | U1 | Register an Account | Command | Yes |
| User Service | U2 | Login | Command | Yes |
| User Service | U3 | View Profile | Query | No |
| User Service | U3 | Update Profile | Command | Yes |
| User Service | U4 | Reset Password | Command | Yes |
| User Service | U5 | Deactivate Account | Command | Yes |
| Product Service | P1 | Browse Product Catalog | Query | No |
| Product Service | P2 | View Product Details | Query | No |
| Product Service | P3 | Search Products | Query | No |
| Product Service | P4 | Create / Update / Deactivate a Product | Command | Yes |
| Order Service | O1 | Place an Order | Command | Yes |
| Order Service | O2 | View Order Status | Query | No |
| Order Service | O3 | View Order History | Query | No |
| Order Service | O4 | Cancel an Order | Command | Yes |
| Order Service | O5 | Update Order Status on Payment Event | Event Handler | Yes |
| Inventory Service | I1 | Check Stock Availability | Query | No |
| Inventory Service | I2 | Deduct Stock on Order Created | Event Handler | Yes |
| Inventory Service | I3 | Adjust Stock (Restock) | Command | Yes |
| Inventory Service | I4 | Receive Low-Stock Alert | Query | No |
| Inventory Service | I5 | Release Stock on Order Cancelled | Event Handler | Yes |
| Payment Service | PAY1 | Pay for an Order | Command | Yes |
| Payment Service | PAY2 | View Payment Status | Query | No |
| Payment Service | PAY3 | Request a Refund | Command | Yes |
| Payment Service | PAY4 | View Payment / Refund History | Query | No |
| AI-Customer Service | AI1 | Ask a Product / Order Question | Command | Yes |
| AI-Customer Service | AI2 | Get Return / Exchange Help | Command | Yes |
| AI-Customer Service | AI3 | Escalate to Human Agent | Command | Yes |
| AI-Customer Service | AI4 | Review AI Conversation Logs | Query | No |

---

## 6. Command Catalog

| Command | Target Aggregate(s) | Story | Output Events | Persistence |
| :--- | :--- | :--- | :--- | :--- |
| `RegisterUserCommand` | `UserAggregate` | U1 | `UserRegistered` | CRUD |
| `LoginCommand` | `UserAggregate` | U2 | `UserLoggedIn`, or `AccountLocked` (after 5 failures / 10 min) | CRUD |
| `UpdateProfileCommand` | `UserAggregate` | U3 | `ProfileUpdated` | CRUD |
| `RequestPasswordResetCommand` | `PasswordResetTokenAggregate` | U4 | `PasswordResetRequested` | CRUD |
| `ConfirmPasswordResetCommand` | `PasswordResetTokenAggregate` + `UserAggregate` | U4 | `PasswordResetCompleted` | CRUD |
| `DeactivateAccountCommand` | `UserAggregate` | U5 | `AccountDeactivated` | CRUD |
| `CreateProductCommand` | `ProductAggregate` | P4 | `ProductCreated` | CRUD |
| `UpdateProductCommand` | `ProductAggregate` | P4 | `ProductUpdated` | CRUD |
| `DeactivateProductCommand` | `ProductAggregate` | P4 | `ProductDeactivated` | CRUD |
| `PlaceOrderCommand` | `OrderAggregate` | O1 | `OrderPlaced` | CRUD |
| `CancelOrderCommand` | `OrderAggregate` | O4 | `OrderCancelled` | CRUD |
| `UpdateOrderOnPaymentEventCommand` | `OrderAggregate` | O5 | `OrderPaid` or `OrderPaymentFailed` | CRUD |
| `AdjustStockCommand` | `StockItemAggregate` | I3 | `StockItemInitialized` (first call) + `StockAdjusted`, plus `LowStockAlertResolved` on `LowStockAlertAggregate` if the threshold is cleared | Event store (StockItem) + CRUD (LowStockAlert) |
| `DeductStockOnOrderCreatedCommand` | `StockItemAggregate` | I2 | `StockItemInitialized` (first call, rare) + `StockDeducted`, plus `LowStockAlertTriggered` on `LowStockAlertAggregate` if the threshold is crossed | Event store (StockItem) + CRUD (LowStockAlert) |
| `ReleaseStockOnOrderCancelledCommand` | `StockItemAggregate` | I5 | `StockReleased`, plus `LowStockAlertResolved` on `LowStockAlertAggregate` if the threshold is cleared | Event store (StockItem) + CRUD (LowStockAlert) |
| `InitiatePaymentCommand` | `PaymentAggregate` | PAY1 | `PaymentInitiated`, then `PaymentSucceeded` or `PaymentFailed` | CRUD |
| `RequestRefundCommand` | `RefundAggregate` + `PaymentAggregate` | PAY3 | `RefundRequested`, then `RefundProcessed` or `RefundRejected` | CRUD |
| `PostConversationMessageCommand` | `ConversationSessionAggregate` | AI1, AI2 | `MessageSent` | CRUD |
| `EscalateSessionCommand` | `ConversationSessionAggregate` | AI3 | `SessionEscalated` | CRUD |

Command responses return outcome metadata (success/failure, IDs, version). They do not return read model projections.

---

## 7. Query Catalog

| Query | Story | Read Source | Consistency | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `GetProfileQuery(userId)` | U3 | User CRUD store | Strong (local DB) | |
| `BrowseProductCatalogQuery(categoryId)` | P1 | Product CRUD store | Strong (local DB) | |
| `GetProductDetailsQuery(productId)` | P2 | Product CRUD store + `inventory_read_model` | Strong (Product) / Eventual (bounded, stock portion) | Live stock is fetched via a synchronous call to Inventory Service's `CheckStockAvailabilityQuery` |
| `SearchProductsQuery(keyword)` | P3 | Product CRUD store | Strong (local DB) | |
| `ViewOrderStatusQuery(orderId)` | O2 | Order CRUD store | Strong (local DB) | |
| `ViewOrderHistoryQuery(userId)` | O3 | Order CRUD store | Strong (local DB) | |
| `CheckStockAvailabilityQuery(productId)` | I1 | `inventory_read_model` | Eventual (bounded) | Projected from the `StockItemAggregate` event stream |
| `ListLowStockAlertsQuery` | I4 | LowStockAlert CRUD store | Strong (local DB) | `LowStockAlertAggregate` is CRUD, not projected from events |
| `ViewPaymentStatusQuery(orderId)` | PAY2 | Payment CRUD store | Strong (local DB) | |
| `ViewPaymentHistoryQuery` | PAY4 | Payment + Refund CRUD store | Strong (local DB) | |
| `ListConversationSessionsQuery` | AI4 | ConversationSession CRUD store | Strong (local DB) | |
| `GetSessionMessagesQuery(sessionId)` | AI4 | ConversationSession CRUD store | Strong (local DB) | |
