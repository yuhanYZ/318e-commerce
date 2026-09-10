package com.ecommerce.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal inter-service response DTO for GET /orders/{id} on Order Service. Tolerant of extra
 * fields the real response carries that Payment Service does not need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderView(
        String id,
        String status,
        MoneyDto totalAmount
) {
}
