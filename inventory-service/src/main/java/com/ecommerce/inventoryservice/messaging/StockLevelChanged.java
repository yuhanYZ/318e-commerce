package com.ecommerce.inventoryservice.messaging;

import java.time.Instant;

/**
 * Input to the I4 low-stock stream function: a stock item's level after one appended event.
 *
 * <p>Internal to Inventory Service. It is never consumed by another service, which keeps the domain
 * model's "Published: none" rule for integration events intact. Under the kafka profile it travels
 * on the internal {@code inventory.stock-level-changed} topic, keyed by product id so updates for one
 * product stay ordered.
 */
public record StockLevelChanged(String productId, int quantityAvailable, long version, Instant occurredAt) {
}
