package com.ecommerce.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One product line within {@link OrderCreatedEvent} / {@link OrderCancelledEvent}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemPayload {
    private String productId;
    private int quantity;
}
