# Domain Models Specification - Product Service

Evolution from `specs/3-domain-models.md`:
- `Product` promoted to an aggregate root; `price` and `status` expressed as value objects.
- `Category` kept as a plain `Entity` (single-level only — `parentCategoryId` removed per earlier decision), since it has no invariants or lifecycle events of its own.
- Implicit create/update/deactivate operations on `Product` made explicit as aggregate behaviors with domain events.

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `Product` | Aggregate Root | Sellable catalog item; owns pricing, status, and category assignment |
| `Category` | Entity | Single-level product category used for catalog browsing |
| `ProductId` | Value Object | Typed, validated product identifier |
| `CategoryId` | Value Object | Typed, validated category identifier |
| `Money` | Value Object | Amount for product pricing |
| `ProductStatus` | Value Object | `ACTIVE`, `INACTIVE` |
| `ProductCreated` | Domain Event | Emitted by `ProductAggregate` when a new product is added to the catalog |
| `ProductUpdated` | Domain Event | Emitted by `ProductAggregate` on name/description/price/category change |
| `ProductDeactivated` | Domain Event | Emitted by `ProductAggregate` when an Admin deactivates the product |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **P1. Browse Product Catalog** | `Product`, `Category` | None |
| **P2. View Product Details** | `Product` | None |
| **P3. Search Products** | `Product` | None |
| **P4. Create / Update / Deactivate a Product** | `Product`, `Category` | `ProductCreated`, `ProductUpdated`, `ProductDeactivated` |

---

## 2. DDD Building Blocks

### 2.1 Entities

#### 2.1.1 `Category`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `CategoryId` | Unique category identifier | Required |
| `name` | `String` | Category name | Required |

*Decision: single-level categories only — no parent/child hierarchy.*

---

### 2.2 Value Objects

#### 2.2.1 `ProductId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `CategoryId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.3 `Money`

| Field | Type | Description |
| :--- | :--- | :--- |
| `amount` | `BigDecimal` | Monetary amount (non-negative) |
| `currency` | `String` | ISO 4217 currency code (e.g., `"AUD"`) |

#### 2.2.4 `ProductStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `ACTIVE`, `INACTIVE` |

---

### 2.3 Aggregates

#### 2.3.1 `ProductAggregate` (root: `Product`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `ProductId` | Unique product identifier | Primary identifier |
| `name` | `String` | Product name | Required, searchable (P3) |
| `description` | `String` | Product description | Searchable (P3) |
| `price` | `Money` | Unit price | Non-negative |
| `imageUrl` | `String` | Product image URL | Optional |
| `categoryId` | `CategoryId` | Reference to the `Category` | Required |
| `status` | `ProductStatus` | Current listing status | Default: `ACTIVE` |
| `createdAt` | `LocalDateTime` | Creation timestamp | Auto-generated |
| `updatedAt` | `LocalDateTime` | Last update timestamp | Auto-updated |

*Note: stock quantity is not stored here — it belongs to `StockItem` in the Inventory Service and is retrieved via inter-service REST call (P2).*

**Invariants**
1. `price.amount >= 0`.
2. `status = INACTIVE` is a terminal listing state — reactivation is out of scope; `update()` on an `INACTIVE` product is rejected (HTTP 409).
3. `categoryId` must reference an existing `Category` (HTTP 400 `ValidationFailed` otherwise).

**Inter-Service Note**
- `GET /products/{id}` (P2) and `GET /inventory/{productId}` are public endpoints called server-to-server by Order Service (O1) and AI-Customer Service tool calls; no bearer token is required for them. When Inventory Service has no stock record for a product (HTTP 404), P2 reports `stockAvailable = 0`, `isInStock = false`.

**Behaviors**
- `create(name, description, price, imageUrl, categoryId)` — emits `ProductCreated`
- `update(name, description, price, imageUrl, categoryId)` — all parameters optional (partial update); emits `ProductUpdated`
- `deactivate()` — emits `ProductDeactivated`

---

### 2.4 Domain Services

*(none — Product lifecycle rules are simple enough to live entirely on the aggregate)*

---

### 2.5 Domain Events

#### 2.5.1 `ProductCreated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `productId` | `ProductId` | The newly created product |
| `categoryId` | `CategoryId` | Assigned category |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `ProductUpdated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `productId` | `ProductId` | The updated product |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `ProductDeactivated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `productId` | `ProductId` | The deactivated product |
| `occurredAt` | `LocalDateTime` | Event timestamp |
