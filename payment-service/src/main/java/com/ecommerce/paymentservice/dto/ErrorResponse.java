package com.ecommerce.paymentservice.dto;

import java.time.Instant;
import java.util.Map;

/** specs/iter2/openapi-contract-v2.yaml components/schemas/ErrorResponse. */
public record ErrorResponse(String code, String message, Instant timestamp, Map<String, Object> details) {

    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), null);
    }
}
