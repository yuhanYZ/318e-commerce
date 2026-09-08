package com.ecommerce.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed by this service (O5) once Payment Service (not yet built) publishes
 * it on the "payment-confirmed" topic after successfully processing payment
 * for {@code orderId}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmedEvent {
    private String orderId;
}
