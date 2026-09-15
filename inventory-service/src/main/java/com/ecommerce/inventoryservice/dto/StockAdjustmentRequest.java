package com.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * OpenAPI {@code StockAdjustmentRequest}; required: delta. Positive restocks, negative removes.
 * A zero delta is rejected by the aggregate, since it changes nothing.
 */
public record StockAdjustmentRequest(
        @NotNull(message = "delta is required")
        Integer delta,

        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason) {
}
