package com.csc318.ecommerce.product;

import com.csc318.ecommerce.product.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductServiceContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    @Test
    void adminCrudAndPublicQueriesFollowContract() throws Exception {
        String admin = jwtService.issue("USR-ADMIN", "admin@example.com", "ADMIN");
        String createdBody = mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wireless Mouse\",\"description\":\"USB mouse\",\"price\":29.99,\"categoryId\":\"CAT-DEFAULT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        String id = created.get("id").asText();

        mvc.perform(get("/api/v1/products").param("categoryId", "CAT-DEFAULT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));

        mvc.perform(get("/api/v1/products/search").param("q", "wireless"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAvailable").isNumber())
                .andExpect(jsonPath("$.isInStock").isBoolean());

        mvc.perform(put("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":34.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(34.99));

        mvc.perform(patch("/api/v1/products/{id}/deactivate", id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void authorizationValidationAndNotFoundFollowContract() throws Exception {
        String customer = jwtService.issue("USR-CUSTOMER", "customer@example.com", "CUSTOMER");
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":10,\"categoryId\":\"CAT-DEFAULT\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":10,\"categoryId\":\"CAT-DEFAULT\"}"))
                .andExpect(status().isUnauthorized());

        String admin = jwtService.issue("USR-ADMIN", "admin@example.com", "ADMIN");
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"price\":-1,\"categoryId\":\"CAT-DEFAULT\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/products/PRD-MISSING")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/search").param("q", " ")).andExpect(status().isBadRequest());
    }
}
