package com.ecommerce.userservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 (Contract-First Test Generation) — User Service.
 *
 * Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /users/*),
 * specs/iter2/4-api-endpoints-v2.md §8 (error catalog),
 * specs/iter2/2-user-stories-user-service-v2.md (U1-U5).
 *
 * These tests are intentionally black-box (raw JSON + JsonPath) and do not reference any
 * main-code class, so they compile independently of the Phase 4 implementation. At the end
 * of Phase 3 they are EXPECTED TO FAIL (endpoints don't exist yet -> 404) — Phase 4's job is
 * to make them pass without editing this file's assertions.
 *
 * JWT tokens are self-minted here with the same shared secret User/every other service reads
 * from `app.jwt.secret` (see application.yml), so protected endpoints can be exercised without
 * depending on the (not-yet-implemented) login flow issuing a real token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserServiceContractTests {

    // Must be byte-identical to the default in application.yml (app.jwt.secret) for every service.
    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

    private static String token(String userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /**
     * Registers a brand-new user via the real endpoint and self-mints a token for that user's
     * REAL id. Endpoints under /users/me look the caller up by id, so a token minted for an
     * arbitrary/never-registered id (e.g. "USR-TEST-1") would 404 once Phase 4 is implemented —
     * this helper is what makes those "happy path" tests actually reflect a valid precondition.
     */
    private String registerAndSelfMintToken(String role) throws Exception {
        String email = uniqueEmail();
        String response = mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "P@ssw0rd123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        return token(id, role);
    }

    private static String registerBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    // ---------------------------------------------------------------
    // U1 — Register (POST /users/register)
    // ---------------------------------------------------------------

    @Test
    void register_happyPath_returns201WithCustomerRole() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "P@ssw0rd123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_missingPassword_returns400ValidationFailed() throws Exception {
        String body = """
                {"email":"%s"}
                """.formatted(uniqueEmail());
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationFailed"));
    }

    @Test
    void register_duplicateEmail_returns409EmailAlreadyExists() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "P@ssw0rd123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "AnotherPass1!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EmailAlreadyExists"));
    }

    // ---------------------------------------------------------------
    // U2 — Login (POST /users/login)
    // ---------------------------------------------------------------

    @Test
    void login_happyPath_returns200WithToken() throws Exception {
        String email = uniqueEmail();
        String password = "P@ssw0rd123";
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401InvalidCredentials() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "P@ssw0rd123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "WrongPassword!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("InvalidCredentials"));
    }

    @Test
    void login_after5FailedAttempts_locksAccountEvenWithCorrectPassword() throws Exception {
        String email = uniqueEmail();
        String password = "P@ssw0rd123";
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody(email, "WrongPassword!")));
        }

        // 6th attempt, even with the CORRECT password, must be rejected while locked.
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AccountLocked"));
    }

    // ---------------------------------------------------------------
    // U3 — View / Update Profile (GET, PUT /users/me)
    // ---------------------------------------------------------------

    @Test
    void getMyProfile_happyPath_returns200() throws Exception {
        String userToken = registerAndSelfMintToken("CUSTOMER");
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void getMyProfile_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMyProfile_happyPath_returns200() throws Exception {
        String userToken = registerAndSelfMintToken("CUSTOMER");
        String body = """
                {"name":"Jane Doe","address":"12 King St, Wollongong NSW","phone":"+61 400 000 000"}
                """;
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void updateMyProfile_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // U5 — Deactivate Account (DELETE /users/me)
    // ---------------------------------------------------------------

    @Test
    void deactivateMyAccount_happyPath_returns204() throws Exception {
        String userToken = registerAndSelfMintToken("CUSTOMER");
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateMyAccount_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAccount_thenLogin_returns401InvalidCredentials() throws Exception {
        String email = uniqueEmail();
        String password = "P@ssw0rd123";
        String userId = mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Deactivate via a self-minted token for the registered user id (extracted from the
        // register response by Phase 4; this test parses "id" out of the JSON body).
        String id = com.jayway.jsonpath.JsonPath.read(userId, "$.id");
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token(id, "CUSTOMER")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("InvalidCredentials"));
    }

    // ---------------------------------------------------------------
    // U4 — Password Reset (POST /users/password-reset/request, /confirm)
    // ---------------------------------------------------------------

    @Test
    void requestPasswordReset_knownEmail_returns202() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "P@ssw0rd123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
    }

    @Test
    void requestPasswordReset_unknownEmail_alsoReturns202_toAvoidEnumeration() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted());
    }

    @Test
    void confirmPasswordReset_invalidToken_returns400ResetTokenInvalidOrExpired() throws Exception {
        String body = """
                {"token":"not-a-real-token","newPassword":"NewP@ss123"}
                """;
        mockMvc.perform(post("/api/v1/users/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ResetTokenInvalidOrExpired"));
    }

    // NOTE: the happy path for password-reset/confirm is deliberately not covered here — the
    // API never returns the raw reset token (it would go out via email in a real system), so a
    // pure black-box HTTP test cannot obtain a valid token. If Phase 4 wants this path covered,
    // add a `@Autowired PasswordResetTokenRepository` (or a test-only introspection endpoint)
    // to fetch the token after calling /password-reset/request.
}
