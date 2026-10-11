package com.ecommerce.userservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-user-service-ddd.md §2.5.3. */
public record AccountLocked(UserId userId, int failedAttempts, Instant occurredAt) implements DomainEvent {
}
