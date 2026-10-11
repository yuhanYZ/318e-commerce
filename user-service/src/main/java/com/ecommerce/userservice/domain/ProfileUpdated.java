package com.ecommerce.userservice.domain;

import java.time.Instant;

/** Domain Event — specs/iter2/3-domain-models-user-service-ddd.md §2.5.4. */
public record ProfileUpdated(UserId userId, Instant occurredAt) implements DomainEvent {
}
