package com.ecommerce.inventoryservice.domain.event;

import java.time.Instant;

/**
 * An event on a {@code StockItem} stream - 5-event-sourcing.md §3.3. Sealed so rehydration can
 * switch over every event type exhaustively, and a new type cannot be added without the
 * aggregate learning how to apply it.
 */
public sealed interface StockItemEvent permits StockAdjusted, StockDeducted, StockReleased {

    /** Stream identity. StockItemId takes the same value as ProductId (domain-models, intro). */
    String productId();

    Instant occurredAt();

    /** The {@code event_type} value stored in the event store. */
    String eventType();
}
