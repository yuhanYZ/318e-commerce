package com.ecommerce.productservice.dto;

import java.util.List;

/** OpenAPI {@code ProductPageResponse} - the { content, page, totalElements } shape from §7 rule 6. */
public record ProductPageResponse(
        List<ProductResponse> content,
        int page,
        long totalElements) {
}
