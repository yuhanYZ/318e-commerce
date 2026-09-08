package com.ecommerce.userservice.domain;

/**
 * Lifecycle status of a {@link User} account.
 * <ul>
 *   <li>{@code ACTIVE} - normal, usable account.</li>
 *   <li>{@code LOCKED} - temporarily locked after too many consecutive failed
 *       login attempts within the configured time window (U2). Auto-unlocks
 *       once {@code lockedUntil} has passed.</li>
 *   <li>{@code DEACTIVATED} - soft-deleted by the user (U5). Order history and
 *       other services' references to this user id remain valid.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DEACTIVATED
}
