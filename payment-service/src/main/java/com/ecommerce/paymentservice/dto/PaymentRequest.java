package com.ecommerce.paymentservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotBlank String orderId,
        @NotNull @Valid MoneyDto amount
) {
}
