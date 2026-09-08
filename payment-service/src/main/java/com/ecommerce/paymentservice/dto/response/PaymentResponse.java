package com.ecommerce.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Response body for PAY1/PAY2/PAY4 (specs/openapi-contract.yaml PaymentResponse schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String id;
    private String orderId;
    private BigDecimal amount;
    private String status;
    private String method;
    private LocalDateTime createdAt;
}
