package com.ecommerce.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** OpenAPI {@code ProductCreateRequest}; required: name, price, categoryId. */
public record ProductCreateRequest(
        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "price is required")
        @Valid
        MoneyDto price,

        String imageUrl,

        @NotBlank(message = "categoryId is required")
        String categoryId) {
}
