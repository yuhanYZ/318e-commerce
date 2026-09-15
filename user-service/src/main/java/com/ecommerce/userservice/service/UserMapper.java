package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.dto.UserResponse;

final class UserMapper {

    private UserMapper() {
    }

    static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    static UserProfileResponse toProfileResponse(User user) {
        String status = user.isCurrentlyLocked() ? "LOCKED" : user.getStatus().name();
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getName(), user.getAddress(),
                user.getPhone(), user.getRole().name(), status);
    }
}
