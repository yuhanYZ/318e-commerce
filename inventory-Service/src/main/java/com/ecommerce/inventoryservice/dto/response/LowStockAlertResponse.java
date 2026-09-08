package com.ecommerce.inventoryservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Response body for I4 (specs/openapi-contract.yaml LowStockAlertResponse schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertResponse {
    private String id;
    private String stockItemId;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;
}
