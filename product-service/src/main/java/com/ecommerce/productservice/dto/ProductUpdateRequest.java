package com.ecommerce.productservice.dto;

import jakarta.validation.Valid;

/**
 * specs/iter2/openapi-contract-v2.yaml components/schemas/ProductUpdateRequest — partial update,
 * every field optional; omitted (null) fields are left unchanged.
 */
public record ProductUpdateRequest(String name, String description, @Valid MoneyDto price, String imageUrl,
                                    String categoryId) {
}
