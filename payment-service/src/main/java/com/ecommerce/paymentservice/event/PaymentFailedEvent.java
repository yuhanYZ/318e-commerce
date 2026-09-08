package com.ecommerce.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published on the "payment-failed" topic after PAY1 declines a payment (mock
 * gateway decline, or a request amount that doesn't match the order's actual
 * total). Two consumers react to this: order-service's O5
 * (PENDING -> PAYMENT_FAILED) and inventory-service's I5 (releases the stock
 * it deducted for this order) - {@code items} is included specifically for
 * the latter, mirroring order-service's own revised PaymentFailedEvent shape
 * (see that project's earlier design note on this exact field). Local copy -
 * see OrderItemPayload's javadoc for why.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String orderId;
    private String reason;
    private List<OrderItemPayload> items;
}
