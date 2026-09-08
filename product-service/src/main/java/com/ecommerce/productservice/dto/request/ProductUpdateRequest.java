package com.ecommerce.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** P4 (update). All fields optional; only non-null fields are applied. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    private String name;

    private String description;

    @DecimalMin(value = "0.0", message = "price must be non-negative")
    private BigDecimal price;

    private String imageUrl;
}
