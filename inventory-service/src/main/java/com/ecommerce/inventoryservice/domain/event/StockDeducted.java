package com.ecommerce.inventoryservice.domain.event;

import java.time.Instant;

/** {@code StockDeducted} - domain-models §2.5.1. Stock taken for a newly created order (I2). */
public record StockDeducted(String productId, int quantity, String orderId, Instant occurredAt)
        implements StockItemEvent {

    public static final String TYPE = "StockDeducted";

    @Override
    public String eventType() {
        return TYPE;
    }
}
