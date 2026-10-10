package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.ContactInfo;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.dto.UserResponse;

/** Maps aggregates onto the API contract's DTOs — specs/iter2/openapi-contract-v2.yaml components/schemas. */
final class UserMapper {

    private UserMapper() {
    }

    static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId().value(), user.getEmail().value(), user.getRole().name());
    }

    static UserProfileResponse toProfileResponse(User user) {
        ContactInfo contact = user.getContact();
        return new UserProfileResponse(
                user.getId().value(),
                user.getEmail().value(),
                user.getName(),
                contact == null ? null : contact.address(),
                contact == null ? null : contact.phone(),
                user.getRole().name(),
                user.effectiveStatus().name());
    }
}
