> **Archived — Iteration 1 (superseded by [`specs/iter2/`](../iter2/)).** Kept for historical reference only; do not use as the current design of record.

# Domain Models Specification

## 1. Domain Classes and Related User Stories

| Service | User Story | Domain Classes |
| :--- | :--- | :--- |
| **User Service** | **U1. Register an Account** | `User` |
| **User Service** | **U2. Login** | `User` |
| **User Service** | **U3. View / Update Profile** | `User` |
| **User Service** | **U4. Reset Password** | `User`, `PasswordResetToken` |
| **User Service** | **U5. Deactivate Account** | `User` |
| **Product Service** | **P1. Browse Product Catalog** | `Product`, `Category` |
| **Product Service** | **P2. View Product Details** | `Product` |
| **Product Service** | **P3. Search Products** | `Product` |
| **Product Service** | **P4. Create / Update / Deactivate a Product** | `Product`, `Category` |
| **Order Service** | **O1. Place an Order** | `Order`, `OrderItem` |
| **Order Service** | **O2. View Order Status** | `Order` |
| **Order Service** | **O3. View Order History** | `Order` |
| **Order Service** | **O4. Cancel an Order** | `Order` |
| **Order Service** | **O5. Update Order Status on Payment Event** | `Order` |
| **Inventory Service** | **I1. Check Stock Availability** | `StockItem` |
| **Inventory Service** | **I2. Deduct Stock on Order Created** | `StockItem` |
| **Inventory Service** | **I3. Adjust Stock (Restock)** | `StockItem`, `StockAdjustmentLog` |
| **Inventory Service** | **I4. Receive Low-Stock Alert** | `StockItem`, `LowStockAlert` |
| **Inventory Service** | **I5. Release Stock on Order Cancelled** | `StockItem` |
| **Payment Service** | **PAY1. Pay for an Order** | `Payment` |
| **Payment Service** | **PAY2. View Payment Status** | `Payment` |
| **Payment Service** | **PAY3. Request a Refund** | `Payment`, `Refund` |
| **Payment Service** | **PAY4. View Payment / Refund History** | `Payment`, `Refund` |
| **AI-Customer Service** | **AI1. Ask a Product / Order Question** | `ConversationSession`, `ConversationMessage` |
| **AI-Customer Service** | **AI2. Get Return / Exchange Help** | `ConversationSession`, `ConversationMessage` |
| **AI-Customer Service** | **AI3. Escalate to Human Agent** | `ConversationSession` |
| **AI-Customer Service** | **AI4. Review AI Conversation Logs** | `ConversationSession`, `ConversationMessage` |
---

## 2. User Service Domain Model

The User Service manages customer accounts, authentication, and profile data.

### 2.1 `User`
Represents a registered account (Customer or Admin).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique user identifier | Primary Identifier |
| `email` | `String` | Login email | Required, Unique |
| `passwordHash` | `String` | Hashed password | Required, never stored in plaintext |
| `name` | `String` | Display name | Editable via U3 |
| `address` | `String` | Delivery address | Editable via U3 |
| `phone` | `String` | Contact number | Editable via U3 |
| `role` | `Role` (Enum) | `CUSTOMER`, `ADMIN` | Required |
| `status` | `AccountStatus` (Enum) | `ACTIVE`, `LOCKED`, `DEACTIVATED` | `LOCKED` after 5 failed logins in 10 min (U2); `DEACTIVATED` is a soft delete (U5) |
| `failedLoginAttempts` | `int` | Consecutive failed login count | Reset on successful login |
| `createdAt` | `LocalDateTime` | Account creation timestamp | Auto-generated timestamp |

---

### 2.2 `PasswordResetToken`
Represents a time-limited token issued for a password reset request (U4).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique token record identifier | Primary Identifier |
| `userId` | `String` / `UUID` | Reference to the `User` | Required |
| `token` | `String` | Randomly generated reset token | Required, Unique |
| `expiresAt` | `LocalDateTime` | Expiry timestamp | Business calculation |
| `used` | `boolean` | Whether the token has been consumed | Default: `false`, prevents replay |

---

## 3. Product Service Domain Model

The Product Service manages the product catalog and category structure.

