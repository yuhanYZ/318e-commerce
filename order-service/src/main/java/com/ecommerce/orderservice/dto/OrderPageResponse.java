package com.ecommerce.orderservice.dto;

import java.util.List;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/OrderPageResponse. */
public record OrderPageResponse(List<OrderResponse> content, int page, long totalElements) {
}
