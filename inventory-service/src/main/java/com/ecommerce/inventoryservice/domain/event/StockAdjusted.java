package com.ecommerce.inventoryservice.domain.event;

import java.time.Instant;

/** {@code StockAdjusted} - domain-models §2.5.3. Manual restock or correction by an Admin (I3). */
public record StockAdjusted(String productId, int delta, String reason, String adjustedBy, Instant occurredAt)
        implements StockItemEvent {

    public static final String TYPE = "StockAdjusted";

    @Override
    public String eventType() {
        return TYPE;
    }
}
