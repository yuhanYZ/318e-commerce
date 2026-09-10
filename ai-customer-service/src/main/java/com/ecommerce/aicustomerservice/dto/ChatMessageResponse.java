package com.ecommerce.aicustomerservice.dto;

import java.time.Instant;

/** Represents the AI's reply message posted in response to the customer's message (AI1/AI2). */
public record ChatMessageResponse(
        String sessionId,
        String messageId,
        String sender,
        String content,
        Instant createdAt
) {
}
