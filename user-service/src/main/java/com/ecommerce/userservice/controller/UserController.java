package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.PasswordResetConfirmRequest;
import com.ecommerce.userservice.dto.PasswordResetRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.dto.UpdateProfileRequest;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.service.UserCommandService;
import com.ecommerce.userservice.service.UserQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** specs/iter2/openapi-contract-v2.yaml paths /users/*. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserCommandService commandService;
    private final UserQueryService queryService;

    public UserController(UserCommandService commandService, UserQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(commandService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(queryService.getProfile(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(Authentication authentication,
                                                                 @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(commandService.updateProfile(authentication.getName(), request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMyAccount(Authentication authentication) {
        commandService.deactivate(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        commandService.requestPasswordReset(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        commandService.confirmPasswordReset(request);
        return ResponseEntity.ok().build();
    }
}
