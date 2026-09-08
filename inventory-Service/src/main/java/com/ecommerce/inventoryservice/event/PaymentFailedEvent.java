package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Consumed for I5 (User Story I5 explicitly names this as an alternative
 * trigger to OrderCancelledEvent): Payment Service (not yet built) will
 * publish this on the "payment-failed" topic. order-service also consumes it
 * (O5), and its copy of this class was revised to add {@code items}
 * specifically so this service could release stock from the same event
 * without a second lookup. Local copy - see OrderCreatedEvent's javadoc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String orderId;
    private String reason;
    private List<OrderItemPayload> items;
}
