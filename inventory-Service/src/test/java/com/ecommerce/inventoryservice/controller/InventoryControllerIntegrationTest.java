package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.domain.LowStockAlert;
import com.ecommerce.inventoryservice.domain.StockItem;
import com.ecommerce.inventoryservice.dto.request.StockAdjustmentRequest;
import com.ecommerce.inventoryservice.messaging.InventoryEventPublisher;
import com.ecommerce.inventoryservice.repository.LowStockAlertRepository;
import com.ecommerce.inventoryservice.repository.StockItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-first integration tests generated from specs/openapi-contract.yaml
 * for the /inventory/* endpoints (I1, I3, I4). InventoryEventPublisher is
 * mocked because no Kafka broker is provisioned in this repo yet. I2/I5 (the
 * Kafka consumers) have no REST endpoint and are covered separately by
 * InventoryServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private LowStockAlertRepository lowStockAlertRepository;

    @MockBean
    private InventoryEventPublisher inventoryEventPublisher;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---------- I1: Check Stock Availability ----------

    @Test
    void getStock_existingStockItem_returns200() throws Exception {
        StockItem stockItem = persistStockItem("PRD-1", 42, 10);

        mockMvc.perform(get("/inventory/{productId}", stockItem.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PRD-1"))
                .andExpect(jsonPath("$.quantityAvailable").value(42))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void getStock_unknownProduct_returns404() throws Exception {
        mockMvc.perform(get("/inventory/{productId}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_ITEM_NOT_FOUND"));
    }

    // ---------- I3: Adjust Stock / Restock ----------

    @Test
    void adjustStock_restockExistingItem_returns200() throws Exception {
        persistStockItem("PRD-2", 5, 10);
        StockAdjustmentRequest request = new StockAdjustmentRequest(50, "Supplier restock");
        String token = generateToken("ADMIN");

        mockMvc.perform(post("/inventory/{productId}/adjust", "PRD-2")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(55));
    }

    @Test
    void adjustStock_newProduct_createsStockItem() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(20, "Initial stock");
        String token = generateToken("ADMIN");

        mockMvc.perform(post("/inventory/{productId}/adjust", "PRD-NEW")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PRD-NEW"))
                .andExpect(jsonPath("$.quantityAvailable").value(20));
    }

    @Test
    void adjustStock_belowZero_returns400() throws Exception {
        persistStockItem("PRD-3", 5, 10);
        StockAdjustmentRequest request = new StockAdjustmentRequest(-10, "Damaged goods");
        String token = generateToken("ADMIN");

        mockMvc.perform(post("/inventory/{productId}/adjust", "PRD-3")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STOCK_ADJUSTMENT"));
    }

    @Test
    void adjustStock_withoutAdminRole_returns403() throws Exception {
        persistStockItem("PRD-4", 5, 10);
        StockAdjustmentRequest request = new StockAdjustmentRequest(10, null);
        String token = generateToken("CUSTOMER");

        mockMvc.perform(post("/inventory/{productId}/adjust", "PRD-4")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjustStock_droppingBelowThreshold_triggersLowStockAlert() throws Exception {
        persistStockItem("PRD-5", 15, 10);
        StockAdjustmentRequest request = new StockAdjustmentRequest(-8, "Bulk sale");
        String token = generateToken("ADMIN");

        mockMvc.perform(post("/inventory/{productId}/adjust", "PRD-5")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(7));

        mockMvc.perform(get("/inventory/alerts").header("Authorization", "Bearer " + generateToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- I4: List Active Low-Stock Alerts ----------

    @Test
    void listActiveAlerts_asAdmin_returnsOnlyUnresolved() throws Exception {
        StockItem stockItem = persistStockItem("PRD-6", 2, 10);
        persistAlert(stockItem.getId(), false);
        persistAlert(stockItem.getId(), true);
        String token = generateToken("ADMIN");

        mockMvc.perform(get("/inventory/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stockItemId").value(stockItem.getId()));
    }

    @Test
    void listActiveAlerts_nonAdmin_returns403() throws Exception {
        String token = generateToken("CUSTOMER");

        mockMvc.perform(get("/inventory/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private StockItem persistStockItem(String productId, int quantity, int threshold) {
        StockItem stockItem = StockItem.builder()
                .productId(productId)
                .quantityAvailable(quantity)
                .lowStockThreshold(threshold)
                .build();
        return stockItemRepository.save(stockItem);
    }

    private void persistAlert(String stockItemId, boolean resolved) {
        LowStockAlert alert = LowStockAlert.builder()
                .stockItemId(stockItemId)
                .resolvedAt(resolved ? LocalDateTime.now() : null)
                .build();
        lowStockAlertRepository.save(alert);
    }

    /** Mints a JWT signed with the same shared secret user-service uses. */
    private String generateToken(String role) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
