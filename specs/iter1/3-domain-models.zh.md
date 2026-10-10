> **已归档 — 迭代一（已被 [`specs/iter2/`](../iter2/) 取代）。** 仅作历史留存，不作为当前设计依据。

# 领域模型规格说明（Domain Models Specification）

## 1. 领域类与用户故事对照表（Domain Classes and Related User Stories）

| Service（服务） | User Story | Domain Classes（领域类） |
| :--- | :--- | :--- |
| **User Service** | **U1. 注册账号 (Register an Account)** | `User` |
| **User Service** | **U2. 登录 (Login)** | `User` |
| **User Service** | **U3. 查看/修改个人资料 (View / Update Profile)** | `User` |
| **User Service** | **U4. 重置密码 (Reset Password)** | `User`, `PasswordResetToken` |
| **User Service** | **U5. 注销账号 (Deactivate Account)** | `User` |
| **Product Service** | **P1. 浏览商品目录 (Browse Product Catalog)** | `Product`, `Category` |
| **Product Service** | **P2. 查看商品详情 (View Product Details)** | `Product` |
| **Product Service** | **P3. 搜索商品 (Search Products)** | `Product` |
| **Product Service** | **P4. 创建/更新/下架商品 (Create / Update / Deactivate a Product)** | `Product`, `Category` |
| **Order Service** | **O1. 下单 (Place an Order)** | `Order`, `OrderItem` |
| **Order Service** | **O2. 查看订单状态 (View Order Status)** | `Order` |
| **Order Service** | **O3. 查看订单历史 (View Order History)** | `Order` |
| **Order Service** | **O4. 取消订单 (Cancel an Order)** | `Order` |
| **Order Service** | **O5. 根据支付事件更新订单状态 (Update Order Status on Payment Event)** | `Order` |
| **Inventory Service** | **I1. 查询库存 (Check Stock Availability)** | `StockItem` |
| **Inventory Service** | **I2. 下单时扣减库存 (Deduct Stock on Order Created)** | `StockItem` |
| **Inventory Service** | **I3. 调整库存/补货 (Adjust Stock (Restock))** | `StockItem`, `StockAdjustmentLog` |
| **Inventory Service** | **I4. 接收低库存预警 (Receive Low-Stock Alert)** | `StockItem`, `LowStockAlert` |
| **Inventory Service** | **I5. 订单取消后释放库存 (Release Stock on Order Cancelled)** | `StockItem` |
| **Payment Service** | **PAY1. 支付订单 (Pay for an Order)** | `Payment` |
| **Payment Service** | **PAY2. 查看支付状态 (View Payment Status)** | `Payment` |
| **Payment Service** | **PAY3. 申请退款 (Request a Refund)** | `Payment`, `Refund` |
| **Payment Service** | **PAY4. 查看支付/退款历史 (View Payment / Refund History)** | `Payment`, `Refund` |
| **AI-Customer Service** | **AI1. 咨询商品/订单问题 (Ask a Product / Order Question)** | `ConversationSession`, `ConversationMessage` |
| **AI-Customer Service** | **AI2. 获取退换货帮助 (Get Return / Exchange Help)** | `ConversationSession`, `ConversationMessage` |
| **AI-Customer Service** | **AI3. 转接人工客服 (Escalate to Human Agent)** | `ConversationSession` |
| **AI-Customer Service** | **AI4. 审查AI对话记录 (Review AI Conversation Logs)** | `ConversationSession`, `ConversationMessage` |
---

## 2. User Service 领域模型

User Service 负责管理用户账号、身份认证和个人资料数据。

### 2.1 `User`
代表一个已注册账号（Customer 或 Admin）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 用户唯一标识 | 主键 (Primary Identifier) |
| `email` | `String` | 登录邮箱 | 必填，唯一 |
| `passwordHash` | `String` | 加密后的密码 | 必填，绝不存明文 |
| `name` | `String` | 显示名称 | U3 可修改 |
| `address` | `String` | 收货地址 | U3 可修改 |
| `phone` | `String` | 联系电话 | U3 可修改 |
| `role` | `Role` (Enum) | `CUSTOMER`、`ADMIN` | 必填 |
| `status` | `AccountStatus` (Enum) | `ACTIVE`、`LOCKED`、`DEACTIVATED` | 10分钟内连续5次登录失败则为 `LOCKED`（U2）；`DEACTIVATED` 为软删除（U5） |
| `failedLoginAttempts` | `int` | 连续登录失败次数 | 登录成功后重置 |
| `createdAt` | `LocalDateTime` | 账号创建时间 | 自动生成时间戳 |

---

### 2.2 `PasswordResetToken`
代表一次密码重置请求生成的限时令牌（U4）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 令牌记录唯一标识 | 主键 (Primary Identifier) |
| `userId` | `String` / `UUID` | 关联的 `User` | 必填 |
| `token` | `String` | 随机生成的重置令牌 | 必填，唯一 |
| `expiresAt` | `LocalDateTime` | 过期时间 | 业务计算得出 |
| `used` | `boolean` | 是否已被使用 | 默认 `false`，防止重放攻击 |

