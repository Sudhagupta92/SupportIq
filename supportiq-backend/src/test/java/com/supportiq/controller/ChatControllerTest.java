package com.supportiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.dto.ChatRequest;
import com.supportiq.dto.ChatResponse;
import com.supportiq.dto.ChatSource;
import com.supportiq.exception.ChatAssistantBusyException;
import com.supportiq.exception.ChatModelUnavailableException;
import com.supportiq.exception.ChatRateLimitException;
import com.supportiq.exception.ChatServiceException;
import com.supportiq.exception.ChatTimeoutException;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.JwtService;
import com.supportiq.service.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private VectorStore vectorStore;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User customer = User.builder()
                .name("Customer User")
                .email("customer@example.com")
                .password("Password@123")
                .role(Role.CUSTOMER)
                .build();
        customer = userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        User admin = User.builder()
                .name("Admin User")
                .email("admin@example.com")
                .password("Password@123")
                .role(Role.ADMIN)
                .build();
        admin = userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);
    }

    @Test
    @DisplayName("Unauthenticated request to POST /api/chat must return HTTP 401")
    void testChat_Unauthenticated_Returns401() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .message("What is your refund policy?")
                .build();

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated CUSTOMER request to POST /api/chat should return 200 with answer and sources")
    void testChat_CustomerRole_Success() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .message("What is your refund policy?")
                .build();

        ChatResponse mockResponse = ChatResponse.builder()
                .answer("Refunds are processed within 14 days.")
                .answered(true)
                .sources(List.of(ChatSource.builder()
                        .filename("Refund_Policy.pdf")
                        .pageNumber(2)
                        .score(0.74)
                        .build()))
                .build();

        when(chatService.processChat(any(), eq("customer@example.com"), any(ChatRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.answer").value("Refunds are processed within 14 days."))
                .andExpect(jsonPath("$.sources[0].filename").value("Refund_Policy.pdf"))
                .andExpect(jsonPath("$.sources[0].pageNumber").value(2))
                .andExpect(jsonPath("$.sources[0].score").value(0.74));
    }

    @Test
    @DisplayName("Authenticated ADMIN request to POST /api/chat should also succeed with 200")
    void testChat_AdminRole_Success() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .message("How is shipping calculated?")
                .build();

        ChatResponse mockResponse = ChatResponse.builder()
                .answer("Shipping is flat rate $5.")
                .answered(true)
                .sources(List.of())
                .build();

        when(chatService.processChat(any(), eq("admin@example.com"), any(ChatRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.answer").value("Shipping is flat rate $5."));
    }

    @Test
    @DisplayName("Validation failure: Blank message should return HTTP 400 with validation error")
    void testChat_BlankMessage_Returns400() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .message("   ")
                .build();

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.message").value("Message must not be blank"));
    }

    @Test
    @DisplayName("Validation failure: Message exceeding 1000 characters should return HTTP 400")
    void testChat_MessageTooLong_Returns400() throws Exception {
        String longMessage = "a".repeat(1001);
        ChatRequest request = ChatRequest.builder()
                .message(longMessage)
                .build();

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.message").value("Message must not exceed 1000 characters"));
    }

    @Test
    @DisplayName("Rate limit exceeded should return HTTP 429 with clear message")
    void testChat_RateLimit_Returns429() throws Exception {
        ChatRequest request = ChatRequest.builder().message("Test query").build();

        when(chatService.processChat(any(), any(), any(ChatRequest.class)))
                .thenThrow(new ChatRateLimitException("Too many chat requests. Please wait a moment before sending another message."));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many chat requests. Please wait a moment before sending another message."));
    }

    @Test
    @DisplayName("Assistant busy (Gemini 429) should return HTTP 429 with exact required message")
    void testChat_AssistantBusy_Returns429() throws Exception {
        ChatRequest request = ChatRequest.builder().message("Test query").build();

        when(chatService.processChat(any(), any(), any(ChatRequest.class)))
                .thenThrow(new ChatAssistantBusyException("The assistant is busy, please try again in a minute"));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("The assistant is busy, please try again in a minute"));
    }

    @Test
    @DisplayName("Model unavailable (404/403) should return HTTP 502 with exact required message")
    void testChat_ModelUnavailable_Returns502() throws Exception {
        ChatRequest request = ChatRequest.builder().message("Test query").build();

        when(chatService.processChat(any(), any(), any(ChatRequest.class)))
                .thenThrow(new ChatModelUnavailableException("Chat model not available. Check GEMINI_CHAT_MODEL and the API key"));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Chat model not available. Check GEMINI_CHAT_MODEL and the API key"));
    }

    @Test
    @DisplayName("Timeout should return HTTP 504 Gateway Timeout")
    void testChat_Timeout_Returns504() throws Exception {
        ChatRequest request = ChatRequest.builder().message("Test query").build();

        when(chatService.processChat(any(), any(), any(ChatRequest.class)))
                .thenThrow(new ChatTimeoutException("The assistant took too long to respond. Please try again."));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value("The assistant took too long to respond. Please try again."));
    }

    @Test
    @DisplayName("General communication error should return HTTP 502 Bad Gateway")
    void testChat_GeneralError_Returns502() throws Exception {
        ChatRequest request = ChatRequest.builder().message("Test query").build();

        when(chatService.processChat(any(), any(), any(ChatRequest.class)))
                .thenThrow(new ChatServiceException("An error occurred while communicating with the chat model. Please try again."));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("An error occurred while communicating with the chat model. Please try again."));
    }
}
