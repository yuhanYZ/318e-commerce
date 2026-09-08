package com.ecommerce.paymentservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Request body for PAY1 (specs/openapi-contract.yaml PaymentRequest schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", message = "amount must not be negative")
    private BigDecimal amount;

    @NotBlank(message = "method is required")
    private String method;
}
