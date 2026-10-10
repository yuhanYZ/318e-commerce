package com.ecommerce.userservice.config;

import com.ecommerce.userservice.domain.Email;
import com.ecommerce.userservice.domain.PasswordHash;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserId;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.service.DomainEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seeds the single Admin account from app.admin.email/password at startup — no self-service
 * Admin path (specs/iter2/2-user-stories-user-service-v2.md "Roles and Admin Provisioning").
 * The seed is idempotent: it is skipped if that email already exists.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final String ADMIN_ID = "USR-ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher domainEventPublisher;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        DomainEventPublisher domainEventPublisher,
                        @Value("${app.admin.email}") String adminEmail,
                        @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.domainEventPublisher = domainEventPublisher;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        Email email = new Email(adminEmail);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = User.seedAdmin(new UserId(ADMIN_ID), email,
                new PasswordHash(passwordEncoder.encode(adminPassword)), Instant.now());
        userRepository.save(admin);
        domainEventPublisher.publish(admin.pullDomainEvents());
    }
}
