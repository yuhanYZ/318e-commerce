package com.ecommerce.userservice.domain;

/**
 * Value Object (enumerated) — specs/iter2/3-domain-models-user-service-ddd.md §2.2.6.
 *
 * <p>{@code LOCKED} is a real persisted state in this iteration (invariant 2: "status transitions
 * to LOCKED only after 5 failed logins within a 10-minute window"). It is cleared either by
 * {@code User.unlockIfExpired(now)} on the next login attempt, or — for read purposes — by
 * {@code User.effectiveStatus()}, which reports a lock whose window has already elapsed as ACTIVE
 * so a profile read never shows a stale LOCKED.
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DEACTIVATED
}
