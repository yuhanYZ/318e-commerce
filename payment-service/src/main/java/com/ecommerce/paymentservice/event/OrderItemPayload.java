package com.ecommerce.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One product line within {@link PaymentFailedEvent}. A local copy of
 * order-service/inventory-service's class of the same name - separate Maven
 * modules can't share a class, so all sides rely on an identical JSON shape
 * plus the spring.json.type.mapping aliases configured in application.yml to
 * deserialize correctly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemPayload {
    private String productId;
    private int quantity;
}
