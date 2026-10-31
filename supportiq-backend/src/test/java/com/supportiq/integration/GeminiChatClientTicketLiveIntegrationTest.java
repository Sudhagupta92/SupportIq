package com.supportiq.integration;

import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.TicketResponse;
import com.supportiq.model.Order;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.model.TicketStatus;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
import com.supportiq.service.chat.ChatService;
import com.supportiq.service.chat.EscalationDetector;
import com.supportiq.service.chat.GeminiChatClient;
import com.supportiq.service.chat.GeminiChatResult;
import com.supportiq.service.chat.OrderToolService;
import com.supportiq.service.chat.TicketToolService;
import com.supportiq.service.chat.ToolExecutionResult;
import com.supportiq.service.ticket.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Optional live integration test verifying function calling with Google Gemini API
 * for explicit escalation and ticket creation.
 * Automatically skipped if GEMINI_API_KEY environment variable is not present.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "^(?!\\s*$).+")
class GeminiChatClientTicketLiveIntegrationTest {

    @Test
    @DisplayName("Live Integration Test: demo1 writes an explicit Hinglish request to raise a ticket about order 1004 and a ticket is created exactly once")
    void testLiveTicketCreationWithGemini() throws InterruptedException {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        String chatModel = System.getenv().getOrDefault("GEMINI_CHAT_MODEL", "gemini-3.5-flash");
        String fallbackModel = System.getenv().getOrDefault("GEMINI_CHAT_FALLBACK_MODEL", "gemini-3.1-flash-lite");

        GeminiChatClient client = new GeminiChatClient(
                apiKey,
                chatModel,
                fallbackModel,
                0.2,
                500,
                30
        );

        User demo1 = User.builder().id(1L).email("demo1@example.com").name("Demo Customer 1").role(Role.CUSTOMER).build();
        Order order1004 = Order.builder()
                .id(4L)
                .orderNumber("1004")
                .user(demo1)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("850.00"))
                .currency("INR")
                .build();

        OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
        when(orderRepository.findByOrderNumber("1004")).thenReturn(Optional.of(order1004));

        TicketService ticketService = Mockito.mock(TicketService.class);
        AtomicInteger ticketCreationCount = new AtomicInteger(0);

        when(ticketService.createTicket(eq(1L), any(CreateTicketRequest.class), eq(TicketSource.CHAT_TOOL), any()))
                .thenAnswer(inv -> {
                    ticketCreationCount.incrementAndGet();
                    CreateTicketRequest req = inv.getArgument(1);
                    return TicketResponse.builder()
                            .ticketNumber("TKT-1004")
                            .subject(req.getSubject())
                            .category(req.getCategory())
                            .priority(req.getPriority() != null ? req.getPriority() : TicketPriority.HIGH)
                            .status(TicketStatus.OPEN)
                            .relatedOrderNumber("1004")
                            .existing(false)
                            .build();
                });

        EscalationDetector escalationDetector = new EscalationDetector(
                "fraud,scam,cheated,damaged,broken,dhokha,toota,loot,fake,chor",
                "refund not received,refund,complaint,wrong item,manager,worst,angry,bekar,kharab,wapas nahi mila,not received,defective,gussa"
        );
        TicketToolService ticketToolService = new TicketToolService(ticketService, escalationDetector);
        OrderToolService orderToolService = new OrderToolService(orderRepository);

        List<Map<String, Object>> tools = List.of(Map.of(
                "functionDeclarations", List.of(
                        orderToolService.getFunctionDeclarations().get(0),
                        orderToolService.getFunctionDeclarations().get(1),
                        ticketToolService.getToolDeclaration()
                )
        ));

        String customerQuery = "mera order 1004 cancel ho gaya tha, please ek ticket bana do refund ke liye";

        GeminiChatResult result = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                result = client.generateContentWithTools(
                        ChatService.SYSTEM_INSTRUCTION_ORDER_ONLY,
                        customerQuery,
                        tools,
                        (fnName, args) -> {
                            if (TicketToolService.TOOL_CREATE_SUPPORT_TICKET.equals(fnName)) {
                                return ticketToolService.executeTool(args, 1L, customerQuery, Collections.emptyList());
                            } else {
                                return orderToolService.executeTool(fnName, args, 1L);
                            }
                        }
                );
                if (result != null && result.getAnswer() != null) {
                    break;
                }
            } catch (Exception ex) {
                if (attempt == 0) {
                    Thread.sleep(2000);
                } else {
                    throw ex;
                }
            }
        }

        assertThat(result).isNotNull();
        assertThat(result.getAnswer()).isNotBlank();

        // Verify ticket creation was invoked exactly once
        assertThat(ticketCreationCount.get()).isEqualTo(1);
        assertThat(result.getToolsUsed()).contains("create_support_ticket");
        assertThat(result.getCreatedTicket()).isNotNull();
        assertThat(result.getCreatedTicket().getTicketNumber()).isEqualTo("TKT-1004");

        // Verify model answer informs customer of ticket number
        assertThat(result.getAnswer()).contains("TKT-1004");
    }
}
