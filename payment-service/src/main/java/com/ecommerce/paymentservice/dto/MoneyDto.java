package com.ecommerce.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MoneyDto(
        @NotNull BigDecimal amount,
        @NotBlank String currency
) {
}
