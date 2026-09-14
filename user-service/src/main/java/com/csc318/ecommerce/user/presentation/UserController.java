package com.csc318.ecommerce.user.presentation;

import com.csc318.ecommerce.user.presentation.UserDtos.*;
import com.csc318.ecommerce.user.service.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserApplicationService service;
    public UserController(UserApplicationService service) { this.service = service; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) { return service.register(request); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }

    @GetMapping("/me")
    public UserProfileResponse profile(Authentication auth) { return service.profile(auth.getName()); }

    @PutMapping("/me")
    public UserProfileResponse update(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(auth.getName(), request);
    }

    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(Authentication auth) { service.deactivate(auth.getName()); }

    @PostMapping("/password-reset/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody PasswordResetRequest request) { service.requestReset(request.email()); }

    @PostMapping("/password-reset/confirm")
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) { service.confirmReset(request); }
}
