package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.AccountStatus;
import com.ecommerce.userservice.domain.AuthenticationService;
import com.ecommerce.userservice.domain.ContactInfo;
import com.ecommerce.userservice.domain.Email;
import com.ecommerce.userservice.domain.PasswordHash;
import com.ecommerce.userservice.domain.PasswordResetService;
import com.ecommerce.userservice.domain.PasswordResetToken;
import com.ecommerce.userservice.domain.ResetToken;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserId;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Command Application Service for User Service — specs/iter2/6-cqrs-command-query.md §3.1.
 * Covers U1, U2, U3b (update), U4, U5.
 *
 * <p>Every state change goes through a {@link User} behavior or a
 * {@link PasswordResetToken} behavior; this class only orchestrates (load → behave → persist →
 * dispatch the events the aggregate raised), which is what the layered architecture in
 * specs/iter2/1-technical-architecture-v2.md asks of the service layer.
 */
@Service
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final DomainEventPublisher domainEventPublisher;

    public UserCommandService(UserRepository userRepository,
                              PasswordResetTokenRepository tokenRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              AuthenticationService authenticationService,
                              PasswordResetService passwordResetService,
                              DomainEventPublisher domainEventPublisher) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** U1 — Register an Account. */
    public UserResponse register(RegisterRequest request) {
        Email email = emailOrThrow(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EmailAlreadyExists",
                    "An account with this email already exists");
        }
        Instant now = Instant.now();
        User user = User.register(new UserId("USR-" + shortId()), email,
                new PasswordHash(passwordEncoder.encode(request.password())), null, ContactInfo.empty(), now);
        userRepository.save(user);
        domainEventPublisher.publish(user.pullDomainEvents());
        return UserMapper.toUserResponse(user);
    }

    /**
     * U2 — Login. 5 failed attempts within 10 minutes locks the account (423) until auto-unlock.
     *
     * <p>noRollbackFor is required here: the class-level @Transactional defaults to rolling back on
     * any unchecked exception, and failedCredentials()/AccountLocked are both ApiException
     * (unchecked). Without this override, the failed-attempt save() just above each throw would be
     * silently discarded when the transaction rolls back — so the lockout counter would never
     * actually persist and the account would never lock.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        Email email = emailOrThrow(request.email());
        User user = userRepository.findByEmail(email).orElseThrow(UserCommandService::invalidCredentials);

        if (user.getStatus() == AccountStatus.DEACTIVATED) {
            throw invalidCredentials();
        }

        Instant now = Instant.now();
        if (user.isCurrentlyLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "AccountLocked",
                    "Account locked after 5 failed login attempts within 10 minutes");
        }
        user.unlockIfExpired(now);

        if (!authenticationService.verifyCredentials(user, request.password())) {
            authenticationService.recordFailedAttempt(user, now);
            userRepository.save(user);
            domainEventPublisher.publish(user.pullDomainEvents());
            throw invalidCredentials();
        }

        authenticationService.resetFailureCount(user);
        user.recordSuccessfulLogin(now);
        userRepository.save(user);
        domainEventPublisher.publish(user.pullDomainEvents());
        return new AuthResponse(jwtService.issueToken(user.getId().value(), user.getRole().name()),
                user.getId().value());
    }

    /** U3b — Update Profile (partial; only non-null fields change). */
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUserOrThrow(userId);
        ContactInfo current = user.getContact() == null ? ContactInfo.empty() : user.getContact();
        ContactInfo contact = null;
        if (request.address() != null || request.phone() != null) {
            contact = new ContactInfo(
                    request.address() != null ? request.address() : current.address(),
                    request.phone() != null ? request.phone() : current.phone());
        }
        user.updateProfile(request.name(), contact, Instant.now());
        userRepository.save(user);
        domainEventPublisher.publish(user.pullDomainEvents());
        return UserMapper.toProfileResponse(user);
    }

    /** U5 — Deactivate Account (soft delete). */
    public void deactivate(String userId) {
        User user = getUserOrThrow(userId);
        user.deactivate(Instant.now());
        userRepository.save(user);
        domainEventPublisher.publish(user.pullDomainEvents());
    }

    /**
     * U4 — Request Password Reset. Always a no-op success from the caller's point of view: an
     * unknown (or even unparseable) email must not be distinguishable from a known one, so this
     * endpoint has to avoid enumeration. The reset token is never returned over the API — it would
     * go out by email in a real system.
     */
    public void requestPasswordReset(PasswordResetRequest request) {
        Email email;
        try {
            email = new Email(request.email());
        } catch (IllegalArgumentException ex) {
            return;
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken resetToken = passwordResetService.generateToken(user.getId(), Instant.now());
            tokenRepository.save(resetToken);
            domainEventPublisher.publish(resetToken.pullDomainEvents());
        });
    }

    /** U4 — Confirm Password Reset. */
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Instant now = Instant.now();
        PasswordResetToken resetToken;
        try {
            resetToken = tokenRepository.findByToken(new ResetToken(request.token()))
                    .orElseThrow(UserCommandService::resetTokenInvalid);
        } catch (IllegalArgumentException ex) {
            throw resetTokenInvalid();
        }
        if (!passwordResetService.validateToken(resetToken, now)) {
            throw resetTokenInvalid();
        }
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(UserCommandService::resetTokenInvalid);

        resetToken.consume(now);
        user.changePassword(new PasswordHash(passwordEncoder.encode(request.newPassword())));

        userRepository.save(user);
        tokenRepository.save(resetToken);
        domainEventPublisher.publish(resetToken.pullDomainEvents());
    }

    private User getUserOrThrow(String userId) {
        return userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "User not found"));
    }

    private static Email emailOrThrow(String rawEmail) {
        try {
            return new Email(rawEmail);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ValidationFailed", "Not a valid email address");
        }
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
