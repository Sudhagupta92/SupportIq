package com.supportiq.controller;

import com.supportiq.dto.ChatRequest;
import com.supportiq.dto.ChatResponse;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing customer support RAG chat endpoint.
 * Accessible to any authenticated user (CUSTOMER or ADMIN).
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    /**
     * Submits a customer inquiry to the RAG chat assistant.
     *
     * @param request chat query payload
     * @param authentication authenticated user principal
     * @return 200 OK with ChatResponse
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {

        String userEmail = (authentication != null) ? authentication.getName() : "anonymous";
        Long userId = resolveUserId(userEmail);

        ChatResponse response = chatService.processChat(userId, userEmail, request);
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId(String email) {
        if (email == null || email.isBlank() || "anonymous".equals(email)) {
            return null;
        }
        return userRepository.findByEmail(email.toLowerCase().trim())
                .map(User::getId)
                .orElse(null);
    }
}
