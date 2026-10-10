package com.ecommerce.aicustomerservice.domain;

import java.time.Instant;

/**
 * Domain Event — §2.5.4. No endpoint closes a session in this milestone (CLOSED is reserved for
 * future use), so this is raised only by {@code ConversationSession.close()}, which is likewise
 * unused by any controller.
 */
public record SessionClosed(SessionId sessionId, Instant occurredAt) implements DomainEvent {
}
