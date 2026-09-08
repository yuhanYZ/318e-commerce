package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One product line within {@link OrderCreatedEvent} / {@link OrderCancelledEvent} /
 * {@link PaymentFailedEvent}. A local copy of order-service's class of the same
 * name - separate Maven modules can't share a class, so both sides rely on an
 * identical JSON shape plus the spring.json.type.mapping aliases configured in
 * application.yml to deserialize correctly (see OrderCreatedEvent's javadoc).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemPayload {
    private String productId;
    private int quantity;
}
