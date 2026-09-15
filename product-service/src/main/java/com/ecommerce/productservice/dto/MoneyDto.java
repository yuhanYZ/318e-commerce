package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/Money. */
public record MoneyDto(@NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal amount,
                        @NotBlank String currency) {
}
