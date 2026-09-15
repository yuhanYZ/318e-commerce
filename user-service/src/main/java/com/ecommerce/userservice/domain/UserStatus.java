package com.ecommerce.userservice.domain;

/**
 * Persisted status. "LOCKED" (per the API contract's UserProfileResponse enum) is NOT a
 * persisted value here — it is derived at read time from {@link User#isCurrentlyLocked()},
 * since the lockout is time-based (auto-unlock after 10 minutes) rather than a durable flag.
 */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED
}
