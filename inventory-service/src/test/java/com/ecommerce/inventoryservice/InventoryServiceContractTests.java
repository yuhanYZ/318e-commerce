package com.ecommerce.inventoryservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 (Contract-First Test Generation) - Inventory Service.
 *
 * <p>Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /inventory*),
 * 4-api-endpoints-v2.md §4 and §8, 2-user-stories-inventory-service-v2.md (I1, I3, I4),
 * 3-domain-models-inventory-service-ddd.md, 5-event-sourcing.md.
 *
 * <p>I2 and I5 are Kafka consumers, not REST endpoints, and are deliberately not covered here.
 *
 * <p>Self-contained: depends on no Phase 4 main-code class. Every test uses its own product id,
 * so tests are order-independent against the shared in-memory H2 instance.
 *
 * <p>Threshold is pinned to 5 so alert tests do not depend on the environment.
 * Alert rule under test: open when quantity &lt; 5, resolve once quantity &gt;= 5.
 */
@SpringBootTest(properties = "app.inventory.low-stock-threshold=5")
@AutoConfigureMockMvc
class InventoryServiceContractTests {

    /** Must match app.jwt.secret in application.yml. */
    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    private static final String BASE = "/api/v1/inventory";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // I1 - Check Stock Availability (Query, public, reads inventory_read_model)
    // ------------------------------------------------------------------

