# API Endpoints Summary

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

## 1. User Service (`http://localhost:8081/api/v1`)

| Feature | CQRS Type | Method | Endpoint Path | Request Body | Response (Success) | Inter-Service REST Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **U1** | Command | `POST` | `/users/register` | `RegisterRequest` | `201 Created` (`UserResponse`) | None |
| **U2** | Command | `POST` | `/users/login` | `LoginRequest` | `200 OK` (`AuthResponse`) | None |
| **U3a** | Query | `GET` | `/users/me` | *None* | `200 OK` (`UserProfileResponse`) | None |
| **U3b** | Command | `PUT` | `/users/me` | `UpdateProfileRequest` | `200 OK` (`UserProfileResponse`) | None |
| **U4** | Command | `POST` | `/users/password-reset/request` | `PasswordResetRequest` | `202 Accepted` (*None*) | None |
| **U4** | Command | `POST` | `/users/password-reset/confirm` | `PasswordResetConfirmRequest` | `200 OK` (*None*) | None |
| **U5** | Command | `DELETE` | `/users/me` | *None* | `204 No Content` | None |

---

## 2. Product Service (`http://localhost:8082/api/v1`)

| Feature | CQRS Type | Method | Endpoint Path | Request Body | Response (Success) | Inter-Service REST Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **P1** | Query | `GET` | `/products?categoryId={id}&page={n}&size={m}` | *None* | `200 OK` (`ProductPageResponse`) | None |
| **P2** | Query | `GET` | `/products/{id}` | *None* | `200 OK` (`ProductDetailResponse`) | **Inventory Service**: `GET /inventory/{productId}` (live stock status) |
| **P3** | Query | `GET` | `/products/search?q={keyword}` | *None* | `200 OK` (`Array<ProductResponse>`) | None |
| **P4** | Command | `POST` | `/products` | `ProductCreateRequest` | `201 Created` (`ProductResponse`) | None |
| **P4** | Command | `PUT` | `/products/{id}` | `ProductUpdateRequest` | `200 OK` (`ProductResponse`) | None |
| **P4** | Command | `PATCH` | `/products/{id}/deactivate` | *None* | `200 OK` (`ProductResponse`) | None |

---

## 3. Order Service (`http://localhost:8083/api/v1`)

| Feature | CQRS Type | Method | Endpoint Path | Request Body | Response (Success) | Inter-Service REST Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **O1** | Command | `POST` | `/orders` | `CreateOrderRequest` | `201 Created` (`OrderResponse`) | **Product Service**: `GET /products/{id}` (existence, `ACTIVE`, price snapshot); **Inventory Service**: `GET /inventory/{productId}` (stock validation) |
| **O2** | Query | `GET` | `/orders/{id}` | *None* | `200 OK` (`OrderResponse`) | None |
| **O3** | Query | `GET` | `/orders?page={n}&size={m}` | *None* | `200 OK` (`OrderPageResponse`) | None |
| **O4** | Command | `POST` | `/orders/{id}/cancel` | *None* | `200 OK` (`OrderResponse`) | None |
| **O5** | Event Handler | *N/A (Kafka consumer)* | consumes `PaymentConfirmedEvent` / `PaymentFailedEvent` | *N/A* | *N/A* | None |
| **O6** | Query | `GET` | `/orders/analytics/realtime?windowMinutes={n}` | *None* | `200 OK` (`OrderAnalyticsResponse`) | None (Admin only) |

---

## 4. Inventory Service (`http://localhost:8084/api/v1`)

| Feature | CQRS Type | Method | Endpoint Path | Request Body | Response (Success) | Inter-Service REST Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **I1** | Query | `GET` | `/inventory/{productId}` | *None* | `200 OK` (`StockResponse`) | None |
| **I2** | Event Handler | *N/A (Kafka consumer)* | consumes `OrderCreatedEvent` | *N/A* | *N/A* | None |
| **I3** | Command | `POST` | `/inventory/{productId}/adjust` | `StockAdjustmentRequest` | `200 OK` (`StockResponse`) | None |
| **I4** | Query | `GET` | `/inventory/alerts` | *None* | `200 OK` (`Array<LowStockAlertResponse>`) | None |
| **I5** | Event Handler | *N/A (Kafka consumer)* | consumes `OrderCancelledEvent` / `PaymentFailedEvent` | *N/A* | *N/A* | None |

---

## 5. Payment Service (`http://localhost:8085/api/v1`)

