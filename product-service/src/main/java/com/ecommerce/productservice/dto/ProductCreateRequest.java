package com.ecommerce.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/ProductCreateRequest. */
public record ProductCreateRequest(@NotBlank String name, String description, @NotNull @Valid MoneyDto price,
                                    String imageUrl, @NotBlank String categoryId) {
}
