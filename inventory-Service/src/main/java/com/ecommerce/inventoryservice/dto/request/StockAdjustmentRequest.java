package com.ecommerce.inventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for I3 (specs/openapi-contract.yaml StockAdjustmentRequest schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {

    @NotNull(message = "delta is required")
    private Integer delta;

    private String reason;
}
