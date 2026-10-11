package com.ecommerce.inventoryservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-inventory-service-ddd.md section 2.3.2. */
public record LowStockAlertResolved(String alertId, StockItemId stockItemId, Instant occurredAt)
        implements DomainEvent {
}
