package com.ecommerce.aicustomerservice.service;

import com.ecommerce.aicustomerservice.domain.ConversationSession;
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

/** AI4 (Admin only) — list all sessions, and view one session's full message log. */
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
        ConversationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ResourceNotFound", "Session not found"));
        return session.getMessages().stream().map(ConversationMapper::toLogEntry).toList();
    }
}
