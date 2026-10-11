package com.ecommerce.userservice.domain;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Domain Service — specs/iter2/3-domain-models-user-service-ddd.md §2.4.1. Verifies credentials,
 * tracks failed attempts and applies the lockout policy (5 failures / 10 minutes).
 *
 * <p>The lockout state machine and its events stay on {@link User} (invariant 2 is an aggregate
 * invariant); this service supplies the credential check and exposes the policy to the application
 * service as the specification describes.
 *
 * <p>Note on the specified signature: the spec lists {@code verifyCredentials(user, passwordHash)}.
 * A salted BCrypt hash cannot be compared against another hash, so the candidate arrives as the
 * raw submitted password and is verified through the encoder — the stored value is still a
 * {@link PasswordHash} VO.
 */
@Component
public class AuthenticationService {

    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public boolean verifyCredentials(User user, String rawCandidatePassword) {
        return passwordEncoder.matches(rawCandidatePassword, user.getPasswordHash().value());
    }

    /** Increments the failure count and applies the lockout policy; returns the resulting status. */
    public AccountStatus recordFailedAttempt(User user, Instant now) {
        user.recordFailedAttempt(now);
        return user.getStatus();
    }

    /** Clears {@code failedLoginAttempts} on a successful login. */
    public void resetFailureCount(User user) {
        user.resetFailureCount();
    }
}