### 3.1 `Product`
Represents a sellable product in the catalog.

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique product identifier | Primary Identifier |
| `name` | `String` | Product name | Required, searchable (P3) |
| `description` | `String` | Product description | Searchable (P3) |
| `price` | `BigDecimal` | Unit price | Non-negative |
| `imageUrl` | `String` | Product image URL | Optional |
| `categoryId` | `String` / `UUID` | Reference to the `Category` | Required |
| `status` | `ProductStatus` (Enum) | `ACTIVE`, `INACTIVE` | `INACTIVE` when deactivated by Admin (P4) |
| `createdAt` | `LocalDateTime` | Creation timestamp | Auto-generated timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp | Auto-updated |

*Note: stock quantity is not stored on `Product` — it belongs to `StockItem` in the Inventory Service and is retrieved via inter-service REST call (P2).*

---

### 3.2 `Category`
Represents a product category used for catalog browsing (P1).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique category identifier | Primary Identifier |
| `name` | `String` | Category name | Required |
| `parentCategoryId` | `String` / `UUID` | Reference to a parent `Category` | Nullable — multi-level support TBD |

---

## 4. Order Service Domain Model

The Order Service manages order lifecycle and status transitions.

### 4.1 `Order`
Represents a customer's order and is the most state-heavy entity in the system.

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique order identifier | Primary Identifier |
| `userId` | `String` / `UUID` | Reference to the ordering `User` | Required, cross-service ID only |
| `status` | `OrderStatus` (Enum) | `PENDING`, `PAID`, `PAYMENT_FAILED`, `SHIPPED`, `CANCELLED` | Full state machine TBD |
| `totalAmount` | `BigDecimal` | Total order value | Non-negative |
| `createdAt` | `LocalDateTime` | Order creation timestamp | Auto-generated timestamp |
| `updatedAt` | `LocalDateTime` | Last status change timestamp | Auto-updated |

**Status transitions:**
- `PENDING → PAID` on consuming `PaymentConfirmedEvent` (O5)
- `PENDING → PAYMENT_FAILED` on consuming `PaymentFailedEvent` (O5)
- `PENDING`/`PAID` → `CANCELLED` while not yet `SHIPPED` (O4)

---

### 4.2 `OrderItem`
Represents a single line item within an `Order` (O1). Composition, not an independent aggregate.

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique order item identifier | Primary Identifier |
| `orderId` | `String` / `UUID` | Reference to the `Order` | Required |
| `productId` | `String` / `UUID` | Reference to the `Product` | Required, cross-service ID only |
| `quantity` | `int` | Quantity ordered | Positive |
| `unitPrice` | `BigDecimal` | Price at time of order | Snapshot value, unaffected by later price changes |

---

## 5. Inventory Service Domain Model

The Inventory Service manages stock levels and stock change history.

### 5.1 `StockItem`
Tracks the stock level of a specific product (I1, I2, I5).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique stock item identifier | Primary Identifier |
| `productId` | `String` / `UUID` | Reference to the `Product` | Required, Unique |
| `quantityAvailable` | `int` | Current available stock | `>= 0` |
| `lowStockThreshold` | `int` | Threshold that triggers a low-stock alert | Global vs per-product TBD |
| `updatedAt` | `LocalDateTime` | Last stock change timestamp | Auto-updated |

---

### 5.2 `StockAdjustmentLog`
Records a manual stock adjustment made by an Admin (I3).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique log entry identifier | Primary Identifier |
| `stockItemId` | `String` / `UUID` | Reference to the `StockItem` | Required |
| `adjustedBy` | `String` / `UUID` | User ID of the Admin performing the adjustment | Required |
| `delta` | `int` | Adjustment amount | Positive = restock, negative = deduction |
| `reason` | `String` | Optional adjustment note | Optional |
| `createdAt` | `LocalDateTime` | Adjustment timestamp | Auto-generated timestamp |

---

### 5.3 `LowStockAlert`
Represents a triggered low-stock alert (I4).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique alert identifier | Primary Identifier |
| `stockItemId` | `String` / `UUID` | Reference to the `StockItem` | Required |
| `triggeredAt` | `LocalDateTime` | Alert trigger timestamp | Auto-generated timestamp |
| `resolvedAt` | `LocalDateTime` | Timestamp when stock rose back above threshold | Null until resolved, prevents duplicate alerts |

