package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.dto.MoneyDto;
import com.ecommerce.paymentservice.dto.RefundResponse;

/** Maps the aggregate onto the API contract's DTOs — specs/iter2/openapi-contract-v2.yaml. */
final class RefundMapper {

    private RefundMapper() {
    }

    static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId().value(),
                new MoneyDto(refund.getAmount().amount(), refund.getAmount().currency()),
                refund.getStatus().name(),
                refund.getRequestedAt());
    }
}
