package com.ecommerce.orderservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 (Contract-First Test Generation) — Order Service.
 *
 * Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /orders/*),
 * specs/iter2/4-api-endpoints-v2.md §8, specs/iter2/2-user-stories-order-service-v2.md,
 * specs/iter2/3-domain-models-order-service-ddd.md (OrderStatus: PENDING/PAID/PAYMENT_FAILED/CANCELLED,
 * no SHIPPED — see iter2 spec-decisions).
 *
 * O1 (POST /orders) calls out to Product Service (GET /products/{id}) and Inventory Service
 * (GET /inventory/{productId}). Both are stubbed with MockRestServiceServer.
 *
 * REQUIRED OF PHASE 4: expose two RestTemplate beans named exactly "productRestTemplate" and
 * "inventoryRestTemplate" for these calls, so this test class can bind to them.
 *
 * O5 (consumes PaymentConfirmedEvent / PaymentFailedEvent) is a Kafka event handler, not a REST
 * endpoint, and is out of scope here (belongs in an @EmbeddedKafka test class).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderServiceContractTests {

    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("productRestTemplate")
    private RestTemplate productRestTemplate;

    @Autowired
    @Qualifier("inventoryRestTemplate")
    private RestTemplate inventoryRestTemplate;

    private static String token(String userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    private static String orderBody(String productId, int quantity) {
        return """
                {"items":[{"productId":"%s","quantity":%d}]}
                """.formatted(productId, quantity);
    }

    private void stubProductActive(String productId, double price, int stock) {
        MockRestServiceServer.bindTo(productRestTemplate).ignoreExpectOrder(true).build()
                .expect(requestTo("http://localhost:8082/api/v1/products/" + productId))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"%s","name":"Test Product","price":{"amount":%s,"currency":"AUD"},
                         "categoryId":"CAT-10","status":"ACTIVE","stockAvailable":%d,"isInStock":true}
                        """.formatted(productId, price, stock), MediaType.APPLICATION_JSON));
    }

    private void stubInventory(String productId, int available) {
        MockRestServiceServer.bindTo(inventoryRestTemplate).ignoreExpectOrder(true).build()
                .expect(requestTo("http://localhost:8084/api/v1/inventory/" + productId))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"productId":"%s","quantityAvailable":%d,"isAvailable":true,"version":1}
                        """.formatted(productId, available), MediaType.APPLICATION_JSON));
    }

    private String placeOrder(String userToken, String productId, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    // ---------------------------------------------------------------
    // O1 — Place Order (POST /orders)
    // ---------------------------------------------------------------

    @Test
    void placeOrder_happyPath_returns201Pending() throws Exception {
        String productId = "PRD-" + UUID.randomUUID();
        stubProductActive(productId, 29.99, 100);
        stubInventory(productId, 100);
        String userToken = token("USR-BUYER-1", "CUSTOMER");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void placeOrder_emptyItems_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token("USR-BUYER-2", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void placeOrder_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("PRD-anything", 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void placeOrder_insufficientStock_returns409StockInvariantViolation() throws Exception {
        String productId = "PRD-" + UUID.randomUUID();
        stubProductActive(productId, 15.00, 1);
        stubInventory(productId, 1);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token("USR-BUYER-3", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("StockInvariantViolation"));
    }

    // ---------------------------------------------------------------
    // O2 — View Order Status (GET /orders/{id})
    // ---------------------------------------------------------------

    @Test
    void getOrderById_owner_returns200() throws Exception {
        String productId = "PRD-" + UUID.randomUUID();
        stubProductActive(productId, 10.00, 50);
        stubInventory(productId, 50);
        String ownerToken = token("USR-OWNER-1", "CUSTOMER");
        String orderId = placeOrder(ownerToken, productId, 1);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    @Test
    void getOrderById_notTheOwner_returns403() throws Exception {
        String productId = "PRD-" + UUID.randomUUID();
        stubProductActive(productId, 10.00, 50);
        stubInventory(productId, 50);
        String ownerToken = token("USR-OWNER-2", "CUSTOMER");
        String orderId = placeOrder(ownerToken, productId, 1);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + token("USR-OTHER-1", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "ORD-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer " + token("USR-BUYER-4", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderById_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "ORD-ANY"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // O3 — View Order History (GET /orders)
    // ---------------------------------------------------------------

    @Test
    void listMyOrders_happyPath_returns200PageResponse() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token("USR-BUYER-5", "CUSTOMER"))
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listMyOrders_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // O4 — Cancel Order (POST /orders/{id}/cancel)
    // ---------------------------------------------------------------

    @Test
    void cancelOrder_pendingOrder_returns200Cancelled() throws Exception {
        String productId = "PRD-" + UUID.randomUUID();
        stubProductActive(productId, 20.00, 30);
        stubInventory(productId, 30);
        String userToken = token("USR-BUYER-6", "CUSTOMER");
        String orderId = placeOrder(userToken, productId, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_alreadyCancelled_returns409OrderStateViolation() throws Exception {
        String productId = "PRD-" + UUID.randomUUID();
        stubProductActive(productId, 20.00, 30);
        stubInventory(productId, 30);
        String userToken = token("USR-BUYER-7", "CUSTOMER");
        String orderId = placeOrder(userToken, productId, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OrderStateViolation"));
    }

    @Test
    void cancelOrder_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", "ORD-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer " + token("USR-BUYER-8", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", "ORD-ANY"))
                .andExpect(status().isUnauthorized());
    }
}
