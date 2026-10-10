package com.ecommerce.aicustomerservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.1. Internal only. */
public record SessionStarted(SessionId sessionId, UserId userId, Instant occurredAt) implements DomainEvent {
}
