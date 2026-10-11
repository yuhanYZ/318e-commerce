package com.ecommerce.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code UserAggregate} (root: User) — specs/iter2/3-domain-models-user-service-ddd.md §2.3.1.
 * Table name is "app_user" (not "user") because USER is a reserved word in several SQL dialects,
 * H2 included.
 *
 * <p>State changes go through the behaviors below, each of which raises the domain event the
 * specification assigns to it; the events are collected on the aggregate and drained by
 * {@link #pullDomainEvents()} after the command service has persisted the aggregate.
 * Fields are typed value objects (§2.2) mapped onto plain columns by the VOs' JPA converters.
 *
 * <p>Invariants: (1) email is unique across users — enforced at the aggregate boundary by the
 * command service's lookup; (2) status transitions to LOCKED only after 5 failed logins within a
 * 10-minute window; (3) DEACTIVATED is terminal.
 */
@Entity
@Table(name = "app_user")
public class User {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(10);

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id", nullable = false))
    private UserId id;

    @Convert(converter = Email.JpaConverter.class)
    @Column(nullable = false, unique = true)
    private Email email;

    @Convert(converter = PasswordHash.JpaConverter.class)
    @Column(nullable = false)
    private PasswordHash passwordHash;

    private String name;

    @Embedded
    private ContactInfo contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private int failedLoginAttempts;

    /** Non-null while a lock is in force; the lock lapses once this instant has passed. */
    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant createdAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected User() {
        // JPA
    }

    /** U1 — Register an Account. Always produces a CUSTOMER; emits {@code UserRegistered}. */
    public static User register(UserId id, Email email, PasswordHash passwordHash, String name,
                                ContactInfo contact, Instant now) {
        User user = new User();
        user.id = id;
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.contact = contact == null ? ContactInfo.empty() : contact;
        user.role = Role.CUSTOMER;
        user.status = AccountStatus.ACTIVE;
        user.failedLoginAttempts = 0;
        user.createdAt = now;
        user.raise(new UserRegistered(id, email, now));
        return user;
    }

    /**
     * System provisioning path used by {@code config.AdminSeeder}. Not a user story: there is no
     * self-service route to the ADMIN role, so this is the only way an ADMIN comes into being.
     */
    public static User seedAdmin(UserId id, Email email, PasswordHash passwordHash, Instant now) {
        User user = register(id, email, passwordHash, null, ContactInfo.empty(), now);
        user.role = Role.ADMIN;
        return user;
    }

    /**
     * U2 — a successful credential check. Clears the failure counter and any lock, and emits
     * {@code UserLoggedIn}. DEACTIVATED is terminal (invariant 3), so it is never revived here.
     */
    public UserLoggedIn recordSuccessfulLogin(Instant now) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        if (this.status == AccountStatus.LOCKED) {
            this.status = AccountStatus.ACTIVE;
        }
        UserLoggedIn event = new UserLoggedIn(id, now);
        raise(event);
        return event;
    }

    /**
     * U2 — a failed credential check. Crosses into LOCKED on the 5th failure (invariant 2) and
     * emits {@code AccountLocked}; earlier failures return {@code null} and emit nothing.
     */
    public AccountLocked recordFailedAttempt(Instant now) {
        this.failedLoginAttempts += 1;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.status = AccountStatus.LOCKED;
            this.lockedUntil = now.plus(LOCKOUT_DURATION);
            AccountLocked event = new AccountLocked(id, failedLoginAttempts, now);
            raise(event);
            return event;
        }
        return null;
    }

    /** Clears the failure counter on a successful login. */
    public void resetFailureCount() {
        this.failedLoginAttempts = 0;
    }

    /** Clears a lock whose window has already elapsed, so the next attempt is evaluated normally. */
    public void unlockIfExpired(Instant now) {
        if (status == AccountStatus.LOCKED && (lockedUntil == null || !lockedUntil.isAfter(now))) {
            status = AccountStatus.ACTIVE;
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
    }

    /** True while a lock is actually in force; a lock whose window has elapsed reads as unlocked. */
    public boolean isCurrentlyLocked() {
        return status == AccountStatus.LOCKED && lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** Read-side status — never reports a stale LOCKED for a lock whose window has elapsed. */
    public AccountStatus effectiveStatus() {
        if (isCurrentlyLocked()) {
            return AccountStatus.LOCKED;
        }
        return status == AccountStatus.LOCKED ? AccountStatus.ACTIVE : status;
    }

    /** U3b — Update Profile (partial; only non-null inputs are applied). Emits {@code ProfileUpdated}. */
    public ProfileUpdated updateProfile(String name, ContactInfo contact, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (contact != null) {
            this.contact = contact;
        }
        ProfileUpdated event = new ProfileUpdated(id, now);
        raise(event);
        return event;
    }

    /** U5 — Deactivate Account (soft delete). Emits {@code AccountDeactivated}. */
    public AccountDeactivated deactivate(Instant now) {
        this.status = AccountStatus.DEACTIVATED;
        AccountDeactivated event = new AccountDeactivated(id, now);
        raise(event);
        return event;
    }

    /**
     * The User-side half of {@code ConfirmPasswordResetCommand} (the other half is
     * {@link PasswordResetToken#consume(Instant)}, which emits {@code PasswordResetCompleted}).
     * The specification's behavior list does not name a separate password-change operation, so it
     * raises no event of its own — the token's consumption is the auditable step.
     */
    public void changePassword(PasswordHash newPasswordHash) {
        this.passwordHash = newPasswordHash;
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

    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public ContactInfo getContact() {
        return contact;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