    /** Invariant 3: a product with no event stream is untracked - 404, not a zero-quantity 200. */
    @Test
    void getStock_untrackedProduct_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/" + newProductId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getStock_afterAdjust_isPublicAndReturnsProjection() throws Exception {
        String productId = newProductId();
        adjust(productId, 42, "Initial stock").andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantityAvailable").value(42))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void getStock_trackedButEmpty_returns200WithIsAvailableFalse() throws Exception {
        String productId = newProductId();
        adjust(productId, 3, "stock in").andExpect(status().isOk());
        adjust(productId, -3, "stock out").andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(0))
                .andExpect(jsonPath("$.isAvailable").value(false))
                .andExpect(jsonPath("$.version").value(2));
    }

    // ------------------------------------------------------------------
    // I3 - Adjust Stock (Command, Admin only, appends StockAdjusted)
    // ------------------------------------------------------------------

    /** The first adjustment starts the stream from an implicit 0 - there is no init event. */
    @Test
    void adjust_firstAdjustment_startsStreamAtVersion1() throws Exception {
        String productId = newProductId();

        adjust(productId, 50, "Supplier restock")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantityAvailable").value(50))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void adjust_subsequentAdjustments_accumulateAndIncrementVersion() throws Exception {
        String productId = newProductId();
        adjust(productId, 10, "restock").andExpect(status().isOk());

        adjust(productId, -4, "damaged")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(6))
                .andExpect(jsonPath("$.version").value(2));
    }

    /** Invariant 1: stock can never go below zero. The rejected change must leave no trace. */
    @Test
    void adjust_wouldGoBelowZero_returns409AndLeavesStockUnchanged() throws Exception {
        String productId = newProductId();
        adjust(productId, 2, "restock").andExpect(status().isOk());

        adjust(productId, -3, "too many")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("StockInvariantViolation"));

        mockMvc.perform(get(BASE + "/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(2))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void adjust_negativeOnUntrackedProduct_returns409AndDoesNotStartStream() throws Exception {
        String productId = newProductId();

        adjust(productId, -1, "nothing to remove")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("StockInvariantViolation"));

        mockMvc.perform(get(BASE + "/" + productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void adjust_missingDelta_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/" + newProductId() + "/adjust")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no delta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    /** A zero delta is neither a restock nor a deduction, and would only pollute the event stream. */
    @Test
    void adjust_zeroDelta_returns400() throws Exception {
        adjust(newProductId(), 0, "no-op")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void adjust_malformedBody_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/" + newProductId() + "/adjust")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": \"lots\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void adjust_noToken_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/" + newProductId() + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":5}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("Unauthorized"));
    }

    @Test
    void adjust_asCustomer_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/" + newProductId() + "/adjust")
                        .header("Authorization", bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    // ------------------------------------------------------------------
    // I4 - Low-Stock Alerts (Query, Admin only, active alerts only)
    // ------------------------------------------------------------------

    @Test
    void listAlerts_asAdmin_returnsArray() throws Exception {
        mockMvc.perform(get(BASE + "/alerts").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listAlerts_noToken_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("Unauthorized"));
    }

    @Test
    void listAlerts_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/alerts").header("Authorization", bearer(customerToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void alert_opensWhenStockBelowThreshold() throws Exception {
        String productId = newProductId();
        adjust(productId, 3, "small delivery").andExpect(status().isOk());

        JsonNode alert = singleActiveAlert(productId);
        assertThat(alert.get("id").asText()).isNotBlank();
        assertThat(alert.get("stockItemId").asText()).isEqualTo(productId);
        assertThat(alert.get("triggeredAt").isNull()).isFalse();
        assertThat(alert.path("resolvedAt").isMissingNode() || alert.get("resolvedAt").isNull()).isTrue();
    }

    @Test
    void alert_notOpenedWhenStockExactlyAtThreshold() throws Exception {
        String productId = newProductId();
        adjust(productId, 5, "exactly threshold").andExpect(status().isOk());

        assertThat(activeAlertCount(productId)).isZero();
    }

    /** Invariant: at most one unresolved alert per stock item. */
    @Test
    void alert_notDuplicatedWhileAlreadyOpen() throws Exception {
        String productId = newProductId();
        adjust(productId, 4, "below").andExpect(status().isOk());
        adjust(productId, -1, "further below").andExpect(status().isOk());

        assertThat(activeAlertCount(productId)).isEqualTo(1);
    }

    @Test
    void alert_resolvesOnceStockReachesThreshold() throws Exception {
        String productId = newProductId();
        adjust(productId, 2, "below").andExpect(status().isOk());
        assertThat(activeAlertCount(productId)).isEqualTo(1);

        adjust(productId, 3, "back to threshold").andExpect(status().isOk());

        assertThat(activeAlertCount(productId)).isZero();
    }

    /** I4 iii: a new alert is raised only after stock recovers and then drops below again. */
    @Test
    void alert_reopensAfterRecoveryAndSecondDrop() throws Exception {
        String productId = newProductId();
        adjust(productId, 2, "below").andExpect(status().isOk());
        adjust(productId, 10, "recovered").andExpect(status().isOk());
        assertThat(activeAlertCount(productId)).isZero();

        adjust(productId, -9, "drop again").andExpect(status().isOk());

        assertThat(activeAlertCount(productId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ResultActions adjust(String productId, int delta, String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/" + productId + "/adjust")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":%d,\"reason\":\"%s\"}".formatted(delta, reason)));
    }

    private int activeAlertCount(String productId) throws Exception {
        int count = 0;
        for (JsonNode alert : listAlerts()) {
            if (productId.equals(alert.get("stockItemId").asText())) {
                count++;
            }
        }
        return count;
    }

    private JsonNode singleActiveAlert(String productId) throws Exception {
        JsonNode match = null;
        for (JsonNode alert : listAlerts()) {
            if (productId.equals(alert.get("stockItemId").asText())) {
                assertThat(match).as("more than one active alert for " + productId).isNull();
                match = alert;
            }
        }
        assertThat(match).as("no active alert for " + productId).isNotNull();
        return match;
    }

    private JsonNode listAlerts() throws Exception {
        String body = mockMvc.perform(get(BASE + "/alerts").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String newProductId() {
        return "PRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String adminToken() {
        return token("admin-user-id", "ADMIN");
    }

    private static String customerToken() {
        return token("customer-user-id", "CUSTOMER");
    }

    /** Mirrors the token User Service issues: sub = userId, role, exp. */
    private static String token(String subject, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(key)
                .compact();
    }
}
