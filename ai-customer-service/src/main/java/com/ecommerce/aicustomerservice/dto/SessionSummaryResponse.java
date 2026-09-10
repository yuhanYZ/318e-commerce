package com.ecommerce.aicustomerservice.dto;

import java.time.Instant;

/** AI4 — one row of GET /api/v1/ai-chat/sessions (Admin only). */
public record SessionSummaryResponse(
        String sessionId,
        String userId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
