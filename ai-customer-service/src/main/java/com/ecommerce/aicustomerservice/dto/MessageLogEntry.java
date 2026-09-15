package com.ecommerce.aicustomerservice.dto;

import java.time.Instant;

/** AI4 — one row of GET /api/v1/ai-chat/sessions/{sessionId}/messages (Admin only, returned as a bare JSON array). */
public record MessageLogEntry(
        String id,
        String sender,
        String content,
        Instant createdAt
) {
}
