package com.ecommerce.inventoryservice.dto;

import java.time.Instant;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/LowStockAlertResponse. */
public record LowStockAlertResponse(String id, String stockItemId, Instant triggeredAt, Instant resolvedAt) {
}
