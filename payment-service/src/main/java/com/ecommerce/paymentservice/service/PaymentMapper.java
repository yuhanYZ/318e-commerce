package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.dto.MoneyDto;
import com.ecommerce.paymentservice.dto.PaymentResponse;

/** Maps the aggregate onto the API contract's DTOs — specs/iter2/openapi-contract-v2.yaml. */
final class PaymentMapper {

    private PaymentMapper() {
    }

    static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId().value(),
                payment.getOrderId().value(),
                new MoneyDto(payment.getAmount().amount(), payment.getAmount().currency()),
                payment.getStatus().name(),
                payment.getMethod(),
                payment.getCreatedAt());
    }
}
