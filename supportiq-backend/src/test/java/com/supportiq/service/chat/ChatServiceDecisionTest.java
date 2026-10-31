package com.supportiq.service.chat;

import com.supportiq.dto.ChatRequest;
import com.supportiq.dto.ChatResponse;
import com.supportiq.dto.ChatSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceDecisionTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private GeminiChatClient geminiChatClient;

    @Mock
    private OrderToolService orderToolService;

    private OrderIntentDetector orderIntentDetector;
    private ChatRateLimiter chatRateLimiter;
    private ChatService chatService;

    private final double minScore = 0.58;
    private final int chatTopK = 4;

    @BeforeEach
    void setUp() {
        chatRateLimiter = new ChatRateLimiter(20);
        orderIntentDetector = new OrderIntentDetector(
                "order,orders,track,tracking,parcel,package,delivery,delivered,shipped,status,kahan,kab aayega,ye order,mera order"
        );
        chatService = new ChatService(
                vectorStore,
                geminiChatClient,
                chatRateLimiter,
                orderIntentDetector,
                orderToolService,
                chatTopK,
                minScore
        );
    }

    @Test
    @DisplayName("Path (a): Retrieval passes AND order-intent -> calls model with policy CONTEXT and tools")
    void testDecisionPathA_RetrievalPassesAndOrderIntent() {
        // High score chunk (0.75 >= 0.58)
        Document doc = Document.builder()
                .text("Orders can be tracked using tracking ID.")
                .metadata(Map.of("filename", "Tracking_Guide.pdf", "pageNumber", 1))
                .score(0.75)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(orderToolService.getFunctionDeclarations()).thenReturn(List.of(Map.of("name", "dummyTool")));

        when(geminiChatClient.generateContentWithTools(
                eq(ChatService.SYSTEM_INSTRUCTION_COMBINED),
                contains("--- BEGIN CONTEXT ---"),
                any(),
                any()
        )).thenReturn(new GeminiChatResult("Order 1001 is delivered", List.of("get_order_status"), List.of("1001")));

        ChatRequest request = ChatRequest.builder().message("What is status of order 1001?").build();
        ChatResponse response = chatService.processChat(1L, "demo1@example.com", request);

        assertThat(response.isAnswered()).isTrue();
        assertThat(response.getToolsUsed()).containsExactly("get_order_status");
        assertThat(response.getSources()).hasSize(2); // 1 POLICY chunk, 1 ORDER chunk
        assertThat(response.getSources()).anyMatch(s -> s.getType() == ChatSourceType.POLICY && "Tracking_Guide.pdf".equals(s.getFilename()));
        assertThat(response.getSources()).anyMatch(s -> s.getType() == ChatSourceType.ORDER && "Order #1001".equals(s.getLabel()));
    }

    @Test
    @DisplayName("Path (b): Retrieval passes AND no order-intent -> calls model with policy CONTEXT, NO tools")
    void testDecisionPathB_RetrievalPassesAndNoOrderIntent() {
        Document doc = Document.builder()
                .text("Our standard return window is 30 days.")
                .metadata(Map.of("filename", "Return_Policy.pdf", "pageNumber", 2))
                .score(0.79)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(geminiChatClient.generateContent(eq(ChatService.SYSTEM_INSTRUCTION_POLICY_ONLY), contains("--- BEGIN CONTEXT ---")))
                .thenReturn("You can return within 30 days.");

        ChatRequest request = ChatRequest.builder().message("What is your refund policy window?").build();
        ChatResponse response = chatService.processChat(1L, "demo1@example.com", request);

        assertThat(response.isAnswered()).isTrue();
        assertThat(response.getToolsUsed()).isEmpty();
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getType()).isEqualTo(ChatSourceType.POLICY);

        // Verify tools were NOT called
        verify(geminiChatClient, never()).generateContentWithTools(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Path (c): Retrieval below threshold AND order-intent -> calls model with tools ONLY, NO policy chunks")
    void testDecisionPathC_RetrievalBelowThresholdAndOrderIntent() {
        // Chunks below minScore (0.45 < 0.58)
        Document doc = Document.builder()
                .text("Some irrelevant policy text.")
                .metadata(Map.of("filename", "Policy.pdf", "pageNumber", 1))
                .score(0.45)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(orderToolService.getFunctionDeclarations()).thenReturn(List.of(Map.of("name", "dummyTool")));

        when(geminiChatClient.generateContentWithTools(
                eq(ChatService.SYSTEM_INSTRUCTION_ORDER_ONLY),
                eq("Mera order 1001 kahan hai?"),
                any(),
                any()
        )).thenReturn(new GeminiChatResult("Aapka order #1001 deliver ho chuka hai.", List.of("get_order_status"), List.of("1001")));

        ChatRequest request = ChatRequest.builder().message("Mera order 1001 kahan hai?").build();
        ChatResponse response = chatService.processChat(1L, "demo1@example.com", request);

        assertThat(response.isAnswered()).isTrue();
        assertThat(response.getToolsUsed()).containsExactly("get_order_status");
        // Must NOT return policy chips because policy chunks failed threshold
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getType()).isEqualTo(ChatSourceType.ORDER);
        assertThat(response.getSources().get(0).getLabel()).isEqualTo("Order #1001");

        // Verify text-only generateContent was NOT called
        verify(geminiChatClient, never()).generateContent(any(), any());
    }

    @Test
    @DisplayName("Path (d): Retrieval below threshold AND no order-intent -> returns fixed message, NEVER calls model (Pizza case)")
    void testDecisionPathD_RetrievalBelowThresholdAndNoOrderIntent_NeverCallsModel() {
        // Chunk below threshold (0.48 < 0.58)
        Document doc = Document.builder()
                .text("Store policies information.")
                .metadata(Map.of("filename", "Store.pdf", "pageNumber", 1))
                .score(0.48)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        ChatRequest request = ChatRequest.builder().message("Do you sell pizza?").build();
        ChatResponse response = chatService.processChat(1L, "demo1@example.com", request);

        assertThat(response.isAnswered()).isFalse();
        assertThat(response.getAnswer()).isEqualTo(ChatService.DEFAULT_UNANSWERED_MESSAGE);
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getToolsUsed()).isEmpty();

        // Model must NEVER be called
        verify(geminiChatClient, never()).generateContent(any(), any());
        verify(geminiChatClient, never()).generateContentWithTools(any(), any(), any(), any());
    }
}
