package com.ecommerce.inventoryservice.analytics;

import com.ecommerce.inventoryservice.integration.StockChangedEvent;

/**
 * The I4 topology's running state for one product: the available quantity obtained by applying
 * every {@code stock.changed} event for that product — the same fold
 * {@code StockItemAggregate} performs on the write path, done here on the stream instead.
 *
 * <p>Serialized into the aggregation's state store as JSON.
 */
public record ProductStock(String productId, int quantityAvailable, long lastEventEpochMilli) {

    public static ProductStock empty() {
        return new ProductStock(null, 0, 0L);
    }

    public ProductStock apply(StockChangedEvent event) {
        int quantity = switch (event.eventType()) {
            case "StockAdjusted" -> quantityAvailable + (event.delta() == null ? 0 : event.delta());
            case "StockDeducted" -> quantityAvailable - (event.quantity() == null ? 0 : event.quantity());
            case "StockReleased" -> quantityAvailable + (event.quantity() == null ? 0 : event.quantity());
            default -> quantityAvailable;
        };
        // The write path already rejects a deduction that would go negative, so this clamp only
        // guards against a partially-applied state store; a negative alert state has no meaning.
        long occurredAt = event.occurredAt() == null ? lastEventEpochMilli : event.occurredAt().toEpochMilli();
        return new ProductStock(event.productId(), Math.max(quantity, 0), occurredAt);
    }
}
