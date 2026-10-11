package com.ecommerce.inventoryservice.integration;

import java.time.Instant;

/**
 * Published to Kafka topic {@code stock.changed} — Inventory Service republishing its own
 * append-only stock events so the I4 low-stock-alert projection can be a stream-processing
 * topology (specs/iter2/6-cqrs-command-query.md section 8).
 *
 * <p>All three stock event types share this one topic and are discriminated by {@code eventType}:
 * {@code delta} carries StockAdjusted's signed adjustment, {@code quantity} carries
 * StockDeducted's / StockReleased's magnitude (see 5-event-sourcing.md section 3.3).
 */
public record StockChangedEvent(String productId, String eventType, Integer quantity, Integer delta,
                                Instant occurredAt) {
}
