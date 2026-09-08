package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Consumed for I2 (specs/2-user-stories.md User Story I2): order-service
 * publishes this on the "order-created" topic after O1. This is a local copy
 * of order-service's event class of the same name, not a shared dependency -
 * separate Maven modules use separate packages for equivalent events, so both
 * sides configure spring.json.type.mapping (application.yml) to map the same
 * alias string ("order-created") to their OWN local class instead of relying
 * on Kafka's default fully-qualified-class-name __TypeId__ header, which would
 * fail to resolve across modules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private List<OrderItemPayload> items;
}
