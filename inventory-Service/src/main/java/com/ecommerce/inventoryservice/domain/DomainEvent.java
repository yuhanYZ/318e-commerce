package com.ecommerce.inventoryservice.domain;

import java.time.Instant;

/**
 * Marker for domain events that are not rows of the {@code StockItemAggregate} event store —
 * currently the {@code LowStockAlert} lifecycle events of
 * specs/iter2/3-domain-models-inventory-service-ddd.md section 2.3.2.
 *
 * <p>StockDeducted / StockReleased / StockAdjusted are deliberately not modelled as their own
 * classes: in this service they are the persisted event-store records themselves
 * ({@link StockEvent}), discriminated by their {@code eventType}.
 */
public interface DomainEvent {

    Instant occurredAt();
}
