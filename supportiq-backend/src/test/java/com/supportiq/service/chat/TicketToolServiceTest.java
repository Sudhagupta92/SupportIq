package com.supportiq.service.chat;

import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.TicketResponse;
import com.supportiq.exception.TicketRateLimitException;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.model.TicketStatus;
import com.supportiq.service.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketToolServiceTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private EscalationDetector escalationDetector;

    private TicketToolService ticketToolService;

    @BeforeEach
    void setUp() {
        ticketToolService = new TicketToolService(ticketService, escalationDetector);
    }

    @Test
    @DisplayName("Security: create_support_ticket declaration schema NEVER exposes userId, customerId, or email")
    void testToolDeclaration_HasNoUserParameters() {
        Map<String, Object> declaration = ticketToolService.getToolDeclaration();
        assertThat(declaration.get("name")).isEqualTo("create_support_ticket");

        Map<String, Object> parameters = (Map<String, Object>) declaration.get("parameters");
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");

        assertThat(properties).doesNotContainKey("userId");
        assertThat(properties).doesNotContainKey("user");
        assertThat(properties).doesNotContainKey("customerId");
        assertThat(properties).doesNotContainKey("email");
        assertThat(properties).containsKeys("summary", "category", "relatedOrderNumber");
    }

    @Test
    @DisplayName("Tool execution: Ignores model-supplied userId and computes priority strictly on the backend")
    void testExecuteTool_IgnoresModelUserIdAndComputesPriority() {
        Long authenticatedUserId = 42L;

        // Model hallucinates an injected userId: 99 and priority: LOW
        Map<String, Object> modelArgs = new LinkedHashMap<>();
        modelArgs.put("summary", "Damaged watch screen");
        modelArgs.put("category", "DAMAGED_ITEM");
        modelArgs.put("relatedOrderNumber", "1001");
        modelArgs.put("userId", 99L);
        modelArgs.put("priority", "LOW");

        when(escalationDetector.determinePriority(any(), any())).thenReturn(TicketPriority.HIGH);

        TicketResponse mockResponse = TicketResponse.builder()
                .ticketNumber("TKT-1005")
                .subject("Damaged watch screen")
                .category(TicketCategory.DAMAGED_ITEM)
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.OPEN)
                .relatedOrderNumber("1001")
                .existing(false)
                .build();

        when(ticketService.createTicket(eq(authenticatedUserId), any(CreateTicketRequest.class), eq(TicketSource.CHAT_TOOL), any()))
                .thenReturn(mockResponse);

        ToolExecutionResult result = ticketToolService.executeTool(
                modelArgs,
                authenticatedUserId,
                "The screen was cracked on arrival",
                Collections.emptyList()
        );

        assertThat(result.responseData().get("success")).isEqualTo(true);
        assertThat(result.responseData().get("ticketNumber")).isEqualTo("TKT-1005");
        assertThat(result.createdTicket()).isNotNull();
        assertThat(result.createdTicket().getTicketNumber()).isEqualTo("TKT-1005");

        // Verify ticketService received the authenticated user ID (42), NOT the model's injected ID (99)
        ArgumentCaptor<CreateTicketRequest> reqCaptor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService).createTicket(eq(authenticatedUserId), reqCaptor.capture(), eq(TicketSource.CHAT_TOOL), any());

        // Verify priority was HIGH (computed by backend), NOT LOW (chosen by model)
        assertThat(reqCaptor.getValue().getPriority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("Tool execution: Rate limit error is gracefully returned to model")
    void testExecuteTool_RateLimitReturnedGracefully() {
        when(escalationDetector.determinePriority(any(), any())).thenReturn(TicketPriority.MEDIUM);
        when(ticketService.createTicket(any(), any(), any(), any()))
                .thenThrow(new TicketRateLimitException("You have reached the maximum limit of 5 new tickets per 24 hours."));

        ToolExecutionResult result = ticketToolService.executeTool(
                Map.of("summary", "Another ticket"),
                1L,
                "raise a ticket",
                Collections.emptyList()
        );

        assertThat(result.responseData().get("success")).isEqualTo(false);
        assertThat(String.valueOf(result.responseData().get("error"))).contains("limit of 5 new tickets");
    }
}
