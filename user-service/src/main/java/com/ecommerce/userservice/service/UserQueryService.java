package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.exception.ApiException;
import com.ecommerce.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query Application Service for User Service — specs/iter2/6-cqrs-command-query.md §3.2.
 * Covers U3a (view).
 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "User not found"));
        return UserMapper.toProfileResponse(user);
    }
}
