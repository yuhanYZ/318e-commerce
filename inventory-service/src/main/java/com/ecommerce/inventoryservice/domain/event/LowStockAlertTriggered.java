package com.ecommerce.inventoryservice.domain.event;

import java.time.Instant;

/** {@code LowStockAlertTriggered} - domain-models §2.5.4. */
public record LowStockAlertTriggered(String alertId, String stockItemId, Instant occurredAt) {
}
