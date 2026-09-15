package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Refund;
import com.ecommerce.paymentservice.dto.MoneyDto;
import com.ecommerce.paymentservice.dto.RefundResponse;

final class RefundMapper {

    private RefundMapper() {
    }

    static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                new MoneyDto(refund.getAmount(), refund.getCurrency()),
                refund.getStatus().name(),
                refund.getRequestedAt());
    }
}
