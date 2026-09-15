package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.PasswordResetToken;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.domain.UserStatus;
import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.PasswordResetConfirmRequest;
import com.ecommerce.userservice.dto.PasswordResetRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.dto.UpdateProfileRequest;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.exception.ApiException;
import com.ecommerce.userservice.repository.PasswordResetTokenRepository;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Command Application Service for User Service — specs/iter2/6-cqrs-command-query.md §3.1.
 * Covers U1, U2, U3 (update), U4, U5.
 */
@Service
@Transactional
public class UserCommandService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(10);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserCommandService(UserRepository userRepository,
                               PasswordResetTokenRepository tokenRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** U1 — Register an Account. */
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "EmailAlreadyExists",
                    "An account with this email already exists");
        }
        User user = new User();
        user.setId("USR-" + shortId());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        return UserMapper.toUserResponse(user);
    }

    /**
     * U2 — Login. 5 failed attempts within 10 minutes locks the account (423) until auto-unlock.
     *
     * <p>noRollbackFor is required here: the class-level @Transactional defaults to rolling back
     * on any unchecked exception, and invalidCredentials()/AccountLocked are both ApiException
     * (unchecked). Without this override, the failedLoginAttempts/lockedUntil save() just above
     * each throw would be silently discarded when the transaction rolls back — so the lockout
     * counter would never actually persist and the account would never lock.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> invalidCredentials());

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw invalidCredentials();
        }

        if (user.isCurrentlyLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "AccountLocked",
                    "Account locked after 5 failed login attempts within 10 minutes");
        }

        if (user.getLockedUntil() != null) {
            // Lock window has elapsed - auto-unlock before evaluating this attempt.
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            }
            userRepository.save(user);
            throw invalidCredentials();
        }

        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        String token = jwtService.issueToken(user.getId(), user.getRole().name());
        return new AuthResponse(token, user.getId());
    }

    /** U3 — Update Profile (partial; only non-null fields change). */
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUserOrThrow(userId);
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.address() != null) {
            user.setAddress(request.address());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        userRepository.save(user);
        return UserMapper.toProfileResponse(user);
    }

    /** U5 — Deactivate Account (soft delete). */
    public void deactivate(String userId) {
        User user = getUserOrThrow(userId);
        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);
    }

    /** U4 — Request Password Reset. Always a no-op success from the caller's point of view. */
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setId("PRT-" + shortId());
            resetToken.setUserId(user.getId());
            resetToken.setToken(shortId() + shortId());
            resetToken.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
            resetToken.setUsed(false);
            resetToken.setCreatedAt(Instant.now());
            tokenRepository.save(resetToken);
        });
    }

    /** U4 — Confirm Password Reset. */
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = tokenRepository.findByToken(request.token())
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(UserCommandService::resetTokenInvalid);

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(UserCommandService::resetTokenInvalid);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    private User getUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "User not found"));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "InvalidCredentials", "Invalid email or password");
    }

    private static ApiException resetTokenInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ResetTokenInvalidOrExpired",
                "This reset token has expired or already been used");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
