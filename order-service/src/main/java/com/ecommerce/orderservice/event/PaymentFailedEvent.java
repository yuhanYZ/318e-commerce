package com.ecommerce.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Consumed by this service (O5) once Payment Service (not yet built) publishes
 * it on the "payment-failed" topic after a failed payment attempt for
 * {@code orderId}. {@code items} is included (even though OrderService.markPaymentFailed
 * only needs orderId) so Inventory Service can also consume this exact event
 * to release the stock it deducted for the order (I5) without a second lookup -
 * see specs/2-user-stories.md User Story I5, which explicitly names
 * PaymentFailedEvent as an alternative trigger to OrderCancelledEvent, and
 * OrderCancelledEvent already carries items for the same reason.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String orderId;
    private String reason;
    private List<OrderItemPayload> items;
}
