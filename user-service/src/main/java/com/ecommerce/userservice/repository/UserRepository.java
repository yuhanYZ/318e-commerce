package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.domain.Email;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UserId> {

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
