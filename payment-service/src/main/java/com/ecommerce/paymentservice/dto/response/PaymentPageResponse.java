package com.ecommerce.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response body for PAY4 (specs/openapi-contract.yaml PaymentPageResponse schema). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPageResponse {
    private List<PaymentResponse> content;
    private int page;
    private long totalElements;
}
