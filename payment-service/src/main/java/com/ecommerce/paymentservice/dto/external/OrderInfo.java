package com.ecommerce.paymentservice.dto.external;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape of the response from Order Service's GET /orders/{id}
 * (specs/openapi-contract.yaml OrderResponse schema). Used only for
 * deserializing that inter-service call in OrderClient - never returned
 * directly by this service's own API. Every PAY1-PAY3 operation needs this:
 * {@code userId} for ownership checks, {@code status} for payability/refund
 * -eligibility checks, {@code totalAmount} for PAY1's amount cross-check, and
 * {@code items} to populate PaymentFailedEvent for inventory-service's I5.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfo {
    private String id;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderItemInfo> items;
}
