package com.ecommerce.userservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a registered account (Customer or Admin).
 * Maps to the {@code User} domain class in specs/3-domain-models.md section 2.1.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash - the plaintext password is never stored. */
    @Column(nullable = false)
    private String passwordHash;

    private String name;

    private String address;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Consecutive failed login count within the current lockout window (U2). Reset on success. */
    @Column(nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /** Timestamp of the first failed attempt in the current failure window; null when no failures are pending. */
    private LocalDateTime firstFailedAttemptAt;

    /** While {@code status == LOCKED}, the account auto-unlocks once this timestamp has passed. */
    private LocalDateTime lockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
