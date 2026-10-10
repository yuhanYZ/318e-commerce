package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.domain.PasswordResetToken;
import com.ecommerce.userservice.domain.ResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(ResetToken token);
}
