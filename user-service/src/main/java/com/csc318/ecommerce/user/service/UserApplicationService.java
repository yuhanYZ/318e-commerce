package com.csc318.ecommerce.user.service;

import com.csc318.ecommerce.user.domain.*;
import com.csc318.ecommerce.user.infrastructure.persistence.*;
import com.csc318.ecommerce.user.infrastructure.security.JwtService;
import com.csc318.ecommerce.user.presentation.UserDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class UserApplicationService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public UserApplicationService(UserRepository users, PasswordResetTokenRepository tokens,
                                  PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.jwt = jwt;
    }

    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email is already registered");
        UserAccount user = users.save(new UserAccount(id("USR"), email, encoder.encode(request.password())));
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"));
        LocalDateTime now = LocalDateTime.now();
        if (user.getStatus() == AccountStatus.DEACTIVATED)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account is temporarily locked");
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) clearFailures(user);
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            recordFailure(user, now);
            if (user.getStatus() == AccountStatus.LOCKED)
                throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account is temporarily locked");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        clearFailures(user);
        return new AuthResponse(jwt.issue(user), user.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String userId) { return profileOf(requireActive(userId)); }

    public UserProfileResponse update(String userId, UpdateProfileRequest request) {
        UserAccount user = requireActive(userId);
        if (request.name() != null) user.setName(request.name());
        if (request.address() != null) user.setAddress(request.address());
        if (request.phone() != null) user.setPhone(request.phone());
        return profileOf(user);
    }

    public void deactivate(String userId) { requireActive(userId).setStatus(AccountStatus.DEACTIVATED); }

    public void requestReset(String emailValue) {
        users.findByEmail(normalize(emailValue)).filter(u -> u.getStatus() != AccountStatus.DEACTIVATED).ifPresent(user -> {
            String raw = UUID.randomUUID().toString();
            tokens.save(new PasswordResetToken(id("RST"), user.getId(), hash(raw), LocalDateTime.now().plusMinutes(15)));
        });
    }

    public void confirmReset(PasswordResetConfirmRequest request) {
        PasswordResetToken token = tokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Reset token is invalid or expired"));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Reset token is invalid or expired");
        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Reset token is invalid or expired"));
        user.setPasswordHash(encoder.encode(request.newPassword()));
        clearFailures(user);
        token.markUsed();
    }

    private UserAccount requireActive(String id) {
        return users.findById(id).filter(u -> u.getStatus() != AccountStatus.DEACTIVATED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }
    private void recordFailure(UserAccount user, LocalDateTime now) {
        if (user.getFirstFailedAt() == null || user.getFirstFailedAt().isBefore(now.minusMinutes(10))) {
            user.setFirstFailedAt(now); user.setFailedLoginAttempts(1);
        } else user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= 5) {
            user.setStatus(AccountStatus.LOCKED); user.setLockedUntil(now.plusMinutes(10));
        }
    }
    private void clearFailures(UserAccount user) {
        user.setFailedLoginAttempts(0); user.setFirstFailedAt(null); user.setLockedUntil(null);
        if (user.getStatus() == AccountStatus.LOCKED) user.setStatus(AccountStatus.ACTIVE);
    }
    private static UserProfileResponse profileOf(UserAccount u) {
        return new UserProfileResponse(u.getId(), u.getEmail(), u.getName(), u.getAddress(), u.getPhone(), u.getRole(), u.getStatus());
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
