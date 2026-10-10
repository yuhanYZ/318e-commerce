package com.ecommerce.paymentservice;

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
 * Phase 3 (Contract-First Test Generation) — Payment Service.
 *
 * Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /payments/*),
 * specs/iter2/4-api-endpoints-v2.md §8, specs/iter2/2-user-stories-payment-service-v2.md,
 * specs/iter2/3-domain-models-payment-service-ddd.md (mock gateway: fails iff the amount's
 * fractional part is exactly .99).
 *
 * PAY1 and PAY3 call out to Order Service (GET /orders/{id}), forwarding the caller's bearer
 * token. Stubbed with MockRestServiceServer.
 *
 * REQUIRED OF PHASE 4: expose a RestTemplate bean named exactly "orderRestTemplate" for that
 * call, so this test class can bind to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentServiceContractTests {

    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("orderRestTemplate")
    private RestTemplate orderRestTemplate;

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

    private void stubOrder(String orderId, String status, double totalAmount) {
        MockRestServiceServer.bindTo(orderRestTemplate).ignoreExpectOrder(true).build()
                .expect(requestTo("http://localhost:8083/api/v1/orders/" + orderId))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"%s","userId":"USR-BUYER-1","status":"%s",
                         "totalAmount":{"amount":%s,"currency":"AUD"},"items":[],
                         "createdAt":"2026-09-09T10:00:00Z"}
                        """.formatted(orderId, status, totalAmount), MediaType.APPLICATION_JSON));
    }

    private static String payBody(String orderId, double amount) {
        return """
                {"orderId":"%s","amount":{"amount":%s,"currency":"AUD"}}
                """.formatted(orderId, amount);
    }

    // ---------------------------------------------------------------
    // PAY1 — Pay for an Order (POST /payments)
    // ---------------------------------------------------------------

    @Test
    void payForOrder_happyPath_returns201Success() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 49.50);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-BUYER-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 49.50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.method").value("MOCK"));
    }

    @Test
    void payForOrder_mockGatewayDeterministicFailure_amountEndsIn99_returns201Failed() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 10.99);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-BUYER-2", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 10.99)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void payForOrder_amountMismatch_returns400ValidationFailed() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 100.00);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-BUYER-3", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 50.00)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void payForOrder_orderNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-BUYER-4", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("ORD-DOES-NOT-EXIST", 25.00)))
                .andExpect(status().isNotFound());
    }

    @Test
    void payForOrder_orderNotPending_returns409PaymentStateViolation() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PAID", 25.00);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-BUYER-5", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 25.00)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PaymentStateViolation"));
    }

    @Test
    void payForOrder_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("ORD-ANY", 10.00)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // PAY2 — View Payment Status (GET /payments/order/{orderId})
    // ---------------------------------------------------------------

    @Test
    void getPaymentByOrderId_happyPath_returns200() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 12.00);
        String userToken = token("USR-BUYER-6", "CUSTOMER");
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 12.00)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/payments/order/{orderId}", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    void getPaymentByOrderId_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/order/{orderId}", "ORD-NEVER-PAID")
                        .header("Authorization", "Bearer " + token("USR-BUYER-7", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentByOrderId_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/order/{orderId}", "ORD-ANY"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // PAY3 — Request a Refund (POST /payments/{id}/refund)
    // ---------------------------------------------------------------

    @Test
    void requestRefund_happyPath_returns201Processed() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 33.00);
        String userToken = token("USR-BUYER-8", "CUSTOMER");
        String paymentResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 33.00)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = com.jayway.jsonpath.JsonPath.read(paymentResponse, "$.id");

        // Order is now CANCELLED (e.g. the customer cancelled after paying).
        stubOrder(orderId, "CANCELLED", 33.00);

        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.amount.amount").value(33.00));
    }

    @Test
    void requestRefund_orderNotCancelled_returns409PaymentStateViolation() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 44.00);
        String userToken = token("USR-BUYER-9", "CUSTOMER");
        String paymentResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 44.00)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = com.jayway.jsonpath.JsonPath.read(paymentResponse, "$.id");

        // Order is still PAID, not CANCELLED.
        stubOrder(orderId, "PAID", 44.00);

        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PaymentStateViolation"));
    }

    @Test
    void requestRefund_paymentNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{id}/refund", "PAY-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer " + token("USR-BUYER-10", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestRefund_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{id}/refund", "PAY-ANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // PAY4 — List Payment / Refund History (GET /payments, Admin only)
    // ---------------------------------------------------------------

    @Test
    void listAllPayments_admin_returns200PageResponse() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-ADMIN-1", "ADMIN"))
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.refunds").isArray());
    }

    /**
     * Regression test for the PAY4 gap found in the specs audit: the user story and
     * 6-cqrs-command-query.md §7 both promise "all payment and refund records", but the response
     * used to omit Refund entirely. Creates a payment + refund, then asserts the refund is present
     * in PAY4's {@code refunds} array (not just {@code content}, which only ever held Payments).
     */
    @Test
    void listAllPayments_admin_includesRefundRecords() throws Exception {
        String orderId = "ORD-" + UUID.randomUUID();
        stubOrder(orderId, "PENDING", 77.00);
        String userToken = token("USR-BUYER-11", "CUSTOMER");
        String paymentResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderId, 77.00)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = com.jayway.jsonpath.JsonPath.read(paymentResponse, "$.id");

        stubOrder(orderId, "CANCELLED", 77.00);
        String refundResponse = mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String refundId = com.jayway.jsonpath.JsonPath.read(refundResponse, "$.id");

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-ADMIN-2", "ADMIN"))
                        .param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds[*].id").value(org.hamcrest.Matchers.hasItem(refundId)))
                .andExpect(jsonPath("$.refunds[*].paymentId").value(org.hamcrest.Matchers.hasItem(paymentId)))
                .andExpect(jsonPath("$.totalRefundElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void listAllPayments_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token("USR-CUST-1", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void listAllPayments_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized());
    }
}
