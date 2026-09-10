package com.ecommerce.productservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 (Contract-First Test Generation) — Product Service.
 *
 * Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /products/*),
 * specs/iter2/4-api-endpoints-v2.md §8, specs/iter2/2-user-stories-product-service-v2.md,
 * specs/iter2/3-domain-models-product-service-ddd.md.
 *
 * P2 (GET /products/{id}) makes an inter-service REST call to Inventory Service
 * (GET /inventory/{productId}) per the openapi description. Per
 * 1-technical-architecture-v2.md's testing strategy, this is stubbed with
 * MockRestServiceServer rather than a live Inventory Service.
 *
 * REQUIRED OF PHASE 4: expose the RestTemplate used for that call as a Spring bean named
 * exactly "inventoryRestTemplate" (e.g. @Bean RestTemplate inventoryRestTemplate(...)) so this
 * test class can bind MockRestServiceServer to it. Everything else in this file is self-contained.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductServiceContractTests {

    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
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

    private static String adminToken() {
        return token("USR-ADMIN-1", "ADMIN");
    }

    private static String customerToken() {
        return token("USR-CUST-1", "CUSTOMER");
    }

    private static String createBody(String name, double amount, String categoryId) {
        return """
                {"name":"%s","description":"desc","price":{"amount":%s,"currency":"AUD"},"categoryId":"%s"}
                """.formatted(name, amount, categoryId);
    }

    private String createProductAsAdmin() throws Exception {
        String body = createBody("Wireless Mouse " + UUID.randomUUID(), 29.99, "CAT-10");
        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    // ---------------------------------------------------------------
    // P4 — Create Product (POST /products, Admin only)
    // ---------------------------------------------------------------

    @Test
    void createProduct_happyPath_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Mechanical Keyboard", 89.00, "CAT-10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createProduct_missingName_returns400ValidationFailed() throws Exception {
        String body = """
                {"price":{"amount":10.0,"currency":"AUD"},"categoryId":"CAT-10"}
                """;
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void createProduct_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("No Auth Product", 10.0, "CAT-10")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_customerRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Forbidden Product", 10.0, "CAT-10")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    // ---------------------------------------------------------------
    // P1 — Browse Catalog (GET /products)
    // ---------------------------------------------------------------

    @Test
    void browseProducts_happyPath_returns200PageResponse() throws Exception {
        createProductAsAdmin();
        mockMvc.perform(get("/api/v1/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ---------------------------------------------------------------
    // P3 — Search Products (GET /products/search)
    // ---------------------------------------------------------------

    @Test
    void searchProducts_happyPath_returns200Array() throws Exception {
        mockMvc.perform(get("/api/v1/products/search").param("q", "mouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void searchProducts_missingQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/search"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------
    // P4 — Update Product (PUT /products/{id}, Admin only)
    // ---------------------------------------------------------------

    @Test
    void updateProduct_happyPath_partialUpdate_returns200() throws Exception {
        String id = createProductAsAdmin();
        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Updated description only"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description only"));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", "PRD-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_onInactiveProduct_returns409ProductStateViolation() throws Exception {
        String id = createProductAsAdmin();
        mockMvc.perform(patch("/api/v1/products/{id}/deactivate", id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Should be rejected"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ProductStateViolation"));
    }

    // ---------------------------------------------------------------
    // P4 — Deactivate Product (PATCH /products/{id}/deactivate, Admin only)
    // ---------------------------------------------------------------

    @Test
    void deactivateProduct_happyPath_returns200() throws Exception {
        String id = createProductAsAdmin();
        mockMvc.perform(patch("/api/v1/products/{id}/deactivate", id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivateProduct_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/products/{id}/deactivate", "PRD-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // P2 — Get Product Details incl. live stock (GET /products/{id})
    // ---------------------------------------------------------------

    @Test
    void getProductById_withStockAvailable_returns200InStock() throws Exception {
        String id = createProductAsAdmin();

        MockRestServiceServer server = MockRestServiceServer.bindTo(inventoryRestTemplate).build();
        server.expect(requestTo("http://localhost:8084/api/v1/inventory/" + id))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"productId":"%s","quantityAvailable":42,"isAvailable":true,"version":3}
                        """.formatted(id), MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAvailable").value(42))
                .andExpect(jsonPath("$.isInStock").value(true));

        server.verify();
    }

    @Test
    void getProductById_inventoryHasNoRecord_returnsZeroStockNotOutOfStockError() throws Exception {
        String id = createProductAsAdmin();

        MockRestServiceServer server = MockRestServiceServer.bindTo(inventoryRestTemplate).build();
        server.expect(requestTo("http://localhost:8084/api/v1/inventory/" + id))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAvailable").value(0))
                .andExpect(jsonPath("$.isInStock").value(false));

        server.verify();
    }

    @Test
    void getProductById_productNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "PRD-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"));
    }
}
