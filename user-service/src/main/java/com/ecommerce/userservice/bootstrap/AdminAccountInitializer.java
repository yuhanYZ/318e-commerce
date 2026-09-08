package com.ecommerce.userservice.bootstrap;

import com.ecommerce.userservice.domain.AccountStatus;
import com.ecommerce.userservice.domain.Role;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a default ADMIN account on startup if one doesn't already exist.
 * <p>
 * Nothing in U1-U5 (specs/2-user-stories.md) exposes a way to register as
 * ADMIN - RegisterRequest has no role field, and UserService.register()
 * always assigns Role.CUSTOMER (correctly - a public "sign up as admin"
 * endpoint would be a real vulnerability). But P4 (product-service),
 * I3/I4 (inventory-service), and PAY4 (payment-service) all require an
 * ADMIN-authenticated caller, and with no seed mechanism at all none of that
 * functionality could ever be exercised or demoed. This is the standard
 * Spring Boot pattern for that: a CommandLineRunner that seeds one known
 * admin account, gated by app.admin.default-email/default-password (see
 * application.yml - local development defaults, override via environment
 * variables before any real deployment, same as app.jwt.secret).
 * <p>
 * Idempotent: checks existsByEmail first, so restarting the service never
 * creates a duplicate or errors on the unique email constraint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAccountInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-email}")
    private String defaultAdminEmail;

    @Value("${app.admin.default-password}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        String email = defaultAdminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
        userRepository.save(admin);

        log.info("Seeded default ADMIN account email={} - log in via POST /users/login like any other " +
                "account (password is set from app.admin.default-password in application.yml)", email);
    }
}
