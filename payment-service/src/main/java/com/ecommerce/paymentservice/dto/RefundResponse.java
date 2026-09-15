package com.ecommerce.paymentservice.dto;

import java.time.Instant;

public record RefundResponse(
        String id,
        String paymentId,
        MoneyDto amount,
        String status,
        Instant requestedAt
) {
}
