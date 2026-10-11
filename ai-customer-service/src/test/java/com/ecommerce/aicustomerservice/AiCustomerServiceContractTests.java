package com.ecommerce.aicustomerservice;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 (Contract-First Test Generation) — AI-Customer Service.
 *
 * Source of truth: specs/iter2/openapi-contract-v2.yaml (paths /ai-chat/*),
 * specs/iter2/4-api-endpoints-v2.md §8, specs/iter2/2-user-stories-ai-customer-service-v2.md
 * (Session Lifecycle: client-generated UUID sessionId, lazy-created on first message),
 * specs/iter2/3-domain-models-ai-customer-service-ddd.md.
 *
 * The real AI1/AI2 flow calls GPT-4o-mini via LangChain4j. Per
 * 1-technical-architecture-v2.md's testing strategy, this is replaced in tests with a stubbed
 * `ChatAgent` bean so the contract can be verified deterministically and without network access
 * or an API key.
 *
 * REQUIRED OF PHASE 4: define a `ChatAgent` interface and route the real LangChain4j call through
 * an implementation of it, so this test's `@Primary` stub bean can override it. The interface also
 * carries the caller's bearer token (`reply(sessionId, userMessage, bearerToken)`) so the agent's
 * order-grounding tool stays subject to Order Service's ownership check; the tool calls themselves
 * are not exercised by this stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiCustomerServiceContractTests {

    private static final String JWT_SECRET =
            "dev-only-please-override-318-capstone-shared-hs256-secret-32bytes-min";

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class StubChatAgentConfig {
        @Bean
        @Primary
        ChatAgent testChatAgent() {
            return (sessionId, userMessage, bearerToken) -> "This is a stubbed AI reply for testing.";
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

    private static String messageBody(String content) {
        return """
                {"content":"%s"}
                """.formatted(content);
    }

    // ---------------------------------------------------------------
    // AI1/AI2 — Ask a Question (POST /ai-chat/sessions/{sessionId}/messages)
    // ---------------------------------------------------------------

    @Test
    void sendChatMessage_newSession_isLazilyCreated_returns200AiReply() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + token("USR-CHAT-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("Where is my order ORD-5001?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.sender").value("AI"));
    }

    @Test
    void sendChatMessage_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("Hello?")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendChatMessage_sessionBelongsToAnotherUser_returns404() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String ownerToken = token("USR-CHAT-2", "CUSTOMER");
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("First message, creates the session")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + token("USR-CHAT-3", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("Trying to hijack someone else's session")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ResourceNotFound"));
    }

    @Test
    void sendChatMessage_notAValidUuid_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", "not-a-uuid")
                        .header("Authorization", "Bearer " + token("USR-CHAT-4", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("Hi")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // AI3 — Escalate to Human Agent (POST /ai-chat/sessions/{sessionId}/escalate)
    // ---------------------------------------------------------------

    @Test
    void escalateSession_existingSession_returns200Escalated() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String userToken = token("USR-CHAT-5", "CUSTOMER");
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("I need a human")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/escalate", sessionId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));
    }

    @Test
    void escalateSession_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/escalate", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token("USR-CHAT-6", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void escalateSession_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/escalate", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // AI4 — Review AI Conversation Logs (GET /ai-chat/sessions, GET .../messages, Admin only)
    // ---------------------------------------------------------------

    @Test
    void listSessions_admin_returns200PageResponse() throws Exception {
        mockMvc.perform(get("/api/v1/ai-chat/sessions")
                        .header("Authorization", "Bearer " + token("USR-ADMIN-1", "ADMIN"))
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listSessions_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/ai-chat/sessions")
                        .header("Authorization", "Bearer " + token("USR-CHAT-7", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("Forbidden"));
    }

    @Test
    void listSessions_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai-chat/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSessionMessages_admin_returns200Array() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + token("USR-CHAT-8", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody("Log this conversation")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ai-chat/sessions/{sessionId}/messages", sessionId)
                        .header("Authorization", "Bearer " + token("USR-ADMIN-2", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSessionMessages_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/ai-chat/sessions/{sessionId}/messages", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token("USR-ADMIN-3", "ADMIN")))
                .andExpect(status().isNotFound());
    }
}
