package com.ecommerce.aicustomerservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.2. Internal only; raised for both the user's message and the AI reply. */
public record MessageSent(SessionId sessionId, String messageId, Sender sender, Instant occurredAt)
        implements DomainEvent {
}
