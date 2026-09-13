package com.ecommerce.inventoryservice.domain.event;

import java.time.Instant;

/** {@code LowStockAlertResolved} - domain-models §2.5.5. */
public record LowStockAlertResolved(String alertId, String stockItemId, Instant occurredAt) {
}
