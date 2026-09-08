package com.ecommerce.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Returned by U3 (get/update profile). Matches the UserProfileResponse schema in specs/openapi-contract.yaml. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String email;
    private String name;
    private String address;
    private String phone;
    private String role;
    private String status;
}
