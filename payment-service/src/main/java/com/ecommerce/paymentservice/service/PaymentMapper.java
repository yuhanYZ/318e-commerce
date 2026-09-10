package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.dto.MoneyDto;
import com.ecommerce.paymentservice.dto.PaymentResponse;

final class PaymentMapper {

    private PaymentMapper() {
    }

    static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                new MoneyDto(payment.getAmount(), payment.getCurrency()),
                payment.getStatus().name(),
                payment.getMethod(),
                payment.getCreatedAt());
    }
}
