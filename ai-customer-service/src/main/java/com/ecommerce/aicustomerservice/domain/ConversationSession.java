package com.ecommerce.aicustomerservice.domain;

import com.ecommerce.aicustomerservice.exception.ApiException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code ConversationSessionAggregate} (root: ConversationSession) —
 * specs/iter2/3-domain-models-ai-customer-service-ddd.md §2.3.1. {@code id} is the client-generated
 * UUID sessionId: there is no dedicated create-session endpoint, so the session is created lazily
 * on the first message.
 *
 * <p><b>Invariants</b>: (1) transitions are restricted to ACTIVE → ESCALATED and
 * {ACTIVE, ESCALATED} → CLOSED; (2) CLOSED is terminal — no message may be posted once closed.
 */
@Entity
@Table(name = "conversation_session")
public class ConversationSession {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id", nullable = false))
    private SessionId id;

    @Convert(converter = UserId.JpaConverter.class)
    @Column(name = "user_id", nullable = false)
    private UserId userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ConversationMessage> messages = new ArrayList<>();

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected ConversationSession() {
        // JPA
    }

    /** AI1/AI2 — begin a session (lazily, on the first message). Emits {@code SessionStarted}. */
    public static ConversationSession start(SessionId id, UserId userId, Instant now) {
        ConversationSession session = new ConversationSession();
        session.id = id;
        session.userId = userId;
        session.status = SessionStatus.ACTIVE;
        session.createdAt = now;
        session.updatedAt = now;
        session.raise(new SessionStarted(id, userId, now));
        return session;
    }

    /**
     * AI1/AI2 — post a message from either party. Emits {@code MessageSent}. Rejected with 409 once
     * the session is CLOSED (invariant 2 — CLOSED is terminal).
     */
    public ConversationMessage postMessage(Sender sender, String content, String referencedData, Instant now) {
        if (status == SessionStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "SessionStateViolation",
                    "This conversation is closed");
        }
        ConversationMessage message = new ConversationMessage(UUID.randomUUID().toString());
        message.setSession(this);
        message.setSender(sender);
        message.setContent(content);
        message.setReferencedData(referencedData);
        message.setCreatedAt(now);
        this.messages.add(message);
        this.updatedAt = now;
        raise(new MessageSent(id, message.getId(), sender, now));
        return message;
    }

    /**
     * AI3 — escalate to a human agent. Emits {@code SessionEscalated}.
     *
     * <p>Escalating an already-escalated session is a no-op rather than an error (it is already in
     * the requested state); CLOSED, which no endpoint produces in this milestone, is rejected
     * because it is terminal per invariant 1.
     */
    public SessionEscalated escalate(Instant now) {
        if (status == SessionStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "SessionStateViolation",
                    "A closed conversation cannot be escalated");
        }
        if (status == SessionStatus.ESCALATED) {
            return null;
        }
        this.status = SessionStatus.ESCALATED;
        this.updatedAt = now;
        SessionEscalated event = new SessionEscalated(id, now);
        raise(event);
        return event;
    }

    /**
     * Ends the conversation. Emits {@code SessionClosed}. Reserved for future use — no endpoint in
     * this milestone closes a session (the user-stories doc's Session Lifecycle note).
     */
    public SessionClosed close(Instant now) {
        this.status = SessionStatus.CLOSED;
        this.updatedAt = now;
        SessionClosed event = new SessionClosed(id, now);
        raise(event);
        return event;
    }

    /** Drains the events raised by the behaviors above so the command service can dispatch them. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    public SessionId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
