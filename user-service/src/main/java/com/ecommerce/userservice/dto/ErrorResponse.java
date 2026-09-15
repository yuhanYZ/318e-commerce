package com.ecommerce.userservice.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Shared error shape — specs/iter2/openapi-contract-v2.yaml components.schemas.ErrorResponse.
 * Duplicated per-service (no shared library between the six independent Maven modules).
 */
public record ErrorResponse(String code, String message, Instant timestamp, Map<String, Object> details) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), null);
    }
}
