package com.ecommerce.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Response body for PAY3 (specs/openapi-contract.yaml RefundResponse schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private String id;
    private String paymentId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime requestedAt;
}
