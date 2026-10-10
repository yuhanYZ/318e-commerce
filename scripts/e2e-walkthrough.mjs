#!/usr/bin/env node
/**
 * End-to-end walkthrough test, mirroring the Postman "318 E-Commerce - End-to-End
 * Walkthrough" collection — run with plain `node`, no Postman environment needed.
 *
 * Usage:
 *   node scripts/e2e-walkthrough.mjs
 *
 * Requires all five services running locally on their default ports
 * (8081 user, 8082 product, 8083 order, 8084 inventory, 8085 payment).
 * Override admin creds with ADMIN_EMAIL / ADMIN_PASSWORD env vars if you've
 * changed app.admin.* from the defaults in user-service/application.yml.
 */

const USER = "http://localhost:8081/api/v1";
const PRODUCT = "http://localhost:8082/api/v1";
const ORDER = "http://localhost:8083/api/v1";
const INVENTORY = "http://localhost:8084/api/v1";
const PAYMENT = "http://localhost:8085/api/v1";

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || "admin@318ecommerce.local";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "ChangeMe123!";

const results = [];
let passCount = 0;
let failCount = 0;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function call(method, url, { token, body } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const start = Date.now();
  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const ms = Date.now() - start;
  let json = null;
  const text = await res.text();
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = text;
    }
  }
  return { status: res.status, json, ms };
}

async function step(name, fn) {
  try {
    const detail = await fn();
    passCount++;
    results.push({ name, ok: true, detail });
    console.log(`PASS  ${name}${detail ? "  — " + detail : ""}`);
  } catch (err) {
    failCount++;
    results.push({ name, ok: false, detail: err.message });
    console.log(`FAIL  ${name}  — ${err.message}`);
  }
}

async function waitFor(fn, { timeoutMs = 4000, intervalMs = 300 } = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastErr;
  while (Date.now() < deadline) {
    try {
      return await fn();
    } catch (err) {
      lastErr = err;
      await new Promise((r) => setTimeout(r, intervalMs));
    }
  }
  throw lastErr;
}

const state = {};

