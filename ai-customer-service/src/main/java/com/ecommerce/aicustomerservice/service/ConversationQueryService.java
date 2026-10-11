package com.ecommerce.aicustomerservice.service;

import com.ecommerce.aicustomerservice.domain.ConversationSession;
import com.ecommerce.aicustomerservice.domain.SessionId;
import com.ecommerce.aicustomerservice.dto.MessageLogEntry;
import com.ecommerce.aicustomerservice.dto.SessionPageResponse;
import com.ecommerce.aicustomerservice.exception.ApiException;
import com.ecommerce.aicustomerservice.repository.ConversationSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Query Application Service for AI-Customer Service — specs/iter2/6-cqrs-command-query.md §3.2.
 * AI4 (Admin only): list all sessions, and view one session's full message log.
 */
@Service
@Transactional(readOnly = true)
public class ConversationQueryService {

    private final ConversationSessionRepository sessionRepository;

    public ConversationQueryService(ConversationSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public SessionPageResponse listSessions(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ConversationSession> result = sessionRepository.findAll(pageable);
        return new SessionPageResponse(
                result.getContent().stream().map(ConversationMapper::toSummary).toList(),
                result.getNumber(),
                result.getTotalElements());
    }

    public List<MessageLogEntry> getSessionMessages(String sessionId) {
        SessionId id;
        try {
            id = new SessionId(sessionId);
        } catch (IllegalArgumentException ex) {
            throw sessionNotFound();
        }
        ConversationSession session = sessionRepository.findById(id)
                .orElseThrow(ConversationQueryService::sessionNotFound);
        return session.getMessages().stream().map(ConversationMapper::toLogEntry).toList();
    }

    private static ApiException sessionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Session not found");
    }
}