---

## 3. Product Service 领域模型

Product Service 负责管理商品目录和分类结构。

### 3.1 `Product`
代表目录中的一个可售商品。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 商品唯一标识 | 主键 (Primary Identifier) |
| `name` | `String` | 商品名称 | 必填，可被搜索（P3） |
| `description` | `String` | 商品描述 | 可被搜索（P3） |
| `price` | `BigDecimal` | 单价 | 非负 |
| `imageUrl` | `String` | 商品图片链接 | 可选 |
| `categoryId` | `String` / `UUID` | 关联的 `Category` | 必填 |
| `status` | `ProductStatus` (Enum) | `ACTIVE`、`INACTIVE` | Admin 下架商品时置为 `INACTIVE`（P4） |
| `createdAt` | `LocalDateTime` | 创建时间 | 自动生成时间戳 |
| `updatedAt` | `LocalDateTime` | 最后更新时间 | 自动更新 |

*说明：库存数量不存储在 `Product` 中——库存归属于 Inventory Service 的 `StockItem`，P2 查看详情时通过跨服务 REST 调用获取。*

---

### 3.2 `Category`
代表用于目录浏览的商品分类（P1）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 分类唯一标识 | 主键 (Primary Identifier) |
| `name` | `String` | 分类名称 | 必填 |
| `parentCategoryId` | `String` / `UUID` | 父级 `Category` 引用 | 可为空——是否支持多级分类待定 |

---

## 4. Order Service 领域模型

Order Service 负责管理订单生命周期与状态流转。

### 4.1 `Order`
代表一个客户订单，是全系统状态流转最复杂的实体。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 订单唯一标识 | 主键 (Primary Identifier) |
| `userId` | `String` / `UUID` | 下单用户的引用 | 必填，仅跨服务ID |
| `status` | `OrderStatus` (Enum) | `PENDING`、`PAID`、`PAYMENT_FAILED`、`SHIPPED`、`CANCELLED` | 完整状态机待补全 |
| `totalAmount` | `BigDecimal` | 订单总金额 | 非负 |
| `createdAt` | `LocalDateTime` | 订单创建时间 | 自动生成时间戳 |
| `updatedAt` | `LocalDateTime` | 最后状态变更时间 | 自动更新 |

**状态流转：**
- `PENDING → PAID`：消费 `PaymentConfirmedEvent`（O5）
- `PENDING → PAYMENT_FAILED`：消费 `PaymentFailedEvent`（O5）
- `PENDING`/`PAID` → `CANCELLED`：仅限尚未 `SHIPPED`（O4）

---

### 4.2 `OrderItem`
代表 `Order` 中的一个商品行项（O1）。属于组合关系，不是独立聚合根。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 订单行唯一标识 | 主键 (Primary Identifier) |
| `orderId` | `String` / `UUID` | 关联的 `Order` | 必填 |
| `productId` | `String` / `UUID` | 关联的 `Product` | 必填，仅跨服务ID |
| `quantity` | `int` | 下单数量 | 正整数 |
| `unitPrice` | `BigDecimal` | 下单时的单价 | 下单时的价格快照，不受后续改价影响 |

---

## 5. Inventory Service 领域模型

Inventory Service 负责管理库存水平与库存变更历史。

### 5.1 `StockItem`
追踪某个商品的库存量（I1、I2、I5）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 库存记录唯一标识 | 主键 (Primary Identifier) |
| `productId` | `String` / `UUID` | 关联的 `Product` | 必填，唯一 |
| `quantityAvailable` | `int` | 当前可用库存 | `>= 0` |
| `lowStockThreshold` | `int` | 触发低库存预警的阈值 | 全局配置还是按商品配置，待定 |
| `updatedAt` | `LocalDateTime` | 最后库存变更时间 | 自动更新 |

---

### 5.2 `StockAdjustmentLog`
记录 Admin 手动调整库存的操作（I3）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 日志记录唯一标识 | 主键 (Primary Identifier) |
| `stockItemId` | `String` / `UUID` | 关联的 `StockItem` | 必填 |
| `adjustedBy` | `String` / `UUID` | 执行调整的 Admin 用户ID | 必填 |
| `delta` | `int` | 调整数量 | 正数为补货，负数为扣减 |
| `reason` | `String` | 调整原因备注 | 可选 |
| `createdAt` | `LocalDateTime` | 调整发生时间 | 自动生成时间戳 |

---

### 5.3 `LowStockAlert`
代表一次触发的低库存预警（I4）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 预警记录唯一标识 | 主键 (Primary Identifier) |
| `stockItemId` | `String` / `UUID` | 关联的 `StockItem` | 必填 |
| `triggeredAt` | `LocalDateTime` | 预警触发时间 | 自动生成时间戳 |
| `resolvedAt` | `LocalDateTime` | 库存回升至阈值以上的时间 | 未解决前为空，避免重复触发预警 |

