package com.csc318.ecommerce.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class UserAccount {
    @Id private String id;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String passwordHash;
    private String name;
    private String address;
    private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountStatus status;
    private int failedLoginAttempts;
    private LocalDateTime firstFailedAt;
    private LocalDateTime lockedUntil;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    protected UserAccount() {}

    public UserAccount(String id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = Role.CUSTOMER;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Role getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int value) { this.failedLoginAttempts = value; }
    public LocalDateTime getFirstFailedAt() { return firstFailedAt; }
    public void setFirstFailedAt(LocalDateTime value) { this.firstFailedAt = value; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime value) { this.lockedUntil = value; }
}
