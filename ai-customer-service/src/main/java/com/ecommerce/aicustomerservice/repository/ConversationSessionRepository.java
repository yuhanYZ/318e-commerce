package com.ecommerce.aicustomerservice.repository;

import com.ecommerce.aicustomerservice.domain.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {
}
