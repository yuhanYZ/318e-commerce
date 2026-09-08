package com.ecommerce.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on the "payment-confirmed" topic after PAY1 succeeds. order
 * -service's O5 consumer (PaymentEventListener.onPaymentConfirmed) reacts to
 * this by transitioning the order PENDING -> PAID. Local copy of
 * order-service's event class of the same name - see OrderItemPayload's
 * javadoc for why, and application.yml for the spring.json.type.mapping alias.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmedEvent {
    private String orderId;
}
