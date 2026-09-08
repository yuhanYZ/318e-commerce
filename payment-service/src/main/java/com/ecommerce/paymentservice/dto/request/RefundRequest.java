package com.ecommerce.paymentservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Request body for PAY3 (specs/openapi-contract.yaml RefundRequest schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", message = "amount must not be negative")
    private BigDecimal amount;
}
