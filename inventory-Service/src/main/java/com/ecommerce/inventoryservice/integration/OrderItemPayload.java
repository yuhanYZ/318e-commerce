package com.ecommerce.inventoryservice.integration;

/** specs/iter2/6-cqrs-command-query.md §8 — line-item shape shared by OrderCreatedEvent/OrderCancelledEvent. */
public record OrderItemPayload(String productId, int quantity) {
}
