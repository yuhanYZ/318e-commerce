> **Archived — Iteration 1 (superseded by [`specs/iter2/4-api-endpoints-v2.md`](../iter2/4-api-endpoints-v2.md)).** Kept for historical reference only; do not use as the current design of record.

# API Endpoints Summary

## 1. User Service (`http://localhost:8081/api/v1`)

| Feature | Method | Endpoint Path | Request Body | Response (Success) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| U1 | `POST` | `/users/register` | `RegisterRequest` | `201 Created` (`UserResponse`) | Register a new customer account |
| U2 | `POST` | `/users/login` | `LoginRequest` | `200 OK` (`AuthResponse`) | Authenticate and issue a JWT token |
| U3 | `GET` | `/users/me` | `None` | `200 OK` (`UserProfileResponse`) | Get the current user's profile |
| U3 | `PUT` | `/users/me` | `UpdateProfileRequest` | `200 OK` (`UserProfileResponse`) | Update name, address, or phone |
| U4 | `POST` | `/users/password-reset/request` | `PasswordResetRequest` | `202 Accepted` (`None`) | Request a password reset token by email |
| U4 | `POST` | `/users/password-reset/confirm` | `PasswordResetConfirmRequest` | `200 OK` (`None`) | Submit token and new password |
| U5 | `DELETE` | `/users/me` | `None` | `204 No Content` | Deactivate account (soft delete) |

---

## 2. Product Service (`http://localhost:8082/api/v1`)

| Feature | Method | Endpoint Path | Request Body | Response (Success) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| P1 | `GET` | `/products?categoryId={id}` | `None` | `200 OK` (`Page<ProductResponse>`) | Browse products by category, paginated |
| P2 | `GET` | `/products/{id}` | `None` | `200 OK` (`ProductDetailResponse`) | Get product details, incl. live stock status |
| P3 | `GET` | `/products/search?q={keyword}` | `None` | `200 OK` (`Array<ProductResponse>`) | Search products by name/description keyword |
| P4 | `POST` | `/products` | `ProductCreateRequest` | `201 Created` (`ProductResponse`) | Create a new product (Admin) |
| P4 | `PUT` | `/products/{id}` | `ProductUpdateRequest` | `200 OK` (`ProductResponse`) | Update a product (Admin) |
| P4 | `PATCH` | `/products/{id}/deactivate` | `None` | `200 OK` (`ProductResponse`) | Deactivate a product (Admin) |

---

## 3. Order Service (`http://localhost:8083/api/v1`)

| Feature | Method | Endpoint Path | Request Body | Response (Success) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| O1 | `POST` | `/orders` | `CreateOrderRequest` | `201 Created` (`OrderResponse`) | Place a new order; validates stock via Inventory Service |
| O2 | `GET` | `/orders/{id}` | `None` | `200 OK` (`OrderResponse`) | Get the current status of an order |
| O3 | `GET` | `/orders` | `None` | `200 OK` (`Page<OrderResponse>`) | List the current user's order history |
| O4 | `POST` | `/orders/{id}/cancel` | `None` | `200 OK` (`OrderResponse`) | Cancel an order before it ships |
| O5 | `N/A` (Kafka consumer) | consumes `PaymentConfirmedEvent` / `PaymentFailedEvent` | `N/A` | `N/A` | Auto-updates order status on payment events |

---

## 4. Inventory Service (`http://localhost:8084/api/v1`)

| Feature | Method | Endpoint Path | Request Body | Response (Success) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| I1 | `GET` | `/inventory/{productId}` | `None` | `200 OK` (`StockResponse`) | Check stock availability for a product |
| I2 | `N/A` (Kafka consumer) | consumes `OrderCreatedEvent` | `N/A` | `N/A` | Deducts stock when an order is created |
| I3 | `POST` | `/inventory/{productId}/adjust` | `StockAdjustmentRequest` | `200 OK` (`StockResponse`) | Manually adjust stock / restock (Admin) |
| I4 | `GET` | `/inventory/alerts` | `None` | `200 OK` (`Array<LowStockAlertResponse>`) | List active low-stock alerts (Admin) |
| I5 | `N/A` (Kafka consumer) | consumes `OrderCancelledEvent` / `PaymentFailedEvent` | `N/A` | `N/A` | Releases previously deducted stock |

---

## 5. Payment Service (`http://localhost:8085/api/v1`)

| Feature | Method | Endpoint Path | Request Body | Response (Success) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PAY1 | `POST` | `/payments` | `PaymentRequest` | `201 Created` (`PaymentResponse`) | Pay for a pending order; validates order via Order Service |
| PAY2 | `GET` | `/payments/order/{orderId}` | `None` | `200 OK` (`PaymentResponse`) | Get the payment status for an order |
| PAY3 | `POST` | `/payments/{id}/refund` | `RefundRequest` | `201 Created` (`RefundResponse`) | Request a refund for a paid order |
| PAY4 | `GET` | `/payments` | `None` | `200 OK` (`Page<PaymentResponse>`) | List all payment/refund transactions (Admin) |

---

## 6. AI-Customer Service (`http://localhost:8086/api/v1`)

| Feature | Method | Endpoint Path | Request Body | Response (Success) | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| AI1 | `POST` | `/ai-chat/sessions/{sessionId}/messages` | `ChatMessageRequest` | `200 OK` (`ChatMessageResponse`) | Ask a product/order question; agent tool-calls Product/Order Service |
| AI2 | `POST` | `/ai-chat/sessions/{sessionId}/messages` | `ChatMessageRequest` | `200 OK` (`ChatMessageResponse`) | Ask about return/exchange eligibility (same endpoint, routed by intent) |
| AI3 | `POST` | `/ai-chat/sessions/{sessionId}/escalate` | `None` | `200 OK` (`ConversationSessionResponse`) | Escalate the session to a human agent |
| AI4 | `GET` | `/ai-chat/sessions` | `None` | `200 OK` (`Page<ConversationSessionResponse>`) | List conversation sessions (Admin) |
| AI4 | `GET` | `/ai-chat/sessions/{sessionId}/messages` | `None` | `200 OK` (`Array<ChatMessageResponse>`) | Get full message history for a session (Admin) |

---

