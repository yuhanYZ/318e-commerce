package com.ecommerce.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Returned by U1 (register). Matches the UserResponse schema in specs/openapi-contract.yaml. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String role;
}
