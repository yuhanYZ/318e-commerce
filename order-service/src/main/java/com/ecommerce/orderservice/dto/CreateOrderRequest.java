package com.ecommerce.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/CreateOrderRequest. */
public record CreateOrderRequest(@NotEmpty @Valid List<CreateOrderItemRequest> items) {
}
