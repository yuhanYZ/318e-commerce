# 用户故事（User Stories）

**项目：** 318e-e-commerce（CSCI318 毕业设计）
**状态：** 草稿 — 待小组review
**相关文档：** [1-technical-architecture.md](./1-technical-architecture.md)

## 用户故事总览（User Story Overview）

| Service（服务） | User Story ID | User Story Name（故事名称） | Role(s)（角色） |
|---|---|---|---|
| User Service | U1 | 注册账号 (Register an Account) | Customer |
| User Service | U2 | 登录 (Login) | Customer |
| User Service | U3 | 查看/修改个人资料 (View / Update Profile) | Customer |
| User Service | U4 | 重置密码 (Reset Password) | Customer |
| User Service | U5 | 注销账号 (Deactivate Account) | Customer |
| Product Service | P1 | 浏览商品目录 (Browse Product Catalog) | Customer |
| Product Service | P2 | 查看商品详情 (View Product Details) | Customer |
| Product Service | P3 | 搜索商品 (Search Products) | Customer |
| Product Service | P4 | 创建/更新/下架商品 (Create / Update / Deactivate a Product) | Admin |
| Order Service | O1 | 下单 (Place an Order) | Customer |
| Order Service | O2 | 查看订单状态 (View Order Status) | Customer |
| Order Service | O3 | 查看订单历史 (View Order History) | Customer |
| Order Service | O4 | 取消订单 (Cancel an Order) | Customer |
| Order Service | O5 | 根据支付事件更新订单状态 (Update Order Status on Payment Event) | System |
| Inventory Service | I1 | 查询库存 (Check Stock Availability) | Customer |
| Inventory Service | I2 | 下单时扣减库存 (Deduct Stock on Order Created) | System |
| Inventory Service | I3 | 调整库存/补货 (Adjust Stock (Restock)) | Admin |
| Inventory Service | I4 | 接收低库存预警 (Receive Low-Stock Alert) | Admin |
| Inventory Service | I5 | 订单取消后释放库存 (Release Stock on Order Cancelled) | System |
| Payment Service | PAY1 | 支付订单 (Pay for an Order) | Customer |
| Payment Service | PAY2 | 查看支付状态 (View Payment Status) | Customer |
| Payment Service | PAY3 | 申请退款 (Request a Refund) | Customer |
| Payment Service | PAY4 | 查看支付/退款历史 (View Payment / Refund History) | Admin |
| AI-Customer Service | AI1 | 咨询商品/订单问题 (Ask a Product / Order Question) | Customer |
| AI-Customer Service | AI2 | 获取退换货帮助 (Get Return / Exchange Help) | Customer |
| AI-Customer Service | AI3 | 转接人工客服 (Escalate to Human Agent) | Customer |
| AI-Customer Service | AI4 | 审查AI对话记录 (Review AI Conversation Logs) | Admin |

---

## User Service（用户服务）

### User Story U1：注册账号
- **作为** Customer，**我想要** 用邮箱和密码注册账号。
- **Scenario（场景）：**
  i. 用户向 User Service 提交邮箱和密码。
  ii. User Service 校验邮箱格式和密码复杂度，并检查该邮箱是否已被注册。
  iii. 校验通过则创建账号并返回新用户ID；若邮箱已被占用，则拒绝请求（HTTP 409）。

### User Story U2：登录
- **作为** Customer，**我想要** 用邮箱和密码登录。
- **Scenario：**
  i. 用户向 User Service 提交邮箱和密码。
  ii. User Service 核对凭证与已存储账号是否一致。
  iii. 校验通过则颁发JWT认证令牌；若10分钟内连续5次失败，账号被临时锁定。

### User Story U3：查看/修改个人资料
- **作为** Customer，**我想要** 查看和修改我的个人资料（姓名、地址、电话）。
- **Scenario：**
  i. 已登录用户向 User Service 请求个人资料。
  ii. User Service 返回当前的资料数据。
  iii. 修改时用户提交变更字段，User Service 校验后持久化保存。

### User Story U4：重置密码
- **作为** Customer，**我想要** 在忘记密码时重置密码。
- **Scenario：**
  i. 用户对已注册邮箱发起重置密码请求。
  ii. User Service 生成一个限时有效的重置令牌。
  iii. 用户提交该令牌和新密码，User Service 校验令牌有效性后更新密码（令牌过期/无效则拒绝）。

### User Story U5：注销账号
- **作为** Customer，**我想要** 注销账号但不丢失我的订单历史。
- **Scenario：**
  i. 已登录用户发起账号注销请求。
  ii. User Service 将账号标记为不可用（软删除），而非直接删除。
  iii. **跨服务说明：** Order Service 中引用该用户ID的订单历史保持完整、仍可查询。

---

## Product Service（商品服务）

