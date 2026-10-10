package com.ecommerce.userservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-user-service-ddd.md §2.5.6. */
public record PasswordResetRequested(String tokenId, UserId userId, Instant expiresAt, Instant occurredAt)
        implements DomainEvent {
}
