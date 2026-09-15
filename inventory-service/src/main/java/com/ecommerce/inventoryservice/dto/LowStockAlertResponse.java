package com.ecommerce.inventoryservice.dto;

import java.time.Instant;

/**
 * OpenAPI {@code LowStockAlertResponse}. {@code stockItemId} carries the product id, since the two
 * are interchangeable. {@code resolvedAt} is serialised as null for an open alert.
 */
public record LowStockAlertResponse(String id, String stockItemId, Instant triggeredAt, Instant resolvedAt) {
}
