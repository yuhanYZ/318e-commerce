package com.ecommerce.aicustomerservice.controller;

import com.ecommerce.aicustomerservice.dto.ChatMessageRequest;
import com.ecommerce.aicustomerservice.dto.ChatMessageResponse;
import com.ecommerce.aicustomerservice.dto.MessageLogEntry;
import com.ecommerce.aicustomerservice.dto.SessionPageResponse;
import com.ecommerce.aicustomerservice.dto.SessionStatusResponse;
import com.ecommerce.aicustomerservice.service.ConversationCommandService;
import com.ecommerce.aicustomerservice.service.ConversationQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** specs/iter2/openapi-contract-v2.yaml paths /ai-chat/*. */
@RestController
@RequestMapping("/api/v1/ai-chat")
public class AiChatController {

    private final ConversationCommandService commandService;
    private final ConversationQueryService queryService;

    public AiChatController(ConversationCommandService commandService, ConversationQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ChatMessageResponse sendMessage(@PathVariable String sessionId,
                                            @Valid @RequestBody ChatMessageRequest request,
                                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                            String authorization,
                                            Authentication authentication) {
        return commandService.sendMessage(sessionId, authentication.getName(), request, authorization);
    }

    @PostMapping("/sessions/{sessionId}/escalate")
    public SessionStatusResponse escalate(@PathVariable String sessionId, Authentication authentication) {
        return commandService.escalate(sessionId, authentication.getName());
    }

    @GetMapping("/sessions")
    public SessionPageResponse listSessions(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return queryService.listSessions(page, size);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<MessageLogEntry> getSessionMessages(@PathVariable String sessionId) {
        return queryService.getSessionMessages(sessionId);
    }
}


