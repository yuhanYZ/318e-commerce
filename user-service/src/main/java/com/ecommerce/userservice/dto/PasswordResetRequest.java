package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(@NotBlank String email) {
}
