package com.ecommerce.inventoryservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 (Contract-First Test Generation) — Inventory Service.
 *
 * Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /inventory/*),
 * specs/iter2/4-api-endpoints-v2.md §8, specs/iter2/2-user-stories-inventory-service-v2.md (I1, I3, I4),
 * specs/iter2/3-domain-models-inventory-service-ddd.md (StockItemAggregate — event-sourced),
 * specs/iter2/5-event-sourcing.md.
 *
 * I2 and I5 are Kafka event handlers (not REST endpoints — see openapi-contract-v2.yaml's
 * "Async / Event-Driven Workflows" note) and are out of scope for MockMvc contract tests;
 * they belong in a separate @EmbeddedKafka test class, not this one.
 *
 * Self-contained: no dependency on any Phase 4 main-code class. Expected to fail (404) until
 * Phase 4 implements the controller — that is the correct red state for this phase.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryServiceContractTests {

    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

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

    private static String adminToken() {
        return token("USR-ADMIN-1", "ADMIN");
    }

    private static String customerToken() {
        return token("USR-CUST-1", "CUSTOMER");
    }

    private static String newProductId() {
        return "PRD-TEST-" + UUID.randomUUID();
    }

    private static String adjustBody(int delta, String reason) {
        return """
                {"delta":%d,"reason":"%s"}
                """.formatted(delta, reason);
    }

    // ---------------------------------------------------------------
    // I3 — Adjust Stock (POST /inventory/{productId}/adjust, Admin only)
    // ---------------------------------------------------------------

    @Test
    void adjustStock_firstAdjustmentForNewProduct_startsStreamFromZero_returns200() throws Exception {
        String productId = newProductId();
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(50, "Supplier restock")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantityAvailable").value(50))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void adjustStock_missingDelta_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", newProductId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"no delta supplied"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void adjustStock_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", newProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(10, "restock")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adjustStock_customerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", newProductId())
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(10, "restock")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void adjustStock_negativeDeltaBelowZero_returns409StockInvariantViolation() throws Exception {
        String productId = newProductId();
        // Start the stream at quantity 10.
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(10, "initial stock")))
                .andExpect(status().isOk());

        // Attempt to deduct 11 -> would go below zero.
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(-11, "over-correction")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("StockInvariantViolation"));
    }

    // ---------------------------------------------------------------
    // I1 — Check Stock Availability (GET /inventory/{productId})
    // ---------------------------------------------------------------

    @Test
    void getStock_trackedProduct_returns200() throws Exception {
        String productId = newProductId();
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(20, "initial stock")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantityAvailable").value(20))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void getStock_untrackedProduct_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{productId}", newProductId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"));
    }

    // ---------------------------------------------------------------
    // I4 — List Low-Stock Alerts (GET /inventory/alerts, Admin only)
    // ---------------------------------------------------------------

    @Test
    void listLowStockAlerts_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listLowStockAlerts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listLowStockAlerts_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void adjustStock_crossingLowStockThreshold_triggersAlert() throws Exception {
        // Default low-stock threshold is 5 (see 3-domain-models-inventory-service-ddd.md
        // §2.4 InventoryPolicyService / application.yml app.inventory.low-stock-threshold).
        String productId = newProductId();
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(3, "starts below threshold")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stockItemId == '" + productId + "')]").exists());
    }
}