async function main() {
  console.log(`Admin login: ${ADMIN_EMAIL}\n`);

  // 1. Login as Admin
  await step("1. Login as Admin", async () => {
    const { status, json } = await call("POST", `${USER}/users/login`, {
      body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    assert(json?.token, "no token in response");
    state.adminToken = json.token;
    return `token acquired for ${json.userId}`;
  });

  // 2. Create a Product (P4, admin)
  await step("2. Create a Product (P4, admin)", async () => {
    const { status, json } = await call("POST", `${PRODUCT}/products`, {
      token: state.adminToken,
      body: {
        name: "E2E Test Widget",
        description: "Created by scripts/e2e-walkthrough.mjs",
        // NOT x.99 — payment-service's mock gateway deterministically fails any
        // amount whose fractional part equals app.payment.mock-failure-fraction (99).
        price: { amount: 20.0, currency: "AUD" },
        imageUrl: "https://example.com/widget.jpg",
        categoryId: "CAT-TEST",
      },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    assert(json?.id, "no product id in response");
    state.productId = json.id;
    return `productId=${json.id}`;
  });

  // 3. Give it Stock (I3, admin)
  await step("3. Give it Stock (I3, admin)", async () => {
    const { status, json } = await call("POST", `${INVENTORY}/inventory/${state.productId}/adjust`, {
      token: state.adminToken,
      body: { delta: 50, reason: "Initial stock" },
    });
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    assert(json?.quantityAvailable === 50, `expected quantityAvailable 50, got ${json?.quantityAvailable}`);
    return `quantityAvailable=${json.quantityAvailable}`;
  });

  // 4. Check Stock (I1, no auth)
  await step("4. Check Stock (I1, no auth)", async () => {
    const { status, json } = await call("GET", `${INVENTORY}/inventory/${state.productId}`);
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    assert(json?.quantityAvailable === 50, `expected 50, got ${json?.quantityAvailable}`);
    return `quantityAvailable=${json.quantityAvailable}`;
  });

  // 5. Register a Customer (U1)
  const customerEmail = `e2e.customer.${Date.now()}@example.com`;
  await step("5. Register a Customer (U1)", async () => {
    const { status, json } = await call("POST", `${USER}/users/register`, {
      body: { email: customerEmail, password: "Passw0rd!" },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    state.customerId = json.id;
    return `userId=${json.id} email=${customerEmail}`;
  });

  // 6. Login as Customer (U2)
  await step("6. Login as Customer (U2)", async () => {
    const { status, json } = await call("POST", `${USER}/users/login`, {
      body: { email: customerEmail, password: "Passw0rd!" },
    });
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    assert(json?.token, "no token in response");
    state.customerToken = json.token;
    return "token acquired";
  });

  // 7. Browse Products (P1, no auth)
  await step("7. Browse Products (P1, no auth)", async () => {
    const { status, json } = await call("GET", `${PRODUCT}/products?page=0&size=20`);
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    assert(Array.isArray(json?.content), "expected content array");
    const found = json.content.some((p) => p.id === state.productId);
    assert(found, `product ${state.productId} not found in catalog page`);
    return `content length=${json.content.length}`;
  });

  // 8. Place an Order (O1)
  await step("8. Place an Order (O1)", async () => {
    const { status, json } = await call("POST", `${ORDER}/orders`, {
      token: state.customerToken,
      body: { items: [{ productId: state.productId, quantity: 2 }] },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    assert(json?.status === "PENDING", `expected PENDING, got ${json?.status}`);
    state.orderId = json.id;
    state.orderAmount = json.totalAmount;
    return `orderId=${json.id} total=${json.totalAmount?.amount} ${json.totalAmount?.currency}`;
  });

  // 9. Check Stock After Order (I1, expect reduced)
  await step("9. Check Stock After Order (I1, expect reduced)", async () => {
    const json = await waitFor(async () => {
      const { status, json } = await call("GET", `${INVENTORY}/inventory/${state.productId}`);
      assert(status === 200, `expected 200, got ${status}`);
      assert(json.quantityAvailable === 48, `expected 48, got ${json.quantityAvailable} (order event may still be processing)`);
      return json;
    });
    return `quantityAvailable=${json.quantityAvailable}`;
  });

  // 10. Pay for the Order (PAY1)
  await step("10. Pay for the Order (PAY1)", async () => {
    const { status, json } = await call("POST", `${PAYMENT}/payments`, {
      token: state.customerToken,
      body: { orderId: state.orderId, amount: state.orderAmount },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    state.paymentId = json.id;
    return `paymentId=${json.id} status=${json.status}`;
  });

  // 11. Check Order After Payment (O2, expect PAID)
  await step("11. Check Order After Payment (O2, expect PAID)", async () => {
    const json = await waitFor(async () => {
      const { status, json } = await call("GET", `${ORDER}/orders/${state.orderId}`, {
        token: state.customerToken,
      });
      assert(status === 200, `expected 200, got ${status}`);
      assert(json.status === "PAID", `expected PAID, got ${json.status} (PaymentConfirmedEvent may still be processing)`);
      return json;
    });
    return `status=${json.status}`;
  });

  // 12. Check Payment by Order (PAY2)
  await step("12. Check Payment by Order (PAY2)", async () => {
    const { status, json } = await call("GET", `${PAYMENT}/payments/order/${state.orderId}`, {
      token: state.customerToken,
    });
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    assert(json?.orderId === state.orderId, "orderId mismatch");
    return `paymentStatus=${json.status}`;
  });

  // 13. Admin: List All Transactions (PAY4)
  await step("13. Admin: List All Transactions (PAY4)", async () => {
    const { status, json } = await call("GET", `${PAYMENT}/payments?page=0&size=50`, {
      token: state.adminToken,
    });
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(json)})`);
    const found = json.content?.some((p) => p.id === state.paymentId);
    assert(found, `payment ${state.paymentId} not found in transaction list`);
    return `content length=${json.content.length}`;
  });

  // 14. Place a Second Order (for cancel/refund)
  await step("14. Place a Second Order (for cancel/refund)", async () => {
    const { status, json } = await call("POST", `${ORDER}/orders`, {
      token: state.customerToken,
      body: { items: [{ productId: state.productId, quantity: 1 }] },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    state.order2Id = json.id;
    state.order2Amount = json.totalAmount;
    return `orderId=${json.id}`;
  });

  // 15. Pay for Second Order
  await step("15. Pay for Second Order", async () => {
    const { status, json } = await call("POST", `${PAYMENT}/payments`, {
      token: state.customerToken,
      body: { orderId: state.order2Id, amount: state.order2Amount },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    state.payment2Id = json.id;
    return `paymentId=${json.id} status=${json.status}`;
  });

  // 16. Cancel Second Order (O4) — extension beyond the visible Postman steps,
  // completing what step 14's name implies.
  await step("16. Cancel Second Order (O4)", async () => {
    const json = await waitFor(async () => {
      const { status, json } = await call("GET", `${ORDER}/orders/${state.order2Id}`, {
        token: state.customerToken,
      });
      assert(status === 200 && json.status === "PAID", `order not PAID yet, got ${json?.status}`);
      return json;
    });
    const { status, json: cancelJson } = await call("POST", `${ORDER}/orders/${state.order2Id}/cancel`, {
      token: state.customerToken,
    });
    assert(status === 200, `expected 200, got ${status} (${JSON.stringify(cancelJson)})`);
    assert(cancelJson?.status === "CANCELLED", `expected CANCELLED, got ${cancelJson?.status}`);
    return `status=${cancelJson.status}`;
  });

  // 17. Refund for Second Order (PAY3)
  await step("17. Refund for Second Order (PAY3)", async () => {
    const { status, json } = await call("POST", `${PAYMENT}/payments/${state.payment2Id}/refund`, {
      token: state.customerToken,
      body: { reason: "Customer requested cancellation" },
    });
    assert(status === 201, `expected 201, got ${status} (${JSON.stringify(json)})`);
    return `refundId=${json.id} status=${json.status}`;
  });

  console.log(`\n${passCount} passed, ${failCount} failed, ${results.length} total`);
  process.exit(failCount > 0 ? 1 : 0);
}

main().catch((err) => {
  console.error("Fatal error running walkthrough:", err);
  process.exit(1);
});
