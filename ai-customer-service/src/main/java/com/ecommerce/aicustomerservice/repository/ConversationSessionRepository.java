package com.ecommerce.aicustomerservice.repository;

import com.ecommerce.aicustomerservice.domain.ConversationSession;
import com.ecommerce.aicustomerservice.domain.SessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, SessionId> {
}
