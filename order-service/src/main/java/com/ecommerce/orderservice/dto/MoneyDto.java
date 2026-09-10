package com.ecommerce.orderservice.dto;

import java.math.BigDecimal;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/Money. */
public record MoneyDto(BigDecimal amount, String currency) {
}