### User Story P1：浏览商品目录
- **作为** Customer，**我想要** 按分类浏览商品。
- **Scenario：**
  i. 用户向 Product Service 请求指定分类下的商品。
  ii. Product Service 返回该分类下所有上架商品的分页列表（无商品时返回空列表）。

### User Story P2：查看商品详情
- **作为** Customer，**我想要** 查看单个商品的详细信息。
- **Scenario：**
  i. 用户向 Product Service 按商品ID发起请求。
  ii. **Inter-Service Validation（跨服务校验）：** Product Service 向 Inventory Service 发送REST请求，获取该商品当前库存量。
  iii. Product Service 返回价格、描述、图片和库存状态；若商品ID不存在，返回 HTTP 404。

### User Story P3：搜索商品
- **作为** Customer，**我想要** 按关键词搜索商品。
- **Scenario：**
  i. 用户向 Product Service 提交搜索关键词。
  ii. Product Service 将关键词与商品名称/描述匹配，返回按相关度排序的结果（无匹配则返回空结果，而非报错）。

### User Story P4：创建/更新/下架商品
- **作为** Admin，**我想要** 创建、更新和下架商品。
- **Scenario：**
  i. Admin 向 Product Service 提交商品数据（创建/更新/下架）。
  ii. Product Service 校验调用者是否具备Admin角色（否则拒绝并返回 HTTP 403）。
  iii. Product Service 持久化该变更；Customer在下次浏览或搜索时立即看到更新后/已下架的商品。

---

## Order Service（订单服务）

### User Story O1：下单
- **作为** Customer，**我想要** 对一个或多个商品下单。
- **Scenario：**
  i. 用户向 Order Service 提交订单（商品ID及数量）。
  ii. **Inter-Service Validation（跨服务校验）：** Order Service 向 Inventory Service 发送REST请求，确认每个商品库存是否充足。
  iii. 库存充足则创建状态为 `PENDING` 的订单，并向Kafka发布 `OrderCreatedEvent`；库存不足则拒绝订单（HTTP 409）。

### User Story O2：查看订单状态
- **作为** Customer，**我想要** 查看订单的当前状态。
- **Scenario：**
  i. 用户按订单ID向 Order Service 发起请求。
  ii. Order Service 校验该订单是否属于当前请求用户（否则返回 HTTP 403/404）。
  iii. Order Service 返回当前状态（`Pending`、`Paid`、`Shipped`、`Cancelled` 等）。

### User Story O3：查看订单历史
- **作为** Customer，**我想要** 查看我过往的订单记录。
- **Scenario：**
  i. 用户向 Order Service 请求订单历史。
  ii. Order Service 返回按最新排序的分页订单列表（无订单则返回空列表）。

### User Story O4：取消订单
- **作为** Customer，**我想要** 在发货前取消订单。
- **Scenario：**
  i. 用户对状态为 `PENDING` 或 `PAID` 的订单发起取消请求（若已是 `SHIPPED`/`CANCELLED` 则拒绝，返回 HTTP 409）。
  ii. Order Service 将订单状态更新为 `CANCELLED`，并向Kafka发布 `OrderCancelledEvent`。
  iii. **跨服务说明：** Inventory Service 与 Payment Service 消费该事件，分别释放已锁定库存并处理可能的退款。

### User Story O5：根据支付事件更新订单状态
- **作为** Order Service（System），**我想要** 在收到支付事件时自动更新订单状态。
- **Scenario：**
  i. Payment Service 处理完支付后，向Kafka发布 `PaymentConfirmedEvent` 或 `PaymentFailedEvent`。
  ii. Order Service 消费该事件。
  iii. 收到 `PaymentConfirmedEvent` 时，状态由 `PENDING` 变为 `PAID`；收到 `PaymentFailedEvent` 时，状态变为 `PAYMENT_FAILED`，并触发库存释放。

---

## Inventory Service（库存服务）

### User Story I1：查询库存
- **作为** Customer，**我想要** 在下单前查看商品是否有货。
- **Scenario：**
  i. 用户（直接查询，或经由 Product Service）向 Inventory Service 查询商品库存量。
  ii. Inventory Service 返回当前库存数量，若为0则返回"缺货"状态。

### User Story I2：下单时扣减库存
- **作为** Inventory Service（System），**我想要** 在订单创建时自动扣减库存。
- **Scenario：**
  i. Order Service 在创建订单后向Kafka发布 `OrderCreatedEvent`。
  ii. Inventory Service 消费该事件，扣减对应商品的库存数量。
  iii. 库存充足则发布 `StockUpdatedEvent`；若因竞态条件导致库存不足，则发布 `StockReservationFailedEvent`，供 Order Service 据此回滚订单。

### User Story I3：调整库存/补货
- **作为** Admin，**我想要** 手动调整库存数量。
- **Scenario：**
  i. Admin 向 Inventory Service 提交某商品的库存调整（正数或负数）。
  ii. Inventory Service 校验该调整不会使库存变为负数（否则拒绝，返回 HTTP 400）。
  iii. Inventory Service 更新库存数量并记录本次变更。

