package com.ecommerce.aicustomerservice.domain;

import java.time.Instant;

/** Domain Event — §2.5.3. Internal only. */
public record SessionEscalated(SessionId sessionId, Instant occurredAt) implements DomainEvent {
}
