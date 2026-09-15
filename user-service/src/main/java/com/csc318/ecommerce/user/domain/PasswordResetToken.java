package com.csc318.ecommerce.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens", uniqueConstraints = @UniqueConstraint(columnNames = "tokenHash"))
public class PasswordResetToken {
    @Id private String id;
    @Column(nullable = false) private String userId;
    @Column(nullable = false) private String tokenHash;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean used;

    protected PasswordResetToken() {}

    public PasswordResetToken(String id, String userId, String tokenHash, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public String getUserId() { return userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public void markUsed() { this.used = true; }
}
