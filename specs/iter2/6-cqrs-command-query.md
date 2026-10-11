# CQRS Command and Query Specification

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

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
- `order_analytics_window` (Order Service): one-minute tumbling buckets (`orderCount`, `totalRevenue`) derived from `OrderPlaced`, maintained by a Spring Cloud Stream one-minute tumbling-window aggregation over `order.created`. Used by the O6 query. See `3-domain-models-order-service-ddd.md` §2.6.
- `low_stock_alert` (Inventory Service): the open/resolve alert lifecycle, maintained by the I4 Kafka Streams topology over `stock.changed`. Used by the I4 query. See `3-domain-models-inventory-service-ddd.md` §2.3.2.
- All other queries (U3a, P1-P3, O2, O3, PAY2, PAY4, AI4) read directly from their own service's CRUD entity store — there is no separate projection for those CRUD aggregates.

---

## 5. User Story Classification

| Service | User Story ID | User Story | CQRS Type | Through Aggregate |
|:---|:---|:---|:---|:---|
| User Service | U1 | Register an Account | Command | Yes |
| User Service | U2 | Login | Command | Yes |
| User Service | U3a | View Profile | Query | No |
| User Service | U3b | Update Profile | Command | Yes |
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
| Order Service | O6 | View Real-Time Order Volume & Revenue Analytics | Query | No |
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
| `UpdateProfileCommand` | `UserAggregate` | U3b | `ProfileUpdated` | CRUD |
| `RequestPasswordResetCommand` | `PasswordResetTokenAggregate` | U4 | `PasswordResetRequested` | CRUD |
| `ConfirmPasswordResetCommand` | `PasswordResetTokenAggregate` + `UserAggregate` | U4 | `PasswordResetCompleted` | CRUD |
| `DeactivateAccountCommand` | `UserAggregate` | U5 | `AccountDeactivated` | CRUD |
| `CreateProductCommand` | `ProductAggregate` | P4 | `ProductCreated` | CRUD |
| `UpdateProductCommand` | `ProductAggregate` | P4 | `ProductUpdated` | CRUD |
| `DeactivateProductCommand` | `ProductAggregate` | P4 | `ProductDeactivated` | CRUD |
| `PlaceOrderCommand` | `OrderAggregate` | O1 | `OrderPlaced` | CRUD |
| `CancelOrderCommand` | `OrderAggregate` | O4 | `OrderCancelled` | CRUD |
| `UpdateOrderOnPaymentEventCommand` | `OrderAggregate` | O5 | `OrderPaid` or `OrderPaymentFailed` | CRUD |
| `AdjustStockCommand` | `StockItemAggregate` | I3 | `StockAdjusted` (the first call for a `productId` starts its stream), plus `LowStockAlertTriggered` / `LowStockAlertResolved` on `LowStockAlertAggregate` if the threshold is crossed / cleared | Event store (StockItem) + CRUD (LowStockAlert) |
| `DeductStockOnOrderCreatedCommand` | `StockItemAggregate` | I2 | `StockDeducted`, plus `LowStockAlertTriggered` on `LowStockAlertAggregate` if the threshold is crossed (rejected and logged if the `productId` has no stream or stock is insufficient) | Event store (StockItem) + CRUD (LowStockAlert) |
| `ReleaseStockOnOrderCancelledCommand` | `StockItemAggregate` | I5 | `StockReleased`, plus `LowStockAlertResolved` on `LowStockAlertAggregate` if the threshold is cleared (triggered by `OrderCancelledEvent` or `PaymentFailedEvent`) | Event store (StockItem) + CRUD (LowStockAlert) |
| `InitiatePaymentCommand` | `PaymentAggregate` | PAY1 | `PaymentInitiated`, then `PaymentSucceeded` or `PaymentFailed` (mock gateway; fails only when `amount` fraction is `.99`) | CRUD |
| `RequestRefundCommand` | `RefundAggregate` + `PaymentAggregate` | PAY3 | `RefundRequested`, then `RefundProcessed` (always, with the mock gateway); requires payment `SUCCESS` and order `CANCELLED` | CRUD |
| `StartSessionCommand` | `ConversationSessionAggregate` | AI1, AI2 | `SessionStarted` — issued implicitly by `PostConversationMessageCommand` when the `sessionId` does not yet exist | CRUD |
| `PostConversationMessageCommand` | `ConversationSessionAggregate` | AI1, AI2 | `MessageSent` (×2: the user's message and the AI reply) | CRUD |
| `EscalateSessionCommand` | `ConversationSessionAggregate` | AI3 | `SessionEscalated` | CRUD |

Command responses return the mutated resource's current representation, or outcome metadata (success/failure, IDs, version) for commands with no resource body. They never read from query-side projections.

---

## 7. Query Catalog

| Query | Story | Read Source | Consistency | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `GetProfileQuery(userId)` | U3a | User CRUD store | Strong (local DB) | |
| `BrowseProductCatalogQuery(categoryId)` | P1 | Product CRUD store | Strong (local DB) | |
| `GetProductDetailsQuery(productId)` | P2 | Product CRUD store + `inventory_read_model` | Strong (Product) / Eventual (bounded, stock portion) | Live stock is fetched via a synchronous call to Inventory Service's `CheckStockAvailabilityQuery` |
| `SearchProductsQuery(keyword)` | P3 | Product CRUD store | Strong (local DB) | |
| `ViewOrderStatusQuery(orderId)` | O2 | Order CRUD store | Strong (local DB) | |
| `ViewOrderHistoryQuery(userId)` | O3 | Order CRUD store | Strong (local DB) | |
| `GetRealtimeOrderAnalyticsQuery(windowMinutes)` | O6 | `order_analytics_window` | Eventual (bounded) | Projected from the `order.created` stream via a Spring Cloud Stream one-minute tumbling-window function (analogous to `5-event-sourcing.md` §3.5's inventory read model). Admin only. Default `windowMinutes` is `app.analytics.window-minutes` (15) |
| `CheckStockAvailabilityQuery(productId)` | I1 | `inventory_read_model` | Eventual (bounded) | Projected from the `StockItemAggregate` event stream; updated in the same transaction as the event append in this iteration (see `5-event-sourcing.md` §3.5). Returns 404 for an untracked `productId` |
| `ListLowStockAlertsQuery` | I4 | LowStockAlert CRUD store | Eventual (bounded) | `LowStockAlert` remains a CRUD aggregate, but its open/resolve lifecycle is now maintained by the I4 Kafka Streams topology over `stock.changed` (see §8), so a read can briefly lag the latest stock event |
| `ViewPaymentStatusQuery(orderId)` | PAY2 | Payment CRUD store | Strong (local DB) | |
| `ViewPaymentHistoryQuery` | PAY4 | Payment + Refund CRUD store | Strong (local DB) | |
| `ListConversationSessionsQuery` | AI4 | ConversationSession CRUD store | Strong (local DB) | |
| `GetSessionMessagesQuery(sessionId)` | AI4 | ConversationSession CRUD store | Strong (local DB) | |

---

## 8. Kafka Integration Event Catalog

Domain events are internal to a service. The subset below is translated 1:1 into Kafka integration events by the publishing service's infrastructure layer (Spring Cloud Stream Kafka binder, one topic per event type, JSON payload).

| Kafka Event | Topic | Publisher (Domain Event) | Consumer(s) → Command | Payload |
| :--- | :--- | :--- | :--- | :--- |
| `OrderCreatedEvent` | `order.created` | Order (`OrderPlaced`) | Inventory → `DeductStockOnOrderCreatedCommand`; Order → O6 analytics topology (Kafka Streams, one-minute tumbling window) | `orderId`, `userId`, `items[{productId, quantity}]`, `totalAmount`, `occurredAt` |
| `OrderCancelledEvent` | `order.cancelled` | Order (`OrderCancelled`) | Inventory → `ReleaseStockOnOrderCancelledCommand` | `orderId`, `items[{productId, quantity}]`, `occurredAt` |
| `PaymentConfirmedEvent` | `payment.confirmed` | Payment (`PaymentSucceeded`) | Order → `UpdateOrderOnPaymentEventCommand` | `paymentId`, `orderId`, `occurredAt` |
| `PaymentFailedEvent` | `payment.failed` | Payment (`PaymentFailed`) | Order → `UpdateOrderOnPaymentEventCommand`; Inventory → `ReleaseStockOnOrderCancelledCommand` | `paymentId`, `orderId`, `occurredAt` |
| `RefundProcessedEvent` | `payment.refunded` | Payment (`RefundProcessed`) | *(none in this iteration)* | `refundId`, `paymentId`, `orderId`, `occurredAt` |
| `StockChangedEvent` | `stock.changed` | Inventory (`StockAdjusted` / `StockDeducted` / `StockReleased`) | Inventory → I4 low-stock-alert topology (Kafka Streams) | `productId`, `eventType`, `quantity` or `delta`, `occurredAt` |

All consumers are idempotent: the command-dispatch consumers on the event's business key (`orderId` [+ `productId`]), and the two read-model topologies (O6, I4) by writing the complete aggregate value for a window/product rather than accumulating, so replaying an event recomputes the same row instead of double-counting. The Iteration 1 (archived) `StockUpdatedEvent` and `StockReservationFailedEvent` are **not** part of this catalog.

The O6 analytics projection and the I4 low-stock-alert projection are Kafka Streams topologies: they
consume `order.created` and `stock.changed` respectively and sink their result into a read-model
table, so no *integration* topic is added for their output. Note that `stock.changed` is an
Inventory-to-Inventory topic — Inventory Service republishes its own append-only stock events
(`5-event-sourcing.md` §3.3) and consumes them back as a stream, exactly as Order Service both
publishes and consumes `order.created`. Kafka Streams additionally manages its own internal
repartition/changelog topics and a local state directory; those are framework implementation
details, not entries in this catalog.
