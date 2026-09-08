package com.ecommerce.paymentservice.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The subset of order-service's OrderItemResponse this service needs - just
 * enough to rebuild the {@code items} list for PaymentFailedEvent (which
 * inventory-service consumes for I5 stock release). Unknown JSON fields
 * (unitPrice, ...) are ignored by Spring's default Jackson configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemInfo {
    private String productId;
    private int quantity;
}
