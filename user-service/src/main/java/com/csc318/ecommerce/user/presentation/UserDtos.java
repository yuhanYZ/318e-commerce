package com.csc318.ecommerce.user.presentation;

import com.csc318.ecommerce.user.domain.AccountStatus;
import com.csc318.ecommerce.user.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record AuthResponse(String token, String userId) {}
    public record UserResponse(String id, String email, Role role) {}
    public record UserProfileResponse(String id, String email, String name, String address, String phone,
                                      Role role, AccountStatus status) {}
    public record UpdateProfileRequest(String name, String address, String phone) {}
    public record PasswordResetRequest(@NotBlank @Email String email) {}
    public record PasswordResetConfirmRequest(@NotBlank String token,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String newPassword) {}
}
