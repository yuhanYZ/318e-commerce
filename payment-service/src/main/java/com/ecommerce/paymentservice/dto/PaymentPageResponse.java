package com.ecommerce.paymentservice.dto;

import java.util.List;

/**
 * openapi-contract-v2.yaml schema for GET /api/v1/payments: {@code content} is
 * {@code PaymentResponse[]} only. (The narrative spec text mentions "all payment and refund
 * records", but the OpenAPI schema is authoritative and is what the contract test asserts against.)
 */
public record PaymentPageResponse(
        List<PaymentResponse> content,
        int page,
        long totalElements
) {
}
