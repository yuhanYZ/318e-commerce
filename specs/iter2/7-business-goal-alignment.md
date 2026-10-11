# Business Goal Alignment — Section 1 ↔ Section 2 Traceability

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10
**Purpose:** Section 2.1 of the report requires every functional requirement to explain how it echoes
a Section 1 business need or stakeholder expectation. This document is that traceability mapping —
it does not restate or replace the user story specs (`2-user-stories-*-v2.md`); it cross-references them.

---

## 1. Business Objectives (from Section 1.2)

Section 1.2 ("Business Case & Problem Statement") sets out three primary objectives. This document
tags every functional requirement (FR) with the objective(s) it primarily serves, using these IDs:

| ID | Objective (verbatim from Section 1.2) | One-line restatement |
| :--- | :--- | :--- |
| **BG1** | Scalability & Team Collaboration | Decoupled microservices, each independently built/tested/deployed against a shared API contract, structured to scale under high-concurrency scenarios (e.g. flash sales) |
| **BG2** | Real-Time Inventory Responsiveness | Event-driven (Kafka) + event-sourced inventory so stock adjusts automatically as orders are placed, paid, or cancelled, with no manual reconciliation |
| **BG3** | AI-Assisted Customer Support | An agentic AI component resolves common product-related queries autonomously, reducing human support volume while keeping a human in the loop for complex cases |

A story can serve more than one objective; the table below lists a **Primary** goal and, where relevant,
a **Secondary** one.

---

## 2. Traceability Table

