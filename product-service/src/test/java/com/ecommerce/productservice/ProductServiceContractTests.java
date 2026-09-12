package com.ecommerce.productservice;

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
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 (Contract-First Test Generation) - Product Service.
 *
 * <p>Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /products*),
 * specs/iter2/4-api-endpoints-v2.md §2 and §8 (error contract),
 * specs/iter2/2-user-stories-product-service-v2.md (P1-P4),
 * specs/iter2/3-domain-models-product-service-ddd.md (ProductAggregate).
 *
 * <p>Self-contained: depends on no Phase 4 main-code class, only on HTTP behaviour the
 * contract promises. Expected to fail until Phase 4 implements the controller - that is
 * the correct red state for this phase.
 *
 * <p>Tests create their own data and never assume an empty database, so they are safe to
 * run in any order against the shared in-memory H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductServiceContractTests {

    /** Must match app.jwt.secret in application.yml (HS256, shared across all six services). */
    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    private static final String BASE = "/api/v1/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // P1 - Browse Product Catalog (Query, public)
    // ------------------------------------------------------------------

    @Test
    void browseProducts_returnsPagedShape() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void browseProducts_isPublic_noTokenRequired() throws Exception {
        mockMvc.perform(get(BASE).param("page", "0").param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void browseProducts_filtersByCategory() throws Exception {
        String categoryId = "CAT-" + shortId();
        createProduct("Filtered Item", categoryId);

        mockMvc.perform(get(BASE).param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].categoryId").value(categoryId));
    }

    @Test
    void browseProducts_unknownCategory_returnsEmptyContentNotError() throws Exception {
        mockMvc.perform(get(BASE).param("categoryId", "CAT-DOES-NOT-EXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void browseProducts_excludesDeactivatedProducts() throws Exception {
        String categoryId = "CAT-" + shortId();
        String id = createProduct("Soon Inactive", categoryId);

        mockMvc.perform(patch(BASE + "/" + id + "/deactivate").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE).param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------------
    // P2 - View Product Details (Query, public, calls Inventory Service)
    // ------------------------------------------------------------------

    @Test
    void getProductById_returnsDetailWithStockFields() throws Exception {
        String id = createProduct("Wireless Mouse", "CAT-10");

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Wireless Mouse"))
                .andExpect(jsonPath("$.price.amount").exists())
                .andExpect(jsonPath("$.price.currency").exists())
                .andExpect(jsonPath("$.categoryId").value("CAT-10"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stockAvailable").exists())
                .andExpect(jsonPath("$.isInStock").exists());
    }

    /**
     * Inventory Service is not running during tests. Per domain-models §2.3.1 "Inter-Service Note",
     * no stock record means stockAvailable = 0 and isInStock = false, not a failed request.
     */
    @Test
    void getProductById_inventoryUnavailable_reportsZeroStock() throws Exception {
        String id = createProduct("No Stock Record", "CAT-10");

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAvailable").value(0))
                .andExpect(jsonPath("$.isInStock").value(false));
    }

    @Test
    void getProductById_unknownId_returns404WithErrorContract() throws Exception {
        mockMvc.perform(get(BASE + "/PRD-NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ------------------------------------------------------------------
    // P3 - Search Products (Query, public)
    // ------------------------------------------------------------------

    @Test
    void searchProducts_matchesOnName() throws Exception {
        String token = shortId();
        createProduct("Mechanical Keyboard " + token, "CAT-20");

        mockMvc.perform(get(BASE + "/search").param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mechanical Keyboard " + token));
    }

    @Test
    void searchProducts_noMatch_returnsEmptyArrayNotError() throws Exception {
        mockMvc.perform(get(BASE + "/search").param("q", "zzz-no-such-product-" + shortId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchProducts_missingRequiredQParam_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    // ------------------------------------------------------------------
    // P4 - Create (Command, Admin only)
    // ------------------------------------------------------------------

    @Test
    void createProduct_asAdmin_returns201WithGeneratedId() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Gaming Chair", "CAT-30", "129.95", "AUD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Gaming Chair"))
                .andExpect(jsonPath("$.price.amount").value(129.95))
                .andExpect(jsonPath("$.price.currency").value("AUD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createProduct_noToken_returns401() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("No Auth", "CAT-30", "10.00", "AUD")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("Unauthorized"));
    }

    @Test
    void createProduct_asCustomer_returns403() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Not Allowed", "CAT-30", "10.00", "AUD")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void createProduct_missingRequiredName_returns400() throws Exception {
        String body = """
                {"description":"no name","price":{"amount":10.00,"currency":"AUD"},"categoryId":"CAT-30"}
                """;
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void createProduct_negativePrice_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Negative", "CAT-30", "-1.00", "AUD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    // ------------------------------------------------------------------
    // P4 - Update (Command, Admin only)
    // ------------------------------------------------------------------

    @Test
    void updateProduct_partialUpdate_leavesOmittedFieldsUnchanged() throws Exception {
        String id = createProduct("Original Name", "CAT-40");

        mockMvc.perform(put(BASE + "/" + id)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.categoryId").value("CAT-40"));
    }

    @Test
    void updateProduct_unknownId_returns404() throws Exception {
        mockMvc.perform(put(BASE + "/PRD-NOPE")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"));
    }

    @Test
    void updateProduct_noToken_returns401() throws Exception {
        mockMvc.perform(put(BASE + "/PRD-ANY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProduct_asCustomer_returns403() throws Exception {
        mockMvc.perform(put(BASE + "/PRD-ANY")
                        .header("Authorization", bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isForbidden());
    }

    /** Invariant 2: INACTIVE is terminal - domain-models §2.3.1, error code ProductStateViolation. */
    @Test
    void updateProduct_whenInactive_returns409() throws Exception {
        String id = createProduct("Will Be Inactive", "CAT-40");

        mockMvc.perform(patch(BASE + "/" + id + "/deactivate")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/" + id)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Too Late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ProductStateViolation"));
    }

    // ------------------------------------------------------------------
    // P4 - Deactivate (Command, Admin only)
    // ------------------------------------------------------------------

    @Test
    void deactivateProduct_asAdmin_returns200WithInactiveStatus() throws Exception {
        String id = createProduct("To Deactivate", "CAT-50");

        mockMvc.perform(patch(BASE + "/" + id + "/deactivate")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivateProduct_unknownId_returns404() throws Exception {
        mockMvc.perform(patch(BASE + "/PRD-NOPE/deactivate")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"));
    }

    @Test
    void deactivateProduct_noToken_returns401() throws Exception {
        mockMvc.perform(patch(BASE + "/PRD-ANY/deactivate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateProduct_asCustomer_returns403() throws Exception {
        mockMvc.perform(patch(BASE + "/PRD-ANY/deactivate")
                        .header("Authorization", bearer(customerToken())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Creates a product via the public contract and returns its generated id. */
    private String createProduct(String name, String categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, categoryId, "29.99", "AUD")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private static String productJson(String name, String categoryId, String amount, String currency) {
        return """
                {"name":"%s","description":"contract test fixture",
                 "price":{"amount":%s,"currency":"%s"},
                 "imageUrl":"https://cdn.example.com/test.jpg","categoryId":"%s"}
                """.formatted(name, amount, currency, categoryId);
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

    /** Mirrors the token User Service issues: sub = userId, role, exp - architecture §Security Model. */
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

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
