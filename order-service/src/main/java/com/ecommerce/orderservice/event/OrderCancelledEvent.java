package com.ecommerce.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published after an order is cancelled (O4), per specs/2-user-stories.md User
 * Story O4. Inventory Service and Payment Service (neither built yet) are
 * expected to consume this on the "order-cancelled" topic to release
 * previously-deducted stock (I5) and process a refund respectively.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private String orderId;
    private String userId;
    private List<OrderItemPayload> items;
}
