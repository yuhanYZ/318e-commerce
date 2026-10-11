# Iteration 1 Specifications — Archived

**Status:** Archived. Superseded by [`../iter2/`](../iter2/).

These documents are Iteration 1 of the 318e-e-commerce specifications. They are kept so that the
evolution of the design into Iteration 2 stays traceable. They are **not** the current design of
record — do not use them as the source of truth for the implementation.

| Concern | Current source of truth |
| :--- | :--- |
| Architecture, user stories, domain models, CQRS, event sourcing, API list | [`../iter2/`](../iter2/) |
| API contract | [`../iter2/openapi-contract-v2.yaml`](../iter2/openapi-contract-v2.yaml) |

## Known superseded content — do not re-adopt

- **`StockUpdatedEvent` / `StockReservationFailedEvent`** (Inventory Service): dropped in Iteration 2. Inventory Service consumes `OrderCreatedEvent` / `OrderCancelledEvent` / `PaymentFailedEvent` and publishes no stock events of its own; a rejected deduction is logged, not compensated.
- **`OrderStatus.SHIPPED`** (and the open `DELIVERED` question): shipping is out of scope in Iteration 2. The status set is `PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`.
- **Per-product `lowStockThreshold`**: replaced by a single global configuration value (`app.inventory.low-stock-threshold`) read by `InventoryPolicyService`.
- **Real-time order volume / revenue analytics (O6)**: not present in this iteration. It is defined in Iteration 2 (Order Service, Admin-only).