### User Story I4：接收低库存预警
- **作为** Admin，**我想要** 在库存低于预设阈值时收到提醒。
- **Scenario：**
  i. 每次库存扣减都会经由 Spring Cloud Stream 流处理任务进行评估（实时流分析）。
  ii. 若扣减后的库存低于预设阈值，则生成低库存预警。
  iii. 在库存被补充到阈值以上、之后再次跌破阈值前，不会重复触发预警。

### User Story I5：订单取消后释放库存
- **作为** Inventory Service（System），**我想要** 在订单被取消或支付失败时恢复库存。
- **Scenario：**
  i. Order Service 发布 `OrderCancelledEvent`（或 Payment Service 发布 `PaymentFailedEvent`）到Kafka。
  ii. Inventory Service 消费该事件。
  iii. Inventory Service 将此前已扣减的数量重新计入可用库存。

---

## Payment Service（支付服务）

### User Story PAY1：支付订单
- **作为** Customer，**我想要** 为已下的订单付款。
- **Scenario：**
  i. 用户向 Payment Service 提交状态为 `PENDING` 订单的支付信息。
  ii. **Inter-Service Validation（跨服务校验）：** Payment Service 向 Order Service 发送REST请求，确认订单存在且尚未支付（若已支付则拒绝，返回 HTTP 409）。
  iii. Payment Service 处理支付，并向Kafka发布 `PaymentConfirmedEvent`（或 `PaymentFailedEvent`）。

### User Story PAY2：查看支付状态
- **作为** Customer，**我想要** 查看订单的支付状态。
- **Scenario：**
  i. 用户向 Payment Service 请求某订单的支付状态。
  ii. Payment Service 返回当前状态（`Pending`、`Success`、`Failed`、`Refunded`）。

### User Story PAY3：申请退款
- **作为** Customer，**我想要** 为已支付订单申请退款。
- **Scenario：**
  i. 用户对已成功支付的订单发起退款申请。
  ii. **Inter-Service Validation（跨服务校验）：** Payment Service 向 Order Service 发送REST请求，确认该订单符合退款条件（例如已关联到 `Cancelled`/`Returned` 状态的订单）。
  iii. 符合条件则处理退款、状态更新为 `REFUNDED`，并发布 `RefundProcessedEvent`；不符合条件则拒绝（HTTP 409）。

### User Story PAY4：查看支付/退款历史
- **作为** Admin，**我想要** 查看所有支付和退款交易记录。
- **Scenario：**
  i. Admin 向 Payment Service 请求交易报表。
  ii. Payment Service 返回所有支付和退款记录，用于财务对账。

---

## AI-Customer Service（AI客服服务）

### User Story AI1：咨询商品/订单问题
- **作为** Customer，**我想要** 用自然语言咨询商品或订单相关的问题。
- **Scenario：**
  i. 用户向 AI-Customer Service 发送问题。
  ii. **Inter-Service Validation（跨服务校验）：** AI-Customer Service（通过LangChain4j的工具调用）向 Product Service 和/或 Order Service 发送REST请求，获取相关的、有据可依的数据。
  iii. LLM（GPT-4o-mini）基于检索到的数据组织回答；若没有可靠数据支撑，则如实告知无法确信地回答。

### User Story AI2：获取退换货帮助
- **作为** Customer，**我想要** 咨询退换货的资格和流程。
- **Scenario：**
  i. 用户咨询某订单的退货/换货问题。
  ii. **Inter-Service Validation（跨服务校验）：** AI-Customer Service 查询 Order Service 获取该订单的状态和下单日期，据此判断是否符合退货政策。
  iii. 符合条件则说明流程并可发起/标记该退货；不符合条件则说明具体原因。

### User Story AI3：转接人工客服
- **作为** Customer，**我想要** 在AI无法解决问题时转接人工客服。
- **Scenario：**
  i. AI智能体经过合理尝试仍无法解决问题，或用户明确要求转人工。
  ii. AI-Customer Service 将该对话标记/转接给人工客服跟进。

### User Story AI4：审查AI对话记录
- **作为** Admin，**我想要** 查看AI客服的历史对话记录。
- **Scenario：**
  i. Admin 向 AI-Customer Service 请求对话记录。
  ii. AI-Customer Service 返回已存储的对话历史，用于质量审核。

---

## 待小组讨论的开放问题

- 支付服务：我们是模拟一个支付网关（mock），还是接入真实/沙盒支付服务商？
- 订单服务：订单状态机的完整定义是什么（所有合法的状态流转）？
- AI客服服务：智能体能读取哪些数据源/工具（Product、Order，还是两者都有）？是否有写操作权限（例如发起退货），还是MVP阶段只做只读？
- Admin角色：是否有独立的Admin认证流程，还是在User Service账号上加一个角色标记？
- 库存低库存阈值：是固定值，还是每个商品可单独配置？
