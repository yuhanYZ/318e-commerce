package com.ecommerce.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published after a new order is created (O1), per specs/2-user-stories.md
 * User Story O1 and specs/1-technical-architecture.md's Kafka example.
 * Inventory Service (not yet built) is expected to consume this on the
 * "order-created" topic and deduct stock per item (I2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private List<OrderItemPayload> items;
}
