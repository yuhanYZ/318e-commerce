package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.AccountStatus;
import com.ecommerce.userservice.domain.PasswordResetToken;
import com.ecommerce.userservice.domain.Role;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.dto.request.PasswordResetConfirmRequest;
import com.ecommerce.userservice.dto.request.PasswordResetRequest;
import com.ecommerce.userservice.dto.request.RegisterRequest;
import com.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.ecommerce.userservice.dto.response.AuthResponse;
import com.ecommerce.userservice.dto.response.UserProfileResponse;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.exception.AccountLockedException;
import com.ecommerce.userservice.exception.EmailAlreadyExistsException;
import com.ecommerce.userservice.exception.InvalidCredentialsException;
import com.ecommerce.userservice.exception.InvalidTokenException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.PasswordResetTokenRepository;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Business logic for the User Service. Implements U1-U5 from
 * specs/2-user-stories.md, orchestrating the {@link UserRepository} /
 * {@link PasswordResetTokenRepository} and delegating token issuance to
 * {@link JwtTokenProvider}, per the Service Layer responsibilities in
 * specs/1-technical-architecture.md.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.failure-window-minutes:10}")
    private long failureWindowMinutes;

    @Value("${app.security.lockout-duration-minutes:15}")
    private long lockoutDurationMinutes;

    @Value("${app.security.reset-token-expiry-minutes:30}")
    private long resetTokenExpiryMinutes;

    /** U1: Register an Account. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        user = userRepository.save(user);

        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    /** U2: Login. */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        autoUnlockIfExpired(user);

        if (user.getStatus() == AccountStatus.LOCKED) {
            throw new AccountLockedException(
                    "Account locked after too many failed login attempts. Please try again later.");
        }

        if (user.getStatus() == AccountStatus.DEACTIVATED
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            if (user.getStatus() != AccountStatus.DEACTIVATED) {
                registerFailedAttempt(user);
            }
            throw new InvalidCredentialsException("Invalid email or password");
        }

        user.setFailedLoginAttempts(0);
        user.setFirstFailedAttemptAt(null);
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getId());
    }

    /** U3: View Profile. */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        User user = findActiveOrLockedUserById(userId);
        return toProfileResponse(user);
    }

    /** U3: Update Profile - only non-null fields are applied. */
    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = findActiveOrLockedUserById(userId);

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        user = userRepository.save(user);

        return toProfileResponse(user);
    }

    /** U5: Deactivate Account (soft delete) - order history referencing this user id remains intact. */
    @Transactional
    public void deactivateAccount(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setStatus(AccountStatus.DEACTIVATED);
        userRepository.save(user);
    }

    /**
     * U4: Request a password reset. Always completes silently (202, no error) whether
     * or not the email is registered, to avoid leaking which emails are registered.
     */
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.getEmail());
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .token(UUID.randomUUID().toString())
                    .expiresAt(LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);
            // NOTE: in production this token would be emailed to the user, never returned via the API.
        });
    }

    /** U4: Confirm a password reset with the issued token and a new password. */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid"));

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Reset token is expired or has already been used");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setFirstFailedAttemptAt(null);
        if (user.getStatus() == AccountStatus.LOCKED) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setLockedUntil(null);
        }
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    // ---------- helpers ----------

    private User findActiveOrLockedUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void autoUnlockIfExpired(User user) {
        if (user.getStatus() == AccountStatus.LOCKED
                && user.getLockedUntil() != null
                && LocalDateTime.now().isAfter(user.getLockedUntil())) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setFailedLoginAttempts(0);
            user.setFirstFailedAttemptAt(null);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private void registerFailedAttempt(User user) {
        LocalDateTime now = LocalDateTime.now();
        boolean windowExpired = user.getFirstFailedAttemptAt() == null
                || Duration.between(user.getFirstFailedAttemptAt(), now).toMinutes() >= failureWindowMinutes;

        if (windowExpired) {
            user.setFirstFailedAttemptAt(now);
            user.setFailedLoginAttempts(1);
        } else {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        }

        if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
            user.setStatus(AccountStatus.LOCKED);
            user.setLockedUntil(now.plusMinutes(lockoutDurationMinutes));
        }

        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAddress(),
                user.getPhone(),
                user.getRole().name(),
                user.getStatus().name());
    }
}
