package com.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/StockAdjustmentRequest. */
public record StockAdjustmentRequest(@NotNull Integer delta, String reason) {
}
