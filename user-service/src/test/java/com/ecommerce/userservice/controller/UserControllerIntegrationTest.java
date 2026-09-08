package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.AccountStatus;
import com.ecommerce.userservice.domain.PasswordResetToken;
import com.ecommerce.userservice.domain.Role;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.dto.request.PasswordResetConfirmRequest;
import com.ecommerce.userservice.dto.request.PasswordResetRequest;
import com.ecommerce.userservice.dto.request.RegisterRequest;
import com.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.ecommerce.userservice.repository.PasswordResetTokenRepository;
import com.ecommerce.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-first integration tests generated from specs/openapi-contract.yaml for
 * the /users/* endpoints (U1-U5). Each happy path is paired with the edge/error
 * paths the contract defines (400, 401, 409, 423).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTest {

    private static final String VALID_PASSWORD = "P@ssw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- U1: Register ----------

    @Test
    void register_withValidData_returns201AndUserResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("newcustomer@example.com", VALID_PASSWORD);

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("newcustomer@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        persistUser("duplicate@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        RegisterRequest request = new RegisterRequest("duplicate@example.com", VALID_PASSWORD);

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", VALID_PASSWORD);

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withWeakPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("weakpass@example.com", "abc");

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------- U2: Login ----------

    @Test
    void login_withValidCredentials_returns200AndToken() throws Exception {
        persistUser("login-ok@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        LoginRequest request = new LoginRequest("login-ok@example.com", VALID_PASSWORD);

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        persistUser("wrongpass@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        LoginRequest request = new LoginRequest("wrongpass@example.com", "WrongPass123");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withNonexistentEmail_returns401() throws Exception {
        LoginRequest request = new LoginRequest("ghost@example.com", VALID_PASSWORD);

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_afterFiveFailedAttemptsWithin10Minutes_locksAccountAndReturns423() throws Exception {
        persistUser("lockout@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        LoginRequest badRequest = new LoginRequest("lockout@example.com", "WrongPass123");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badRequest)));
        }

        LoginRequest correctRequest = new LoginRequest("lockout@example.com", VALID_PASSWORD);
        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().is(HttpStatus.LOCKED.value()))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    // ---------- U3: View / Update Profile ----------

    @Test
    void getProfile_withValidToken_returns200() throws Exception {
        User user = persistUser("profile@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        String token = loginAndGetToken("profile@example.com", VALID_PASSWORD);

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("profile@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_withValidToken_returns200AndUpdatedFields() throws Exception {
        persistUser("update@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        String token = loginAndGetToken("update@example.com", VALID_PASSWORD);
        UpdateProfileRequest request =
                new UpdateProfileRequest("Jane Doe", "12 King St, Wollongong NSW", "+61 400 000 000");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.address").value("12 King St, Wollongong NSW"))
                .andExpect(jsonPath("$.phone").value("+61 400 000 000"));
    }

    @Test
    void updateProfile_withoutToken_returns401() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane Doe", null, null);

        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- U5: Deactivate Account ----------

    @Test
    void deactivateAccount_withValidToken_returns204AndBlocksFutureLogin() throws Exception {
        persistUser("deactivate@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        String token = loginAndGetToken("deactivate@example.com", VALID_PASSWORD);

        mockMvc.perform(delete("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        LoginRequest loginRequest = new LoginRequest("deactivate@example.com", VALID_PASSWORD);
        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateAccount_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- U4: Reset Password ----------

    @Test
    void requestPasswordReset_forExistingEmail_returns202() throws Exception {
        persistUser("reset@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        PasswordResetRequest request = new PasswordResetRequest("reset@example.com");

        mockMvc.perform(post("/users/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void requestPasswordReset_forUnknownEmail_stillReturns202() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("unknown@example.com");

        mockMvc.perform(post("/users/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void confirmPasswordReset_withValidToken_returns200AndAllowsLoginWithNewPassword() throws Exception {
        User user = persistUser("confirm@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        PasswordResetToken resetToken = passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build());

        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest(resetToken.getToken(), "NewP@ss123");

        mockMvc.perform(post("/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest("confirm@example.com", "NewP@ss123");
        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void confirmPasswordReset_withExpiredToken_returns400() throws Exception {
        User user = persistUser("expired@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        PasswordResetToken resetToken = passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build());

        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest(resetToken.getToken(), "NewP@ss123");

        mockMvc.perform(post("/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void confirmPasswordReset_withAlreadyUsedToken_returns400() throws Exception {
        User user = persistUser("used-token@example.com", VALID_PASSWORD, AccountStatus.ACTIVE);
        PasswordResetToken resetToken = passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(true)
                .build());

        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest(resetToken.getToken(), "NewP@ss123");

        mockMvc.perform(post("/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmPasswordReset_withInvalidToken_returns400() throws Exception {
        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest("not-a-real-token", "NewP@ss123");

        mockMvc.perform(post("/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    // ---------- helpers ----------

    private User persistUser(String email, String rawPassword, AccountStatus status) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.CUSTOMER)
                .status(status)
                .failedLoginAttempts(0)
                .build();
        return userRepository.save(user);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        String response = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
