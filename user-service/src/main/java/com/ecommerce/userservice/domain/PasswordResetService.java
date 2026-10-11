package com.ecommerce.userservice.domain;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Domain Service — specs/iter2/3-domain-models-user-service-ddd.md §2.4.2. Issues reset tokens and
 * validates them for expiry/replay before a reset is allowed. The token lifetime is a business
 * calculation owned here (the specification only says "time-limited"): 30 minutes.
 */
@Component
public class PasswordResetService {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Creates a new token aggregate, expiring {@link #TOKEN_TTL} after {@code now}. */
    public PasswordResetToken generateToken(UserId userId, Instant now) {
        return PasswordResetToken.issue(
                "PRT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId,
                new ResetToken(randomValue()),
                now.plus(TOKEN_TTL),
                now);
    }

    /** A token is usable only while it is unconsumed and unexpired (aggregate invariants 1 and 2). */
    public boolean validateToken(PasswordResetToken token, Instant now) {
        return token.isUsable(now);
    }

    private static String randomValue() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