---

## 6. Payment Service 领域模型

Payment Service 负责管理支付交易与退款。

### 6.1 `Payment`
代表某个订单的一笔支付交易（PAY1、PAY2、PAY4）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 支付记录唯一标识 | 主键 (Primary Identifier) |
| `orderId` | `String` / `UUID` | 关联的 `Order` | 必填，仅跨服务ID |
| `amount` | `BigDecimal` | 支付金额 | 非负 |
| `status` | `PaymentStatus` (Enum) | `PENDING`、`SUCCESS`、`FAILED`、`REFUNDED` | 必填 |
| `method` | `String` | 支付方式 | 模拟网关还是真实/沙盒网关，待定 |
| `createdAt` | `LocalDateTime` | 支付创建时间 | 自动生成时间戳 |

---

### 6.2 `Refund`
代表针对某笔 `Payment` 发起的退款申请（PAY3、PAY4）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 退款记录唯一标识 | 主键 (Primary Identifier) |
| `paymentId` | `String` / `UUID` | 关联的 `Payment` | 必填 |
| `amount` | `BigDecimal` | 退款金额 | 非负；是否支持部分退款待定 |
| `status` | `RefundStatus` (Enum) | `REQUESTED`、`PROCESSED`、`REJECTED` | 默认 `REQUESTED` |
| `requestedAt` | `LocalDateTime` | 退款申请时间 | 自动生成时间戳 |
| `processedAt` | `LocalDateTime` | 退款处理完成时间 | 处理前为空 |

---

## 7. AI-Customer Service 领域模型

AI-Customer Service 负责管理与AI客服的对话会话。

### 7.1 `ConversationSession`
代表一次客服对话会话（AI1-AI4）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 会话唯一标识 | 主键 (Primary Identifier) |
| `userId` | `String` / `UUID` | 关联的 `User` | 必填，仅跨服务ID |
| `status` | `SessionStatus` (Enum) | `ACTIVE`、`ESCALATED`、`CLOSED` | AI3 转人工时置为 `ESCALATED` |
| `createdAt` | `LocalDateTime` | 会话开始时间 | 自动生成时间戳 |
| `updatedAt` | `LocalDateTime` | 最后活动时间 | 自动更新 |

---

### 7.2 `ConversationMessage`
代表 `ConversationSession` 中的一条消息（AI1、AI2、AI4）。

| 字段名 (Field Name) | 类型 (Type) | 说明 (Description) | 约束/备注 (Constraints / Notes) |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | 消息唯一标识 | 主键 (Primary Identifier) |
| `sessionId` | `String` / `UUID` | 关联的 `ConversationSession` | 必填 |
| `sender` | `Sender` (Enum) | `USER`、`AI`、`AGENT` | 必填 |
| `content` | `String` | 消息文本内容 | 必填 |
| `referencedData` | `JSON` | AI回答所依据的跨服务数据快照（Product/Order） | 可为空，供 AI4 审查追溯使用 |
| `createdAt` | `LocalDateTime` | 消息发送时间 | 自动生成时间戳 |

---

## 8. 跨服务引用总览（无外键，仅ID引用）

| 从 (From) | 到 (To) | 引用字段 (Reference Field) | 说明 (Notes) |
| :--- | :--- | :--- | :--- |
| Order Service | User Service | `Order.userId` | 账号注销后订单历史仍保留（U5） |
| Order Service | Product Service | `OrderItem.productId` | `unitPrice` 在下单时做快照 |
| Inventory Service | Product Service | `StockItem.productId` | 一对一，独立数据库存储 |
| Payment Service | Order Service | `Payment.orderId` | 通过同步 REST 调用校验（PAY1、PAY3） |
| Inventory Service | Order / Payment Service | 事件驱动，无直接字段引用 | 通过 Kafka 消费 `OrderCreatedEvent`、`OrderCancelledEvent`、`PaymentFailedEvent` |
| AI-Customer Service | User / Product / Order Service | `ConversationSession.userId`、`ConversationMessage.referencedData` | 通过 LangChain4j 工具调用查询，不作为外部实体持久化 |

每个服务只持久化自己 Bounded Context 内的聚合根。跨服务数据一律通过ID引用 + REST查询或Kafka事件获取，不使用跨库外键约束——符合 Database-per-Service 原则。

---

## 9. 待小组讨论的开放问题

- `Category` 是否需要支持多级分类（`parentCategoryId`）？
- `OrderStatus` 是否需要在 `SHIPPED` 之后增加 `DELIVERED` 状态？完整状态机待补全。
- `Refund` 是否需要支持部分退款，还是 MVP 阶段只做全额退款？
- `lowStockThreshold` 是每个商品单独配置，还是先用统一的全局默认值？
- `ConversationMessage.referencedData` 的具体 JSON schema 待细化。
