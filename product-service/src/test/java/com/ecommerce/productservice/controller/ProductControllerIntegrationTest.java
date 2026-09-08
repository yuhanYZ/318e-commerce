package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.domain.Product;
import com.ecommerce.productservice.domain.ProductStatus;
import com.ecommerce.productservice.dto.external.StockResponse;
import com.ecommerce.productservice.dto.request.ProductCreateRequest;
import com.ecommerce.productservice.dto.request.ProductUpdateRequest;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.service.InventoryClient;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-first integration tests generated from specs/openapi-contract.yaml for
 * the /products/* endpoints (P1-P4). InventoryClient is mocked because
 * Inventory Service does not exist yet in this repo (see InventoryClient's javadoc).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @MockBean
    private InventoryClient inventoryClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---------- P1: Browse Product Catalog ----------

    @Test
    void browseProducts_returnsOnlyActiveProducts() throws Exception {
        persistProduct("Active Widget", "CAT-1", ProductStatus.ACTIVE);
        persistProduct("Inactive Widget", "CAT-1", ProductStatus.INACTIVE);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Active Widget"));
    }

    @Test
    void browseProducts_filtersByCategoryId() throws Exception {
        persistProduct("Mouse", "CAT-electronics", ProductStatus.ACTIVE);
        persistProduct("Novel", "CAT-books", ProductStatus.ACTIVE);

        mockMvc.perform(get("/products").param("categoryId", "CAT-electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].categoryId").value("CAT-electronics"));
    }

    // ---------- P3: Search Products ----------

    @Test
    void searchProducts_matchesNameOrDescription() throws Exception {
        persistProduct("Wireless Mouse", "CAT-1", ProductStatus.ACTIVE);
        persistProduct("USB Keyboard", "CAT-1", ProductStatus.ACTIVE);

        mockMvc.perform(get("/products/search").param("q", "mouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Wireless Mouse"));
    }

    @Test
    void searchProducts_noMatches_returnsEmptyArrayNot404() throws Exception {
        mockMvc.perform(get("/products/search").param("q", "nonexistent-keyword-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- P2: View Product Details ----------

    @Test
    void getProductById_returnsDetailsWithStockFromInventoryService() throws Exception {
        Product product = persistProduct("Wireless Mouse", "CAT-1", ProductStatus.ACTIVE);
        Mockito.when(inventoryClient.getStock(product.getId()))
                .thenReturn(new StockResponse(product.getId(), 42, true));

        mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.stockAvailable").value(42))
                .andExpect(jsonPath("$.isInStock").value(true));
    }

    @Test
    void getProductById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/products/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    // ---------- P4: Create / Update / Deactivate a Product (Admin) ----------

    @Test
    void createProduct_asAdmin_returns201() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "New Gadget", "A shiny new gadget", new BigDecimal("19.99"), null, "CAT-1");
        String adminToken = generateToken(UUID.randomUUID().toString(), "ADMIN");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Gadget"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createProduct_asNonAdmin_returns403() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "New Gadget", "desc", new BigDecimal("19.99"), null, "CAT-1");
        String customerToken = generateToken(UUID.randomUUID().toString(), "CUSTOMER");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_withoutToken_returns401() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "New Gadget", "desc", new BigDecimal("19.99"), null, "CAT-1");

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProduct_asAdmin_returns200AndUpdatedFields() throws Exception {
        Product product = persistProduct("Old Name", "CAT-1", ProductStatus.ACTIVE);
        String adminToken = generateToken(UUID.randomUUID().toString(), "ADMIN");
        ProductUpdateRequest request = new ProductUpdateRequest(
                "New Name", null, new BigDecimal("29.99"), null);

        mockMvc.perform(put("/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.price").value(29.99));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        String adminToken = generateToken(UUID.randomUUID().toString(), "ADMIN");
        ProductUpdateRequest request = new ProductUpdateRequest("New Name", null, null, null);

        mockMvc.perform(put("/products/{id}", "does-not-exist")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateProduct_asAdmin_returns200AndHidesFromBrowse() throws Exception {
        Product product = persistProduct("Discontinued Item", "CAT-1", ProductStatus.ACTIVE);
        String adminToken = generateToken(UUID.randomUUID().toString(), "ADMIN");

        mockMvc.perform(patch("/products/{id}/deactivate", product.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void deactivateProduct_asNonAdmin_returns403() throws Exception {
        Product product = persistProduct("Some Item", "CAT-1", ProductStatus.ACTIVE);
        String customerToken = generateToken(UUID.randomUUID().toString(), "CUSTOMER");

        mockMvc.perform(patch("/products/{id}/deactivate", product.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private Product persistProduct(String name, String categoryId, ProductStatus status) {
        Product product = Product.builder()
                .name(name)
                .description(name + " description")
                .price(new BigDecimal("9.99"))
                .categoryId(categoryId)
                .status(status)
                .build();
        return productRepository.save(product);
    }

    /** Mints a JWT signed with the same shared secret user-service uses, so Admin-role checks can be tested here in isolation. */
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
