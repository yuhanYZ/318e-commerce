package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * OpenAPI {@code Money} schema. {@code currency} is optional on the wire and defaults to AUD.
 * The non-negative rule is declared here for a clean 400, and re-enforced by the Money value
 * object so the invariant cannot be bypassed.
 */
public record MoneyDto(
        @NotNull(message = "price.amount is required")
        @DecimalMin(value = "0.0", message = "price.amount must not be negative")
        BigDecimal amount,

        String currency) {
}