| Feature | CQRS Type | Method | Endpoint Path | Request Body | Response (Success) | Inter-Service REST Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **PAY1** | Command | `POST` | `/payments` | `PaymentRequest` | `201 Created` (`PaymentResponse`) | **Order Service**: `GET /orders/{id}` (order is `PENDING`, `amount` = `totalAmount`; caller's bearer token forwarded) |
| **PAY2** | Query | `GET` | `/payments/order/{orderId}` | *None* | `200 OK` (`PaymentResponse`) | **Order Service**: `GET /orders/{id}` (the caller can read the payment's order; caller's bearer token forwarded) |
| **PAY3** | Command | `POST` | `/payments/{id}/refund` | `RefundRequest` | `201 Created` (`RefundResponse`) | **Order Service**: `GET /orders/{id}` (order is `CANCELLED`; caller's bearer token forwarded) |
| **PAY4** | Query | `GET` | `/payments?page={n}&size={m}` | *None* | `200 OK` (`PaymentPageResponse`) | None |

---

## 6. AI-Customer Service (`http://localhost:8086/api/v1`)

| Feature | CQRS Type | Method | Endpoint Path | Request Body | Response (Success) | Inter-Service REST Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **AI1** | Command | `POST` | `/ai-chat/sessions/{sessionId}/messages` | `ChatMessageRequest` | `200 OK` (`ChatMessageResponse`) | **Product Service**, **Order Service** — via LangChain4j tool calls, resolved dynamically per question; caller's bearer token forwarded. Creates the session on first use (client-generated `sessionId`) |
| **AI2** | Command | `POST` | `/ai-chat/sessions/{sessionId}/messages` | `ChatMessageRequest` | `200 OK` (`ChatMessageResponse`) | Same endpoint as AI1, routed by intent; same dependency pattern |
| **AI3** | Command | `POST` | `/ai-chat/sessions/{sessionId}/escalate` | *None* | `200 OK` (`ConversationSessionResponse`) | None |
| **AI4** | Query | `GET` | `/ai-chat/sessions?page={n}&size={m}` | *None* | `200 OK` (`ConversationSessionPageResponse`) | None |
| **AI4** | Query | `GET` | `/ai-chat/sessions/{sessionId}/messages` | *None* | `200 OK` (`Array<ChatMessageResponse>`) | None |

---

## 7. Endpoint Naming and Contract Rules

1. Command endpoints use verbs as trailing actions only when state transition intent is explicit (e.g., `/cancel`, `/adjust`, `/deactivate`, `/escalate`, `/refund`).
2. Query endpoints stay resource-oriented and side-effect free.
3. Command responses return the mutated resource's representation, or an empty body for state-only commands (e.g. U5 deactivate); query responses return read DTOs.
4. Kafka-consumed events (O5, I2, I5) are not exposed as REST endpoints — they are internal event handlers, consistent with the event-driven, Database-per-Service design in `specs/iter1/3-domain-models.md` §8.
5. Admin-only endpoints (P4, I3, I4, PAY4, AI4) are not distinguished by path — authorization is enforced via `role = ADMIN` on the authenticated user, not by URL shape.
6. Paginated queries (P1, O3, PAY4, AI4-list) accept optional `page` (default `0`) and `size` (default `20`, max `100`) and return `{ content, page, totalElements }`.

---

## 7a. Authentication and Inter-Service Calls

| Concern | Rule |
| :--- | :--- |
| Token format | JWT, HS256, shared secret `app.jwt.secret` configured identically in all six services; claims `sub` (userId), `role`, `exp` |
| Token validation | Each service validates the signature locally; no callback to User Service |
| Public endpoints (no token) | U1, U2, U4, P1, P2, P3, I1 |
| Server-to-server calls to public endpoints | Order → Product (`GET /products/{id}`), Order → Inventory (`GET /inventory/{productId}`), Product → Inventory (`GET /inventory/{productId}`): no token sent |
| Server-to-server calls to protected endpoints | Payment → Order (`GET /orders/{id}`), AI-Customer tool calls → Order: the caller's original `Authorization: Bearer` header is forwarded unchanged, so Order Service's ownership check (O2) applies to the end user |
| Kafka consumers | No authentication; events are trusted intra-system messages |

---

## 8. Error Response Contract (Summary)

| Error Code | HTTP Status | Applies To | Meaning |
| :--- | :--- | :--- | :--- |
| `ResourceNotFound` | `404` | Commands + Queries | Requested user/product/order/payment/session not found |
| `ValidationFailed` | `400` | Commands + Queries | Invalid request shape or parameters |
| `Unauthorized` | `401` | Commands + Queries requiring auth | Missing or invalid JWT |
| `Forbidden` | `403` | O2, PAY2, P4, I3, I4, PAY4, AI4 | Authenticated user lacks the `ADMIN` role for this action, or the requested resource belongs to another user |
| `AccountLocked` | `423` | U2 | Login blocked after 5 failed attempts within 10 minutes |
| `StockInvariantViolation` | `409` | O1, I2, I3 | Inventory rule violation (e.g., available stock would go below zero) |
| `OrderStateViolation` | `409` | O4, O5 | Order status transition denied (e.g., cancel an order that is already `CANCELLED` or `PAYMENT_FAILED`) |
| `PaymentStateViolation` | `409` | PAY1, PAY3 | Payment/refund transition denied (e.g., paying an order that is not `PENDING`; refund requested on a non-`SUCCESS` payment or on an order that is not `CANCELLED`) |
| `ProductStateViolation` | `409` | P4 | Update attempted on an `INACTIVE` product |
| `EmailAlreadyExists` | `409` | U1 | Registration email already registered |
| `InvalidCredentials` | `401` | U2 | Wrong email/password, or account `DEACTIVATED` |
| `ResetTokenInvalidOrExpired` | `400` | U4 | Password-reset token unknown, expired, or already used |
