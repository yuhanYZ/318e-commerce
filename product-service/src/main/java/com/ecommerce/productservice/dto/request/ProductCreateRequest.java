package com.ecommerce.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** P4 (create). Required fields match the ProductCreateRequest schema in specs/openapi-contract.yaml. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0", message = "price must be non-negative")
    private BigDecimal price;

    private String imageUrl;

    @NotBlank(message = "categoryId is required")
    private String categoryId;
}
