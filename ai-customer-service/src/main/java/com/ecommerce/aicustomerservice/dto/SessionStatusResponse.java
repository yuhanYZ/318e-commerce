package com.ecommerce.aicustomerservice.dto;

import java.time.Instant;

public record SessionStatusResponse(
        String sessionId,
        String status,
        Instant updatedAt
) {
}