---

## 6. Payment Service Domain Model

The Payment Service manages payment transactions and refunds.

### 6.1 `Payment`
Represents a payment transaction for an order (PAY1, PAY2, PAY4).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique payment identifier | Primary Identifier |
| `orderId` | `String` / `UUID` | Reference to the `Order` | Required, cross-service ID only |
| `amount` | `BigDecimal` | Payment amount | Non-negative |
| `status` | `PaymentStatus` (Enum) | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` | Required |
| `method` | `String` | Payment method | Mock vs real/sandbox gateway TBD |
| `createdAt` | `LocalDateTime` | Payment creation timestamp | Auto-generated timestamp |

---

### 6.2 `Refund`
Represents a refund request against a `Payment` (PAY3, PAY4).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique refund identifier | Primary Identifier |
| `paymentId` | `String` / `UUID` | Reference to the `Payment` | Required |
| `amount` | `BigDecimal` | Refund amount | Non-negative; partial refund support TBD |
| `status` | `RefundStatus` (Enum) | `REQUESTED`, `PROCESSED`, `REJECTED` | Default: `REQUESTED` |
| `requestedAt` | `LocalDateTime` | Refund request timestamp | Auto-generated timestamp |
| `processedAt` | `LocalDateTime` | Refund processed timestamp | Null until processed |

---

## 7. AI-Customer Service Domain Model

The AI-Customer Service manages conversational sessions with the AI agent.

### 7.1 `ConversationSession`
Represents a single customer-service conversation (AI1-AI4).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique session identifier | Primary Identifier |
| `userId` | `String` / `UUID` | Reference to the `User` | Required, cross-service ID only |
| `status` | `SessionStatus` (Enum) | `ACTIVE`, `ESCALATED`, `CLOSED` | `ESCALATED` set via AI3 |
| `createdAt` | `LocalDateTime` | Session start timestamp | Auto-generated timestamp |
| `updatedAt` | `LocalDateTime` | Last activity timestamp | Auto-updated |

---

### 7.2 `ConversationMessage`
Represents a single message within a `ConversationSession` (AI1, AI2, AI4).

| Field Name | Type | Description | Constraints / Notes |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique message identifier | Primary Identifier |
| `sessionId` | `String` / `UUID` | Reference to the `ConversationSession` | Required |
| `sender` | `Sender` (Enum) | `USER`, `AI`, `AGENT` | Required |
| `content` | `String` | Message text | Required |
| `referencedData` | `JSON` | Snapshot of cross-service data (Product/Order) the AI used to answer | Nullable, used for audit in AI4 |
| `createdAt` | `LocalDateTime` | Message timestamp | Auto-generated timestamp |

---

## 8. Cross-Service References (No Foreign Keys)

| From | To | Reference Field | Notes |
| :--- | :--- | :--- | :--- |
| Order Service | User Service | `Order.userId` | Order history persists after account deactivation (U5) |
| Order Service | Product Service | `OrderItem.productId` | `unitPrice` is snapshotted at order time |
| Inventory Service | Product Service | `StockItem.productId` | One-to-one, stored in a separate database |
| Payment Service | Order Service | `Payment.orderId` | Validated via synchronous REST call (PAY1, PAY3) |
| Inventory Service | Order / Payment Service | Event-driven, no direct field | Consumed via Kafka: `OrderCreatedEvent`, `OrderCancelledEvent`, `PaymentFailedEvent` |
| AI-Customer Service | User / Product / Order Service | `ConversationSession.userId`, `ConversationMessage.referencedData` | Queried via LangChain4j tool calls, not persisted as foreign entities |

Each service persists only the aggregates within its own bounded context. Cross-service data is accessed via ID reference plus REST query or Kafka event, never via cross-database foreign keys — consistent with the Database-per-Service principle.

---

## 9. Open Questions for Team Discussion

- Should `Category` support multi-level nesting (`parentCategoryId`)?
- Does `OrderStatus` need a `DELIVERED` state after `SHIPPED`? Full state machine TBD.
- Does `Refund` need to support partial refunds, or is full refund sufficient for the MVP?
- Should `lowStockThreshold` be configurable per product, or a single global default for the MVP?
- What is the exact JSON schema for `ConversationMessage.referencedData`?
