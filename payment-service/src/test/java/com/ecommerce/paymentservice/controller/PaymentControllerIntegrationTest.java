package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.dto.external.OrderInfo;
import com.ecommerce.paymentservice.dto.external.OrderItemInfo;
import com.ecommerce.paymentservice.dto.request.PaymentRequest;
import com.ecommerce.paymentservice.dto.request.RefundRequest;
import com.ecommerce.paymentservice.messaging.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.service.OrderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-first integration tests generated from specs/openapi-contract.yaml
 * for the /payments/* endpoints (PAY1-PAY4). OrderClient and
 * PaymentEventPublisher are mocked because Order Service isn't actually
 * running during this test and no Kafka broker is provisioned in this repo
 * yet; MockPaymentGateway is left as a real bean since it's a deterministic,
 * side-effect-free simulation (see its javadoc) - tests drive its decline
 * branch by submitting the configured app.payment.mock-decline-trigger
 * value (see application.yml) as the payment method.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerIntegrationTest {

    private static final String DECLINE_TRIGGER = "MOCK_CARD_DECLINE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private PaymentEventPublisher paymentEventPublisher;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---------- PAY1: Pay for an Order ----------

    @Test
    void payForOrder_validPendingOrder_returns201Success() throws Exception {
        String userId = UUID.randomUUID().toString();
        Mockito.when(orderClient.getOrder("ORD-1")).thenReturn(Optional.of(
                new OrderInfo("ORD-1", userId, "PENDING", new BigDecimal("39.98"),
                        List.of(new OrderItemInfo("PRD-1", 2)))));

        PaymentRequest request = new PaymentRequest("ORD-1", new BigDecimal("39.98"), "VISA_TEST");
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.orderId").value("ORD-1"));
    }

    @Test
    void payForOrder_declineMethod_returns201Failed() throws Exception {
        String userId = UUID.randomUUID().toString();
        Mockito.when(orderClient.getOrder("ORD-2")).thenReturn(Optional.of(
                new OrderInfo("ORD-2", userId, "PENDING", new BigDecimal("20.00"), List.of())));

        PaymentRequest request = new PaymentRequest("ORD-2", new BigDecimal("20.00"), DECLINE_TRIGGER);
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void payForOrder_amountMismatch_returns201Failed() throws Exception {
        String userId = UUID.randomUUID().toString();
        Mockito.when(orderClient.getOrder("ORD-3")).thenReturn(Optional.of(
                new OrderInfo("ORD-3", userId, "PENDING", new BigDecimal("39.98"), List.of())));

        PaymentRequest request = new PaymentRequest("ORD-3", new BigDecimal("10.00"), "VISA_TEST");
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void payForOrder_orderNotFound_returns409() throws Exception {
        Mockito.when(orderClient.getOrder(anyString())).thenReturn(Optional.empty());

        PaymentRequest request = new PaymentRequest("does-not-exist", new BigDecimal("10.00"), "VISA_TEST");
        String token = generateToken(UUID.randomUUID().toString(), "CUSTOMER");

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void payForOrder_orderNotPending_returns409() throws Exception {
        String userId = UUID.randomUUID().toString();
        Mockito.when(orderClient.getOrder("ORD-4")).thenReturn(Optional.of(
                new OrderInfo("ORD-4", userId, "PAID", new BigDecimal("10.00"), List.of())));

        PaymentRequest request = new PaymentRequest("ORD-4", new BigDecimal("10.00"), "VISA_TEST");
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void payForOrder_notOrderOwner_returns403() throws Exception {
        Mockito.when(orderClient.getOrder("ORD-5")).thenReturn(Optional.of(
                new OrderInfo("ORD-5", UUID.randomUUID().toString(), "PENDING", new BigDecimal("10.00"), List.of())));

        PaymentRequest request = new PaymentRequest("ORD-5", new BigDecimal("10.00"), "VISA_TEST");
        String token = generateToken(UUID.randomUUID().toString(), "CUSTOMER");

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payForOrder_withoutToken_returns401() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-6", new BigDecimal("10.00"), "VISA_TEST");

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PAY2: View Payment Status ----------

    @Test
    void getPaymentByOrderId_owner_returns200() throws Exception {
        String userId = UUID.randomUUID().toString();
        persistPayment("ORD-7", new BigDecimal("15.00"), PaymentStatus.SUCCESS, "VISA_TEST");
        Mockito.when(orderClient.getOrder("ORD-7")).thenReturn(Optional.of(
                new OrderInfo("ORD-7", userId, "PAID", new BigDecimal("15.00"), List.of())));
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(get("/payments/order/{orderId}", "ORD-7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-7"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getPaymentByOrderId_noPayment_returns404() throws Exception {
        String token = generateToken(UUID.randomUUID().toString(), "CUSTOMER");

        mockMvc.perform(get("/payments/order/{orderId}", "does-not-exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    // ---------- PAY3: Request a Refund ----------

    @Test
    void requestRefund_eligible_returns201() throws Exception {
        String userId = UUID.randomUUID().toString();
        Payment payment = persistPayment("ORD-8", new BigDecimal("25.00"), PaymentStatus.SUCCESS, "VISA_TEST");
        Mockito.when(orderClient.getOrder("ORD-8")).thenReturn(Optional.of(
                new OrderInfo("ORD-8", userId, "CANCELLED", new BigDecimal("25.00"), List.of())));

        RefundRequest request = new RefundRequest(new BigDecimal("25.00"));
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(post("/payments/{id}/refund", payment.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void requestRefund_orderNotCancelled_returns409() throws Exception {
        String userId = UUID.randomUUID().toString();
        Payment payment = persistPayment("ORD-9", new BigDecimal("25.00"), PaymentStatus.SUCCESS, "VISA_TEST");
        Mockito.when(orderClient.getOrder("ORD-9")).thenReturn(Optional.of(
                new OrderInfo("ORD-9", userId, "PAID", new BigDecimal("25.00"), List.of())));

        RefundRequest request = new RefundRequest(new BigDecimal("25.00"));
        String token = generateToken(userId, "CUSTOMER");

        mockMvc.perform(post("/payments/{id}/refund", payment.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ELIGIBLE"));
    }

    // ---------- PAY4: View Payment/Refund History ----------

    @Test
    void listAllPayments_asAdmin_returns200() throws Exception {
        persistPayment("ORD-10", new BigDecimal("5.00"), PaymentStatus.SUCCESS, "VISA_TEST");
        String token = generateToken(UUID.randomUUID().toString(), "ADMIN");

        mockMvc.perform(get("/payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listAllPayments_nonAdmin_returns403() throws Exception {
        String token = generateToken(UUID.randomUUID().toString(), "CUSTOMER");

        mockMvc.perform(get("/payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private Payment persistPayment(String orderId, BigDecimal amount, PaymentStatus status, String method) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(status)
                .method(method)
                .build();
        return paymentRepository.save(payment);
    }

    /** Mints a JWT signed with the same shared secret user-service uses. */
    private String generateToken(String userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
