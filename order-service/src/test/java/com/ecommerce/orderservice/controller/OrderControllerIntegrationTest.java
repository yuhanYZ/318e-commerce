package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.Order;
import com.ecommerce.orderservice.domain.OrderItem;
import com.ecommerce.orderservice.domain.OrderStatus;
import com.ecommerce.orderservice.dto.external.ProductInfo;
import com.ecommerce.orderservice.dto.external.StockResponse;
import com.ecommerce.orderservice.dto.request.CreateOrderRequest;
import com.ecommerce.orderservice.dto.request.OrderItemRequest;
import com.ecommerce.orderservice.messaging.OrderEventPublisher;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.service.InventoryClient;
import com.ecommerce.orderservice.service.ProductClient;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-first integration tests generated from specs/openapi-contract.yaml
 * for the /orders/* endpoints (O1-O4). InventoryClient and ProductClient are
 * mocked because Inventory Service and Product Service aren't actually running
 * during this test (see their javadocs); OrderEventPublisher is mocked because
 * no Kafka broker is provisioned in this repo yet. O5 (the PaymentEventListener
 * Kafka consumer) has no REST endpoint and is covered separately by
 * OrderServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---------- O1: Place an Order ----------

    @Test
    void placeOrder_withSufficientStock_returns201() throws Exception {
        Mockito.when(inventoryClient.getStock(anyString()))
                .thenReturn(new StockResponse("PRD-1", 10, true));
        Mockito.when(productClient.getProduct(anyString()))
                .thenReturn(new ProductInfo("PRD-1", new BigDecimal("19.99"), "ACTIVE"));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("PRD-1", 2)));
        String token = generateToken(UUID.randomUUID().toString());

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(39.98))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void placeOrder_withInsufficientStock_returns409() throws Exception {
        Mockito.when(inventoryClient.getStock(anyString()))
                .thenReturn(new StockResponse("PRD-1", 1, true));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("PRD-1", 5)));
        String token = generateToken(UUID.randomUUID().toString());

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void placeOrder_withoutToken_returns401() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("PRD-1", 1)));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- O2: View Order Status ----------

    @Test
    void getOrderById_ownedByUser_returns200() throws Exception {
        String userId = UUID.randomUUID().toString();
        Order order = persistOrder(userId, OrderStatus.PENDING);
        String token = generateToken(userId);

        mockMvc.perform(get("/orders/{id}", order.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()));
    }

    @Test
    void getOrderById_ownedByAnotherUser_returns403() throws Exception {
        Order order = persistOrder(UUID.randomUUID().toString(), OrderStatus.PENDING);
        String otherUserToken = generateToken(UUID.randomUUID().toString());

        mockMvc.perform(get("/orders/{id}", order.getId())
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        String token = generateToken(UUID.randomUUID().toString());

        mockMvc.perform(get("/orders/{id}", "does-not-exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ---------- O3: View Order History ----------

    @Test
    void listMyOrders_returnsOnlyCurrentUsersOrders() throws Exception {
        String userId = UUID.randomUUID().toString();
        persistOrder(userId, OrderStatus.PENDING);
        persistOrder(UUID.randomUUID().toString(), OrderStatus.PENDING);
        String token = generateToken(userId);

        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(userId));
    }

    // ---------- O4: Cancel an Order ----------

    @Test
    void cancelOrder_pending_returns200AndCancelled() throws Exception {
        String userId = UUID.randomUUID().toString();
        Order order = persistOrder(userId, OrderStatus.PENDING);
        String token = generateToken(userId);

        mockMvc.perform(post("/orders/{id}/cancel", order.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_alreadyCancelled_returns409() throws Exception {
        String userId = UUID.randomUUID().toString();
        Order order = persistOrder(userId, OrderStatus.CANCELLED);
        String token = generateToken(userId);

        mockMvc.perform(post("/orders/{id}/cancel", order.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    // ---------- helpers ----------

    private Order persistOrder(String userId, OrderStatus status) {
        Order order = Order.builder()
                .userId(userId)
                .status(status)
                .totalAmount(new BigDecimal("39.98"))
                .build();
        OrderItem item = OrderItem.builder()
                .productId("PRD-1")
                .quantity(2)
                .unitPrice(new BigDecimal("19.99"))
                .build();
        order.addItem(item);
        return orderRepository.save(order);
    }

    /** Mints a JWT signed with the same shared secret user-service uses, so ownership checks can be tested here in isolation. */
    private String generateToken(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("role", "CUSTOMER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
