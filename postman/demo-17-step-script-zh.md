# 318 E-Commerce：Postman 17 步演示脚本

## 演示前准备

1. 启动 Kafka，并确认 `318-kafka` 为 healthy：`docker compose up -d`。
2. 启动五个核心服务。`order-service`、`inventory-Service`、`payment-service` 必须启用 `kafka` profile。
3. 在 Postman 中导入 `postman/318-ecommerce-e2e.postman_collection.json`。
4. 选择 **No Environment** 即可。集合已内置 base URL，且会自动保存 token、productId、orderId 和 paymentId 等变量。
5. 在 Collection Runner 中按顺序运行整个 Collection。不要单独运行后半段请求，因为它们依赖前面自动保存的变量。

默认管理员账号：`admin@318ecommerce.local` / `ChangeMe123!`。

## 逐步讲解

| 步骤 | Postman 请求 | 演示时可说 | 预期结果 |
| --- | --- | --- | --- |
| 1 | `POST /api/v1/users/login`（管理员） | 管理员登录，系统返回 JWT；之后的管理动作都使用此 token。 | `200`，自动保存 `adminToken` |
| 2 | `POST /api/v1/products` | 管理员创建一件价格为 AUD 20 的商品，演示商品管理权限。 | `201`，自动保存 `productId` |
| 3 | `POST /api/v1/inventory/{productId}/adjust` | 管理员为新商品增加 50 件初始库存。 | `200`，`quantityAvailable = 50` |
| 4 | `GET /api/v1/inventory/{productId}` | 库存查询对访客开放；此时验证库存确实是 50。 | `200`，库存为 50 |
| 5 | `POST /api/v1/users/register` | 注册一个带时间戳的新顾客邮箱，避免重复运行时冲突。 | `201` |
| 6 | `POST /api/v1/users/login`（顾客） | 顾客登录，后续下单与支付使用顾客 token。 | `200`，自动保存 `customerToken` |
| 7 | `GET /api/v1/products?page=0&size=20` | 顾客浏览商品目录，验证刚创建的商品可以被检索。 | `200` |
| 8 | `POST /api/v1/orders` | 顾客购买 2 件商品，订单初始状态为 `PENDING`。 | `201`，自动保存 `orderId`、`orderAmount` |
| 9 | `GET /api/v1/inventory/{productId}` | 这是 Kafka 异步库存扣减的证据：库存从 50 变为 48。 | `200`，库存为 48 |
| 10 | `POST /api/v1/payments` | 顾客使用 mock payment 支付订单；支付服务发布成功事件。 | `201`，自动保存 `paymentId` |
| 11 | `GET /api/v1/orders/{orderId}` | 订单服务消费支付成功事件，将订单从 `PENDING` 更新为 `PAID`。 | `200`，`status = PAID` |
| 12 | `GET /api/v1/payments/order/{orderId}` | 按订单查询支付记录，证明订单与支付关联。 | `200` |
| 13 | `GET /api/v1/payments?page=0&size=50`（管理员） | 切回管理员权限，查看全量交易列表。 | `200`，列表含第 10 步支付 |
| 14 | `POST /api/v1/orders` | 再创建 1 件商品的第二张订单，用来演示取消与退款流程。 | `201`，自动保存 `order2Id`、`order2Amount` |
| 15 | `POST /api/v1/payments` | 支付第二张订单，自动保存第二笔支付 ID。 | `201`，自动保存 `payment2Id` |
| 16 | `POST /api/v1/orders/{order2Id}/cancel` | 顾客取消已支付的第二张订单，订单进入 `CANCELLED`。 | `200`，`status = CANCELLED` |
| 17 | `POST /api/v1/payments/{payment2Id}/refund` | 对第二笔支付发起退款，完整覆盖售后闭环。 | `201` |

## 运行与讲解建议

- 最直观的方式：打开 **Collection Runner**，选择整个 Collection，点击 **Run 318 E-Commerce...**；17 项应全部通过。
- 步骤 9 和 11 依赖 Kafka 异步事件。若立即查看仍是旧值，等待 1–3 秒后重新发送该请求；这正好可以说明系统是事件驱动架构。
- 演示时可同时打开 Kafka UI（`http://localhost:8090`），在第 8、10、16 步后展示相关 topic 的消息。
- 商品价格保持为 `20.00`，不要使用以 `.99` 结尾的金额；该项目的 mock payment 会故意把 `.99` 金额判定为支付失败。
- 每次服务重启都会清空 H2 内存数据；从第 1 步重新运行整个 Collection 即可恢复演示数据。

## 关键请求体

创建商品（第 2 步）：

```json
{
  "name": "E2E Test Widget",
  "description": "Created via Postman collection",
  "price": { "amount": 20.00, "currency": "AUD" },
  "imageUrl": "https://example.com/widget.jpg",
  "categoryId": "CAT-TEST"
}
```

下单（第 8 步）：

```json
{
  "items": [{ "productId": "{{productId}}", "quantity": 2 }]
}
```

支付（第 10 步）：

```json
{
  "orderId": "{{orderId}}",
  "amount": {{orderAmount}}
}
```
