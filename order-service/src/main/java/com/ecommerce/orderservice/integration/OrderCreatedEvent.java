package com.ecommerce.orderservice.integration;

import com.ecommerce.orderservice.domain.Money;

import java.time.Instant;
import java.util.List;

/**
 * Published to Kafka topic {@code order.created} on O1 (place order) — consumed by Inventory
 * Service's I2 (DeductStockOnOrderCreatedCommand). specs/iter2/6-cqrs-command-query.md §8.
 *
 * <p>{@code totalAmount} was added for O6: the real-time analytics projection is a windowed
 * aggregation over this stream, so the revenue per order has to travel with the event (the item
 * list alone only carries prices indirectly, and the analytics topology must not do a database
 * lookup per event). Inventory Service ignores the extra field.
 */
public record OrderCreatedEvent(String orderId, String userId, List<OrderItemPayload> items, Money totalAmount,
                                Instant occurredAt) {
}
