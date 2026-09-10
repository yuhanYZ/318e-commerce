package com.ecommerce.paymentservice.dto;

/** {@code reason} is optional/free-text — the test always sends {@code {}}. */
public record RefundRequest(
        String reason
) {
}
