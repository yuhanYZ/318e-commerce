package com.ecommerce.paymentservice.dto;

import java.util.List;

/**
 * openapi-contract-v2.yaml schema for GET /api/v1/payments (PAY4). Per the user story
 * ("returns all payment and refund records for reconciliation") and
 * 6-cqrs-command-query.md §7's {@code ViewPaymentHistoryQuery} read source ("Payment + Refund CRUD
 * store"), this returns both record sets: {@code content} is the Payment page, {@code refunds} is
 * an independently-paged Refund slice using the same {@code page}/{@code size} parameters.
 */
public record PaymentPageResponse(
        List<PaymentResponse> content,
        int page,
        long totalElements,
        List<RefundResponse> refunds,
        long totalRefundElements
) {
}
