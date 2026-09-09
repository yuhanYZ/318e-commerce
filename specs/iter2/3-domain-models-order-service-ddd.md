# Domain Models Specification - Order Service

Evolution from `specs/3-domain-models.md`:
- `Order` promoted to an aggregate root; `OrderItem` remains composed inside it (not an independent aggregate, unchanged from the original design).
- `status` transitions made explicit as aggregate behaviors, each emitting its own domain event instead of a single generic "status changed" update.
- `userId`/`productId` fields re-typed as value objects to make the cross-service, ID-only nature of these references explicit.

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `Order` | Aggregate Root | Customer's order; the most state-heavy entity in the system |
| `OrderItem` | Entity | Line item within an `Order`; composition, not an independent aggregate |
| `OrderId` | Value Object | Typed, validated order identifier |
| `UserId` | Value Object | Typed reference to a `User` (cross-service ID only) |
| `ProductId` | Value Object | Typed reference to a `Product` (cross-service ID only) |
| `Money` | Value Object | Amount used for `totalAmount` and `unitPrice` |
| `Quantity` | Value Object | Non-negative validated quantity |
| `OrderStatus` | Value Object | `PENDING`, `PAID`, `PAYMENT_FAILED`, `SHIPPED`, `CANCELLED` |
| `OrderPlaced` | Domain Event | Emitted by `OrderAggregate` on successful order placement (O1) |
| `OrderPaid` | Domain Event | Emitted by `OrderAggregate` on consuming `PaymentConfirmedEvent` (O5) |
| `OrderPaymentFailed` | Domain Event | Emitted by `OrderAggregate` on consuming `PaymentFailedEvent` (O5) |
| `OrderCancelled` | Domain Event | Emitted by `OrderAggregate` when a customer cancels an order (O4) |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **O1. Place an Order** | `Order`, `OrderItem` | `OrderPlaced` |
| **O2. View Order Status** | `Order` | None |
| **O3. View Order History** | `Order` | None |
| **O4. Cancel an Order** | `Order` | `OrderCancelled` |
| **O5. Update Order Status on Payment Event** | `Order` | `OrderPaid`, `OrderPaymentFailed` |

---

## 2. DDD Building Blocks

### 2.1 Entities

#### 2.1.1 `OrderItem`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique order item identifier | Primary identifier within the aggregate |
| `productId` | `ProductId` | Reference to the `Product` | Required, cross-service ID only |
| `quantity` | `Quantity` | Quantity ordered | Positive |
| `unitPrice` | `Money` | Price at time of order | Snapshot value, unaffected by later price changes |

---

### 2.2 Value Objects

#### 2.2.1 `OrderId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `UserId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the ordering user |

#### 2.2.3 `ProductId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the ordered product |

#### 2.2.4 `Money`

| Field | Type | Description |
| :--- | :--- | :--- |
| `amount` | `BigDecimal` | Monetary amount (non-negative) |
| `currency` | `String` | ISO 4217 currency code (e.g., `"AUD"`) |

#### 2.2.5 `Quantity`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `value` | `int` | Number of units | `value > 0` |

#### 2.2.6 `OrderStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `PENDING`, `PAID`, `PAYMENT_FAILED`, `SHIPPED`, `CANCELLED` |

---

### 2.3 Aggregates

#### 2.3.1 `OrderAggregate` (root: `Order`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `OrderId` | Unique order identifier | Primary identifier |
| `userId` | `UserId` | Reference to the ordering `User` | Required, cross-service ID only |
| `items` | `List<OrderItem>` | Line items | At least one item |
| `status` | `OrderStatus` | Current order status | Default: `PENDING` |
| `totalAmount` | `Money` | Total order value | Non-negative; equals the sum of `item.unitPrice × item.quantity` |
| `createdAt` | `LocalDateTime` | Order creation timestamp | Auto-generated |
| `updatedAt` | `LocalDateTime` | Last status change timestamp | Auto-updated |

**Invariants**
1. `totalAmount` equals the sum of all `OrderItem.unitPrice × OrderItem.quantity`.
2. Status transitions are restricted to: `PENDING → PAID`, `PENDING → PAYMENT_FAILED`, `{PENDING, PAID} → CANCELLED` (only while not yet `SHIPPED`).
3. `SHIPPED` and `CANCELLED` are terminal statuses.

**Behaviors**
- `place(userId, items)` — emits `OrderPlaced`
- `markPaid()` — emits `OrderPaid`
- `markPaymentFailed()` — emits `OrderPaymentFailed`
- `cancel()` — emits `OrderCancelled`

---

### 2.4 Domain Services

*(none — status transitions and total-amount calculation are simple enough to live entirely on the aggregate)*

---

### 2.5 Domain Events

#### 2.5.1 `OrderPlaced`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The newly placed order |
| `userId` | `UserId` | The ordering user |
| `totalAmount` | `Money` | Total order value |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `OrderPaid`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The paid order |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `OrderPaymentFailed`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The order whose payment failed |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.4 `OrderCancelled`

| Field | Type | Description |
| :--- | :--- | :--- |
| `orderId` | `OrderId` | The cancelled order |
| `occurredAt` | `LocalDateTime` | Event timestamp |