| FR ID | FR Name | Service | Primary Goal | Secondary Goal | Justification |
| :--- | :--- | :--- | :--- | :--- | :--- |
| U1 | Register an Account | User | BG1 | — | Account creation is foundational to the "core shopping functionality" Section 1.1 describes, and is owned end-to-end by an independently built/tested User Service per BG1's decomposition. |
| U2 | Login | User | BG1 | — | JWT issuance/local validation across all six services with no callback to User Service is exactly the loosely-coupled, shared-contract pattern Section 1.4 credits for BG1's parallel-development benefit. |
| U3a | View Profile | User | BG1 | — | Standard read against data owned solely by User Service — a direct instance of each service owning its bounded context (Section 1.1), which is what makes independent development possible. |
| U3b | Update Profile | User | BG1 | — | Same as U3a; profile mutation stays entirely inside User Service's bounded context. |
| U4 | Reset Password | User | BG1 | — | Account-security workflow owned and testable independently of the other five services, reinforcing BG1's parallel-delivery claim. |
| U5 | Deactivate Account | User | BG1 | — | The story's own inter-service note (Order history stays intact after deactivation) demonstrates the database-per-service isolation BG1 relies on for independent evolution. |
| P1 | Browse Product Catalog | Product | BG1 | — | Core shopping functionality (Section 1.1) delivered by a catalog service developed/deployed independently of Order, Payment, etc. |
| P2 | View Product Details | Product | **BG2** | BG1 | Section 1.3 states Product Service "exposes live stock status by querying Inventory Service on read" — this is the customer-facing surface of BG2's real-time inventory responsiveness. |
| P3 | Search Products | Product | BG1 | — | Same architectural rationale as P1 — a self-contained read capability of the Product bounded context. |
| P4 | Create / Update / Deactivate a Product | Product | BG1 | — | Admin catalog management isolated inside Product Service; changes here never require redeploying another service, per BG1's independent-deployability goal. |
| O1 | Place an Order | Order | **BG2** | BG1 | Directly the mechanism Section 1.4 describes: "a synchronous stock check at the moment of order placement acts as the primary safeguard against overselling," paired with the `OrderCreatedEvent` that drives Inventory's automatic deduction. |
| O2 | View Order Status | Order | BG1 | — | Ownership-enforced read against Order Service's own store — bounded-context isolation, not an inventory/AI concern. |
| O3 | View Order History | Order | BG1 | — | Same rationale as O2. |
| O4 | Cancel an Order | Order | **BG2** | — | Publishing `OrderCancelledEvent` so Inventory "release[s] previously reserved stock" is the exact cancellation-side example Section 1.4 gives for BG2. |
| O5 | Update Order Status on Payment Event | Order | **BG2** | — | An idempotent Kafka consumer reacting to Payment events — Section 1.3's closing guarantee ("all consumers are implemented idempotently, so redelivered or out-of-order messages cannot corrupt state") exists specifically for flows like this one. |
| O6 | View Real-Time Order Volume & Revenue Analytics | Order | **BG1** | — | Section 1.2 names "high-concurrency traffic spikes, such as during flash sales" as the core scalability pain point; O6 gives admins the real-time visibility needed to actually detect and respond to such a spike, operationalizing BG1 rather than just architecturally enabling it. |
| I1 | Check Stock Availability | Inventory | **BG2** | — | The real-time stock lookup Section 1.3 lists verbatim as an Inventory Service capability. |
| I2 | Deduct Stock on Order Created | Inventory | **BG2** | — | The canonical BG2 example named in both Section 1.1 and 1.4: an appended, replayable event drives automatic stock deduction with no manual reconciliation. |
| I3 | Adjust Stock (Restock) | Inventory | **BG2** | — | Manual adjustments still flow through the same event-sourced write model ("every adjustment is an appended, replayable event," Section 1.3), keeping the audit trail real-time-consistent. |
| I4 | Receive Low-Stock Alert | Inventory | **BG2** | — | The low-stock alerting capability Section 1.3 attributes to Inventory Service, giving admins the same real-time responsiveness on the supply side that O6 gives on the demand side. |
| I5 | Release Stock on Order Cancelled | Inventory | **BG2** | — | The other half of Section 1.4's cancellation example: order cancellations *or payment failures* both release previously reserved stock automatically. |
| PAY1 | Pay for an Order | Payment | **BG2** | BG1 | Section 1.4: "a successful or failed payment publishes an event that Order Service consumes to update order status" — PAY1 is the producer of that event, making it a BG2 trigger point, not just a core-functionality story. |
| PAY2 | View Payment Status | Payment | BG1 | — | Self-contained read within Payment Service's own bounded context. |
| PAY3 | Request a Refund | Payment | BG1 | — | Completes the order-cancellation business process for the customer; stays inside Payment's own data/ownership boundary (refunds are customer-initiated, not automatically triggered — see the iteration's "Out of Scope" list). |
| PAY4 | View Payment / Refund History | Payment | BG1 | — | Administrative/reconciliation visibility enabled by Payment Service owning both the Payment and Refund tables independently. |
| AI1 | Ask a Product / Order Question | AI-Customer | **BG3** | — | The centerpiece BG3 example in Section 1.4: the agent is "grounded through a tool call into Product Service," answering from real catalogue data instead of the model's own knowledge. |
| AI2 | Get Return / Exchange Help | AI-Customer | **BG3** | — | Directly the "return/exchange Q&A" capability named in Section 1.1's system description of the AI-Customer Service. |
| AI3 | Escalate to Human Agent | AI-Customer | **BG3** | — | Implements Section 1.4's explicit design intent: "keeping a human in the loop for cases that genuinely need one." |
| AI4 | Review AI Conversation Logs | AI-Customer | **BG3** | — | Gives administrators the oversight needed to confirm BG3's claimed benefit (fewer routine queries reaching human staff) is actually happening safely, closing the loop on the business case's cost-efficiency argument. |

---

## 3. Observations

- **BG2 (Real-Time Inventory Responsiveness)** is the most concretely served objective — all 5 Inventory
  stories, plus O1/O4/O5 and PAY1 (the events that drive them), map to it directly and specifically,
  each traceable to a named sentence in Section 1.3/1.4.
- **BG3 (AI-Assisted Customer Support)** is fully covered by the four AI-Customer stories, satisfying
  the "≥2 Agentic AI user stories" requirement while also giving each one a specific textual anchor
  in Section 1.4's dedicated AI paragraph, rather than a generic "this is the AI service" label.
- **BG1 (Scalability & Team Collaboration)** is architectural rather than feature-level — it is best
  understood as the property that makes independent delivery of *every other* story possible, which is
  why most User/Product/Payment stories that don't touch inventory or AI are tagged BG1: the honest
  claim for those stories is "this was buildable/testable/deployable without blocking on another
  service," not an invented functional link to concurrency or flash sales.
- **O6** is the one FR whose BG1 link is deliberately non-architectural: it gives a concrete, operational
  answer to Section 1.2's named pain point (detecting a flash-sale-style spike in real time), which is
  why it is tagged BG1 rather than BG2 despite being built the same way (Spring Cloud Stream) as the
  BG2-tagged Inventory stories.
