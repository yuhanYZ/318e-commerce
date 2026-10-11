package com.ecommerce.userservice.domain;

import com.ecommerce.userservice.exception.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code PasswordResetTokenAggregate} (root: PasswordResetToken) —
 * specs/iter2/3-domain-models-user-service-ddd.md §2.3.2.
 *
 * <p>Invariants: (1) a token can be consumed at most once; (2) a token cannot be consumed after
 * {@code expiresAt}. Both are enforced by {@link #consume(Instant)}, which is also the only place
 * that emits {@code PasswordResetCompleted}.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    private String id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "user_id", nullable = false))
    private UserId userId;

    @Convert(converter = ResetToken.JpaConverter.class)
    @Column(nullable = false, unique = true)
    private ResetToken token;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected PasswordResetToken() {
        // JPA
    }

    /** U4 — issue a token for a user; emits {@code PasswordResetRequested}. */
    public static PasswordResetToken issue(String id, UserId userId, ResetToken token, Instant expiresAt,
                                           Instant now) {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.id = id;
        resetToken.userId = userId;
        resetToken.token = token;
        resetToken.expiresAt = expiresAt;
        resetToken.used = false;
        resetToken.raise(new PasswordResetRequested(id, userId, expiresAt, now));
        return resetToken;
    }

    /** Invariants 1 and 2 — reusable for validation without mutating the aggregate. */
    public boolean isUsable(Instant now) {
        return !used && expiresAt.isAfter(now);
    }

    /** U4 — consume the token exactly once; emits {@code PasswordResetCompleted}. */
    public PasswordResetCompleted consume(Instant now) {
        if (!isUsable(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ResetTokenInvalidOrExpired",
                    "This reset token has expired or already been used");
        }
        this.used = true;
        PasswordResetCompleted event = new PasswordResetCompleted(id, userId, now);
        raise(event);
        return event;
    }

    /** Drains the events raised by the behaviors above so the command service can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    public String getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public ResetToken getToken() {
        return token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }
}
