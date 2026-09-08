package com.ecommerce.orderservice.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Partial shape of Product Service's GET /products/{id} response
 * (specs/openapi-contract.yaml ProductDetailResponse schema) - only the fields
 * ProductClient needs (price, for the unit-price snapshot required by
 * specs/3-domain-models.md section 4.2; status, reserved for future use).
 * Unknown JSON fields (name, description, ...) are ignored by Spring's default
 * Jackson configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {
    private String id;
    private BigDecimal price;
    private String status;
}
