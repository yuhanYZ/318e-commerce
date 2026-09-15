package com.ecommerce.userservice.dto;

public record UserProfileResponse(
        String id,
        String email,
        String name,
        String address,
        String phone,
        String role,
        String status
) {
}
