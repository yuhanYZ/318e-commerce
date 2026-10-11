package com.ecommerce.aicustomerservice.service;

import com.ecommerce.aicustomerservice.ChatAgent;
import com.ecommerce.aicustomerservice.domain.ConversationMessage;
import com.ecommerce.aicustomerservice.domain.ConversationSession;
import com.ecommerce.aicustomerservice.domain.Sender;
import com.ecommerce.aicustomerservice.domain.SessionId;
import com.ecommerce.aicustomerservice.domain.UserId;
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
 * Command Application Service for AI-Customer Service — specs/iter2/6-cqrs-command-query.md §3.1.
 * AI1/AI2 (sendMessage) and AI3 (escalate).
 *
 * <p>Per the user-stories doc's "Session Lifecycle" note: no dedicated create-session endpoint — a
 * session is lazily created on first message for a client-generated UUID sessionId, and a sessionId
 * that exists but belongs to a different user is answered with 404 (never 403), to avoid leaking
 * session existence. A syntactically-invalid sessionId is likewise 404, not 400 — it can never have
 * been a session anyone owns.
 *
 * <p>The caller's own bearer token is forwarded to the agent so its order-grounding tool is subject
 * to Order Service's ownership check (O2) — the agent can only read the caller's own orders.
 */
@Service
@Transactional
public class ConversationCommandService {

    private final ConversationSessionRepository sessionRepository;
    private final ChatAgent chatAgent;
    private final DomainEventPublisher domainEventPublisher;

    public ConversationCommandService(ConversationSessionRepository sessionRepository,
                                       ChatAgent chatAgent,
                                       DomainEventPublisher domainEventPublisher) {
        this.sessionRepository = sessionRepository;
        this.chatAgent = chatAgent;
        this.domainEventPublisher = domainEventPublisher;
    }

    public ChatMessageResponse sendMessage(String sessionId, String callerId, ChatMessageRequest request,
                                            String bearerToken) {
        SessionId id = sessionIdOrNotFound(sessionId);
        ConversationSession session = loadForCallerOrCreate(id, new UserId(callerId));

        Instant now = Instant.now();
        session.postMessage(Sender.USER, request.content(), null, now);

        // The real implementation (ai.ChatAgentConfig) calls the configured LLM through LangChain4j
        // with the product/order tools bound to this caller's token; the contract test replaces this
        // bean with a deterministic stub. Either way nothing has been persisted yet at this point —
        // the aggregate only holds the user message in memory — so a failure here leaves zero
        // partial state.
        String replyText = chatAgent.reply(id.value(), request.content(), bearerToken);

        ConversationMessage aiMessage = session.postMessage(Sender.AI, replyText, null, Instant.now());

        sessionRepository.save(session);
        domainEventPublisher.publish(session.pullDomainEvents());
        return ConversationMapper.toChatResponse(aiMessage);
    }

    public SessionStatusResponse escalate(String sessionId, String callerId) {
        SessionId id = sessionIdOrNotFound(sessionId);
        ConversationSession session = requireOwnedSession(id, new UserId(callerId));

        session.escalate(Instant.now());
        sessionRepository.save(session);
        domainEventPublisher.publish(session.pullDomainEvents());

        return new SessionStatusResponse(session.getId().value(), session.getStatus().name(),
                session.getUpdatedAt());
    }

    /**
     * Distinguishes "no session with this id" (create it, lazily) from "a session exists but
     * belongs to someone else" (404 — must NOT fall through to creation, or {@code save()} would
     * merge/overwrite the existing row's owner since the id already exists).
     */
    private ConversationSession loadForCallerOrCreate(SessionId id, UserId callerId) {
        Optional<ConversationSession> existing = sessionRepository.findById(id);
        if (existing.isEmpty()) {
            return ConversationSession.start(id, callerId, Instant.now());
        }
        ConversationSession session = existing.get();
        if (!session.getUserId().equals(callerId)) {
            throw sessionNotFound();
        }
        return session;
    }

    /** 404 if missing, and 404 (never 403) if it belongs to someone else — avoids leaking existence. */
    private ConversationSession requireOwnedSession(SessionId id, UserId callerId) {
        ConversationSession session = sessionRepository.findById(id)
                .orElseThrow(ConversationCommandService::sessionNotFound);
        if (!session.getUserId().equals(callerId)) {
            throw sessionNotFound();
        }
        return session;
    }

    private static SessionId sessionIdOrNotFound(String sessionId) {
        try {
            UUID.fromString(sessionId);
            return new SessionId(sessionId);
        } catch (IllegalArgumentException ex) {
            throw sessionNotFound();
        }
    }

    private static ApiException sessionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Session not found");
    }
}
