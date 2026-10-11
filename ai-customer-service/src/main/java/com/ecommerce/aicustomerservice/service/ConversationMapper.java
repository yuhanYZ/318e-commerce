package com.ecommerce.aicustomerservice.service;

import com.ecommerce.aicustomerservice.domain.ConversationMessage;
import com.ecommerce.aicustomerservice.domain.ConversationSession;
import com.ecommerce.aicustomerservice.dto.ChatMessageResponse;
import com.ecommerce.aicustomerservice.dto.MessageLogEntry;
import com.ecommerce.aicustomerservice.dto.SessionSummaryResponse;

/** Maps the aggregate onto the API contract's DTOs — specs/iter2/openapi-contract-v2.yaml. */
final class ConversationMapper {

    private ConversationMapper() {
    }

    static ChatMessageResponse toChatResponse(ConversationMessage message) {
        return new ChatMessageResponse(
                message.getSession().getId().value(),
                message.getId(),
                message.getSender().name(),
                message.getContent(),
                message.getCreatedAt());
    }

    static SessionSummaryResponse toSummary(ConversationSession session) {
        return new SessionSummaryResponse(
                session.getId().value(),
                session.getUserId().value(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    static MessageLogEntry toLogEntry(ConversationMessage message) {
        return new MessageLogEntry(
                message.getId(),
                message.getSender().name(),
                message.getContent(),
                message.getCreatedAt());
    }
}
