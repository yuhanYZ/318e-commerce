package com.ecommerce.productservice.dto;

import java.util.List;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/ProductPageResponse. */
public record ProductPageResponse(List<ProductResponse> content, int page, long totalElements) {
}
