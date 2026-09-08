package com.ecommerce.productservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Matches ProductDetailResponse in specs/openapi-contract.yaml (ProductResponse fields
 * plus live stock info from Inventory Service, P2). The boolean field is explicitly
 * mapped with @JsonProperty("isInStock") because Jackson's default bean-property
 * naming would otherwise serialize a Java field/getter named isInStock as "inStock",
 * not the "isInStock" the contract requires.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private String categoryId;
    private String status;
    private int stockAvailable;

    @JsonProperty("isInStock")
    private boolean inStock;
}
