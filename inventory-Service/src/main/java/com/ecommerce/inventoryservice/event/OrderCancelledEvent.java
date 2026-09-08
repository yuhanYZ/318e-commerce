package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Consumed for I5 (specs/2-user-stories.md User Story I5): order-service
 * publishes this on the "order-cancelled" topic after O4. Local copy of
 * order-service's event class of the same name - see OrderCreatedEvent's
 * javadoc for why, and application.yml for the spring.json.type.mapping alias.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private String orderId;
    private String userId;
    private List<OrderItemPayload> items;
}
