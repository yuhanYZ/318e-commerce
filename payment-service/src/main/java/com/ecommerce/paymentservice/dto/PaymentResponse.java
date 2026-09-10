package com.ecommerce.paymentservice.dto;

import java.time.Instant;

public record PaymentResponse(
        String id,
        String orderId,
        MoneyDto amount,
        String status,
        String method,
        Instant createdAt
) {
}
