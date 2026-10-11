package com.ecommerce.aicustomerservice;

import dev.ai4j.openai4j.OpenAiHttpException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the error-masking defect found during manual end-to-end testing.
 *
 * <p>Previously a failing model call was answered as HTTP 401
 * {@code {"code":"Unauthorized","message":"Missing or invalid authentication token"}} — impossible
 * to tell apart from a bad token — because the provider exception escaped to Spring's {@code /error}
 * dispatch and that internal dispatch was rejected by the security chain (the JWT filter is a
 * {@code OncePerRequestFilter} and skips ERROR dispatches). The real cause (an invalid API key, in
 * the recorded run) never reached the client.
 *
 * <p>With the fix the caller gets 502 plus the provider's own message, and — per the documented
 * invariant in {@code ConversationCommandService} — a failed model call leaves no partial session
 * behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiCustomerServiceModelFailureTests {

    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class ThrowingChatAgentConfig {
        @Bean
        @Primary
        ChatAgent throwingChatAgent() {
            return (sessionId, userMessage, bearerToken) -> {
                throw new OpenAiHttpException(401,
                        "{\"error\":{\"message\":\"Authentication Fails, Your api key: z is invalid\","
                                + "\"type\":\"authentication_error\"}}");
            };
        }
    }

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

    @Test
    void modelCallFailure_isReportedAs502WithProviderMessage_notMaskedAs401() throws Exception {
        String sessionId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + token("USR-MODEL-FAIL", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"What products do you have?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ModelCallFailed"))
                .andExpect(jsonPath("$.message").value(containsString("Your api key: z is invalid")));
    }

    @Test
    void modelCallFailure_leavesNoPartiallyCreatedSession() throws Exception {
        String sessionId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                .header("Authorization", "Bearer " + token("USR-MODEL-FAIL-2", "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"));

        mockMvc.perform(get("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + token("USR-ADMIN", "ADMIN")))
                .andExpect(status().isNotFound());
    }
}
