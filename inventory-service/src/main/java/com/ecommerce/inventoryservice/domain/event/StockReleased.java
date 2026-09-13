package com.ecommerce.inventoryservice.domain.event;

import java.time.Instant;

/** {@code StockReleased} - domain-models §2.5.2. Stock returned for a cancelled or unpaid order (I5). */
public record StockReleased(String productId, int quantity, String orderId, Instant occurredAt)
        implements StockItemEvent {

    public static final String TYPE = "StockReleased";

    @Override
    public String eventType() {
        return TYPE;
    }
}
