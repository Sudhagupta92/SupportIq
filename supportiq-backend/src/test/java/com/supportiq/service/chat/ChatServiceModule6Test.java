package com.supportiq.service.chat;

import com.supportiq.dto.ChatRequest;
import com.supportiq.dto.ChatResponse;
import com.supportiq.dto.TicketSummaryDto;
import com.supportiq.service.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceModule6Test {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private GeminiChatClient geminiChatClient;

    @Mock
    private ChatRateLimiter chatRateLimiter;

    @Mock
    private OrderToolService orderToolService;

    @Mock
    private TicketService ticketService;

    private OrderIntentDetector orderIntentDetector;
    private EscalationDetector escalationDetector;
    private ExplicitEscalationRequestDetector explicitEscalationDetector;
    private TicketToolService ticketToolService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        orderIntentDetector = new OrderIntentDetector(
                "order,orders,track,tracking,parcel,package,delivery,delivered,shipped,status,kahan,kab aayega,ye order,mera order"
        );
        escalationDetector = new EscalationDetector(
                "fraud,scam,cheated,damaged,broken,dhokha,toota,loot,fake,chor",
                "refund not received,refund,complaint,wrong item,manager,worst,angry,bekar,kharab,wapas nahi mila,not received,defective,gussa"
        );
        explicitEscalationDetector = new ExplicitEscalationRequestDetector(ExplicitEscalationRequestDetector.DEFAULT_EXPLICIT_KEYWORDS);
        ticketToolService = new TicketToolService(ticketService, escalationDetector);

        chatService = new ChatService(
                vectorStore,
                geminiChatClient,
                chatRateLimiter,
                orderIntentDetector,
                orderToolService,
                escalationDetector,
                explicitEscalationDetector,
                ticketToolService,
                4,
                0.58
        );
    }

    @Test
    @DisplayName("Tool declaration rule: create_support_ticket is NOT declared when message has no explicit request")
    void testToolDeclaration_NotDeclaredWithoutExplicitRequest() {
        // Query has order intent but NO explicit escalation request
        String query = "Where is my order 1001?";
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        when(orderToolService.getFunctionDeclarations()).thenReturn(List.of(
                Map.of("name", "get_order_status"),
                Map.of("name", "list_my_recent_orders")
        ));

        when(geminiChatClient.generateContentWithTools(any(), any(), any(), any()))
                .thenReturn(new GeminiChatResult("Order is on the way", List.of("get_order_status"), List.of("1001")));

        chatService.processChat(1L, "user1", new ChatRequest(query));

        ArgumentCaptor<List<Map<String, Object>>> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiChatClient).generateContentWithTools(any(), any(), toolsCaptor.capture(), any());

        List<Map<String, Object>> declaredTools = toolsCaptor.getValue();
        List<Map<String, Object>> functions = (List<Map<String, Object>>) declaredTools.get(0).get("functionDeclarations");

        // Must contain order tools, but MUST NOT contain create_support_ticket!
        List<String> functionNames = functions.stream().map(f -> (String) f.get("name")).toList();
        assertThat(functionNames).contains("get_order_status", "list_my_recent_orders");
        assertThat(functionNames).doesNotContain("create_support_ticket");
    }

    @Test
    @DisplayName("Tool declaration rule: create_support_ticket IS declared when message has explicit request")
    void testToolDeclaration_DeclaredWhenExplicitRequestPresent() {
        // Query has explicit request to raise a ticket
        String query = "Please raise a ticket for my order 1004";
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        when(orderToolService.getFunctionDeclarations()).thenReturn(List.of(
                Map.of("name", "get_order_status"),
                Map.of("name", "list_my_recent_orders")
        ));

        when(geminiChatClient.generateContentWithTools(any(), any(), any(), any()))
                .thenReturn(new GeminiChatResult("Ticket created", List.of("create_support_ticket"), List.of("1004"),
                        new TicketSummaryDto("TKT-1004", "Order 1004 refund", false)));

        ChatResponse response = chatService.processChat(1L, "user1", new ChatRequest(query));

        ArgumentCaptor<List<Map<String, Object>>> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiChatClient).generateContentWithTools(any(), any(), toolsCaptor.capture(), any());

        List<Map<String, Object>> declaredTools = toolsCaptor.getValue();
        List<Map<String, Object>> functions = (List<Map<String, Object>>) declaredTools.get(0).get("functionDeclarations");

        List<String> functionNames = functions.stream().map(f -> (String) f.get("name")).toList();
        assertThat(functionNames).contains("create_support_ticket");
        assertThat(response.getTicket()).isNotNull();
        assertThat(response.getTicket().getTicketNumber()).isEqualTo("TKT-1004");
    }

    @Test
    @DisplayName("Module 5 decision paths still hold: Off-topic pizza message NEVER calls the model (Path d)")
    void testModule5DecisionPath_PizzaNeverCallsModel() {
        String query = "Do you sell pizza?";

        // Low retrieval score
        Document doc = Document.builder()
                .text("Our store sells apparel")
                .score(0.40) // < 0.58
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        ChatResponse response = chatService.processChat(1L, "user1", new ChatRequest(query));

        // Path (d) immediately returns unanswered message
        assertThat(response.isAnswered()).isFalse();
        assertThat(response.getAnswer()).isEqualTo(ChatService.DEFAULT_UNANSWERED_MESSAGE);
        assertThat(response.getTicket()).isNull();

        // LLM client is NEVER called!
        verify(geminiChatClient, never()).generateContent(any(), any());
        verify(geminiChatClient, never()).generateContentWithTools(any(), any(), any(), any());
        verify(ticketService, never()).createTicket(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Soft escalation signals NEVER create a ticket by themselves")
    void testSoftEscalation_NeverCreatesTicket() {
        // Customer says "item was damaged" -> soft escalation signal (suggested = true, HIGH priority)
        String query = "The shoes in my order arrived damaged";
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        when(orderToolService.getFunctionDeclarations()).thenReturn(List.of(
                Map.of("name", "get_order_status")
        ));

        when(geminiChatClient.generateContentWithTools(any(), any(), any(), any()))
                .thenReturn(new GeminiChatResult("I am sorry your shoes were damaged. You can return them.", List.of(), List.of()));

        ChatResponse response = chatService.processChat(1L, "user1", new ChatRequest(query));

        // Soft escalation suggested is TRUE
        assertThat(response.getEscalation()).isNotNull();
        assertThat(response.getEscalation().isSuggested()).isTrue();
        assertThat(response.getEscalation().getPriority()).isEqualTo(com.supportiq.model.TicketPriority.HIGH);

        // But NO ticket was created!
        assertThat(response.getTicket()).isNull();
        verify(ticketService, never()).createTicket(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Security: Prompt injection text in order lastUpdateNote cannot cause a ticket to be created")
    void testPromptInjectionInOrderNotes_CannotCreateTicket() {
        // Customer asks benign order tracking question
        String query = "Where is my order 1002?";
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        when(orderToolService.getFunctionDeclarations()).thenReturn(List.of(
                Map.of("name", "get_order_status")
        ));

        when(geminiChatClient.generateContentWithTools(any(), any(), any(), any()))
                .thenReturn(new GeminiChatResult(
                        "Order is shipped. Tracking number: DL44820193",
                        List.of("get_order_status"),
                        List.of("1002")
                ));

        ChatResponse response = chatService.processChat(1L, "user1", new ChatRequest(query));

        // Because query has NO explicit escalation intent, create_support_ticket was NOT declared
        ArgumentCaptor<List<Map<String, Object>>> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiChatClient).generateContentWithTools(any(), any(), toolsCaptor.capture(), any());

        List<Map<String, Object>> functions = (List<Map<String, Object>>) toolsCaptor.getValue().get(0).get("functionDeclarations");
        List<String> functionNames = functions.stream().map(f -> (String) f.get("name")).toList();
        assertThat(functionNames).doesNotContain("create_support_ticket");
        assertThat(response.getTicket()).isNull();
        verify(ticketService, never()).createTicket(any(), any(), any(), any());
    }
}
