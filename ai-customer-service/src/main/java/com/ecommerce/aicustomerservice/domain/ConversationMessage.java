package com.ecommerce.aicustomerservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * {@code ConversationMessage} entity, composed inside {@code ConversationSession} (not an
 * independent aggregate) — specs/iter2/3-domain-models-ai-customer-service-ddd.md §2.1.1.
 */
@Entity
@Table(name = "conversation_message")
public class ConversationMessage {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ConversationSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sender sender;

    @Column(nullable = false, length = 4000)
    private String content;

    /** JSON snapshot of cross-service data (Product/Order) the AI used to answer; nullable, audit-only (AI4). */
    @Column(name = "referenced_data", length = 4000)
    private String referencedData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationMessage() {
        // JPA
    }

    public ConversationMessage(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public ConversationSession getSession() {
        return session;
    }

    public void setSession(ConversationSession session) {
        this.session = session;
    }

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReferencedData() {
        return referencedData;
    }

    public void setReferencedData(String referencedData) {
        this.referencedData = referencedData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
