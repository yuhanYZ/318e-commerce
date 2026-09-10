package com.ecommerce.aicustomerservice.service;

import com.ecommerce.aicustomerservice.ChatAgent;
import com.ecommerce.aicustomerservice.domain.ConversationMessage;
import com.ecommerce.aicustomerservice.domain.ConversationSession;
import com.ecommerce.aicustomerservice.domain.Sender;
import com.ecommerce.aicustomerservice.domain.SessionStatus;
import com.ecommerce.aicustomerservice.dto.ChatMessageRequest;
import com.ecommerce.aicustomerservice.dto.ChatMessageResponse;
import com.ecommerce.aicustomerservice.dto.SessionStatusResponse;
import com.ecommerce.aicustomerservice.exception.ApiException;
import com.ecommerce.aicustomerservice.repository.ConversationSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * AI1/AI2 (sendMessage) and AI3 (escalate). Per the user-stories doc's "Session Lifecycle" note:
 * no dedicated create-session endpoint — a session is lazily created on first message for a
 * client-generated UUID sessionId, and a sessionId that exists but belongs to a different user is
 * answered with 404 (never 403), to avoid leaking session existence. A syntactically-invalid
 * sessionId is likewise 404, not 400 — it can never have been a session anyone owns.
 */
@Service
@Transactional
public class ConversationCommandService {

    private final ConversationSessionRepository sessionRepository;
    private final ChatAgent chatAgent;

    public ConversationCommandService(ConversationSessionRepository sessionRepository, ChatAgent chatAgent) {
        this.sessionRepository = sessionRepository;
        this.chatAgent = chatAgent;
    }

    public ChatMessageResponse sendMessage(String sessionId, String callerId, ChatMessageRequest request) {
        validateSessionIdFormat(sessionId);

        ConversationSession session = loadForCallerOrCreate(sessionId, callerId);

        Instant now = Instant.now();
        ConversationMessage userMessage = new ConversationMessage(UUID.randomUUID().toString());
        userMessage.setSession(session);
        userMessage.setSender(Sender.USER);
        userMessage.setContent(request.content());
        userMessage.setCreatedAt(now);
        session.getMessages().add(userMessage);

        // The real implementation (ai.ChatAgentConfig) calls out to GPT-4o-mini via LangChain4j;
        // the contract test replaces this bean with a deterministic stub. Either way, nothing has
        // been persisted yet at this point, so a failure here leaves zero partial state.
        String replyText = chatAgent.reply(sessionId, request.content());

        ConversationMessage aiMessage = new ConversationMessage(UUID.randomUUID().toString());
        aiMessage.setSession(session);
        aiMessage.setSender(Sender.AI);
        aiMessage.setContent(replyText);
        aiMessage.setCreatedAt(Instant.now());
        session.getMessages().add(aiMessage);

        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        return ConversationMapper.toChatResponse(aiMessage);
    }

    public SessionStatusResponse escalate(String sessionId, String callerId) {
        validateSessionIdFormat(sessionId);

        ConversationSession session = requireOwnedSession(sessionId, callerId);

        session.setStatus(SessionStatus.ESCALATED);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        return new SessionStatusResponse(session.getId(), session.getStatus().name(), session.getUpdatedAt());
    }

    /**
     * Distinguishes "no session with this id" (create it, lazily) from "a session exists but
     * belongs to someone else" (404 — must NOT fall through to creation, or {@code save()} would
     * merge/overwrite the existing row's owner since the id already exists).
     */
    private ConversationSession loadForCallerOrCreate(String sessionId, String callerId) {
        Optional<ConversationSession> existing = sessionRepository.findById(sessionId);
        if (existing.isEmpty()) {
            return startNewSession(sessionId, callerId);
        }
        ConversationSession session = existing.get();
        if (!session.getUserId().equals(callerId)) {
            throw sessionNotFound();
        }
        return session;
    }

    /** 404 if missing, and 404 (never 403) if it belongs to someone else — avoids leaking session existence. */
    private ConversationSession requireOwnedSession(String sessionId, String callerId) {
        ConversationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(ConversationCommandService::sessionNotFound);
        if (!session.getUserId().equals(callerId)) {
            throw sessionNotFound();
        }
        return session;
    }

    private ConversationSession startNewSession(String sessionId, String callerId) {
        ConversationSession session = new ConversationSession(sessionId);
        session.setUserId(callerId);
        session.setStatus(SessionStatus.ACTIVE);
        Instant now = Instant.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    private void validateSessionIdFormat(String sessionId) {
        try {
            UUID.fromString(sessionId);
        } catch (IllegalArgumentException ex) {
            throw sessionNotFound();
        }
    }

    private static ApiException sessionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Session not found");
    }
}
