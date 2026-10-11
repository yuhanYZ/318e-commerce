# Iteration 2 Specifications — Index

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

This directory holds the current design of record for the 318e-e-commerce microservice
application. Every document here is Final at Version 2.0. The earlier Iteration 1 material is
archived in [`../iter1/`](../iter1/) and is **not** the design of record.

## 1. What is in here

| # | Document | Purpose |
| :--- | :--- | :--- |
| 1 | [`1-technical-architecture-v2.md`](1-technical-architecture-v2.md) | Services, layered architecture, technology stack, security model, messaging model, event-sourcing scope, out-of-scope list |
| 2 | `2-user-stories-*-v2.md` (6 files) | User stories per service — user, product, order, inventory, payment, AI-customer |
| 3 | `3-domain-models-*-ddd.md` (6 files) | DDD domain models per service — entities, value objects, aggregates, domain services, domain events, read models |
| 4 | [`4-api-endpoints-v2.md`](4-api-endpoints-v2.md) | Endpoint summary per service, plus naming, authentication and error-response contract rules |
| 5 | [`openapi-contract-v2.yaml`](openapi-contract-v2.yaml) | Machine-readable OpenAPI 3.0 contract — 22 paths / 29 operations |
| 6 | [`5-event-sourcing.md`](5-event-sourcing.md) | Event sourcing for `StockItemAggregate` — event model, persistence, replay, idempotency, read model |
| 7 | [`6-cqrs-command-query.md`](6-cqrs-command-query.md) | CQRS split, write/read models, command and query catalogs, consistency and error models, Kafka integration event catalog (§8) |
| 8 | [`7-business-goal-alignment.md`](7-business-goal-alignment.md) | Traceability — every functional requirement mapped to the Section 1 business objective it serves |

## 2. Suggested reading order

1. [`1-technical-architecture-v2.md`](1-technical-architecture-v2.md) — the shape of the system.
2. `2-user-stories-*-v2.md` — what each service must do, per story.
3. `3-domain-models-*-ddd.md` — the domain model behind each service's stories.
4. [`4-api-endpoints-v2.md`](4-api-endpoints-v2.md) with [`openapi-contract-v2.yaml`](openapi-contract-v2.yaml) — how the stories are exposed over HTTP.
5. [`6-cqrs-command-query.md`](6-cqrs-command-query.md) §8 — how the services talk to each other over Kafka.
6. [`5-event-sourcing.md`](5-event-sourcing.md) — the one event-sourced aggregate, in depth.
7. [`7-business-goal-alignment.md`](7-business-goal-alignment.md) — why each requirement exists at all.

## 3. Functional requirements at a glance

29 requirements across 6 services. "BG" is the primary business objective served (see
[`7-business-goal-alignment.md`](7-business-goal-alignment.md)): **BG1** scalability and team
collaboration, **BG2** real-time inventory responsiveness, **BG3** AI-assisted customer support.

| ID | Requirement | Service | CQRS Type | BG |
| :--- | :--- | :--- | :--- | :--- |
| U1 | Register an Account | User | Command | BG1 |
| U2 | Login | User | Command | BG1 |
| U3a | View Profile | User | Query | BG1 |
| U3b | Update Profile | User | Command | BG1 |
| U4 | Reset Password | User | Command | BG1 |
| U5 | Deactivate Account | User | Command | BG1 |
| P1 | Browse Product Catalog | Product | Query | BG1 |
| P2 | View Product Details | Product | Query | BG2 |
| P3 | Search Products | Product | Query | BG1 |
| P4 | Create / Update / Deactivate a Product | Product | Command | BG1 |
| O1 | Place an Order | Order | Command | BG2 |
| O2 | View Order Status | Order | Query | BG1 |
| O3 | View Order History | Order | Query | BG1 |
| O4 | Cancel an Order | Order | Command | BG2 |
| O5 | Update Order Status on Payment Event | Order | Event Handler | BG2 |
| O6 | View Real-Time Order Volume & Revenue Analytics | Order | Query | BG1 |
| I1 | Check Stock Availability | Inventory | Query | BG2 |
| I2 | Deduct Stock on Order Created | Inventory | Event Handler | BG2 |
| I3 | Adjust Stock (Restock) | Inventory | Command | BG2 |
| I4 | Receive Low-Stock Alert | Inventory | Query | BG2 |
| I5 | Release Stock on Order Cancelled | Inventory | Event Handler | BG2 |
| PAY1 | Pay for an Order | Payment | Command | BG2 |
| PAY2 | View Payment Status | Payment | Query | BG1 |
| PAY3 | Request a Refund | Payment | Command | BG1 |
| PAY4 | View Payment / Refund History | Payment | Query | BG1 |
| AI1 | Ask a Product / Order Question | AI-Customer | Command | BG3 |
| AI2 | Get Return / Exchange Help | AI-Customer | Command | BG3 |
| AI3 | Escalate to Human Agent | AI-Customer | Command | BG3 |
| AI4 | Review AI Conversation Logs | AI-Customer | Query | BG3 |

**Real-time analysis on streaming data:** O6 (order volume and revenue over a sliding window) and
I4 (low-stock alerting).

**Agentic AI:** AI1–AI4.

## 4. Coverage at a glance

| Requirement | Minimum | This iteration |
| :--- | :--- | :--- |
| Functionally distinct bounded contexts, each a microservice | 4 | 6 |
| Functional requirements | 12 | 29 |
| User stories involving real-time analysis on streaming data | 2 | 2 (O6, I4) |
| User stories involving an agentic AI component | 2 | 4 (AI1–AI4) |
| Kafka integration event types | — | 5 (`order.created`, `order.cancelled`, `payment.confirmed`, `payment.failed`, `payment.refunded`) |
