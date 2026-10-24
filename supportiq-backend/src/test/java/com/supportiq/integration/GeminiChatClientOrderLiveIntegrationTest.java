package com.supportiq.integration;

import com.supportiq.model.Order;
import com.supportiq.model.OrderItem;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
import com.supportiq.service.chat.ChatService;
import com.supportiq.service.chat.GeminiChatClient;
import com.supportiq.service.chat.GeminiChatResult;
import com.supportiq.service.chat.OrderToolService;
import com.supportiq.service.chat.ToolExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Optional live integration test verifying function calling with Google Gemini API.
 * Automatically skipped if GEMINI_API_KEY environment variable is not present.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "^(?!\\s*$).+")
class GeminiChatClientOrderLiveIntegrationTest {

    @Test
    @DisplayName("Live Integration Test: Ask real Gemini model about order 1001 using demo1 data and verify tool calling")
    void testLiveOrderToolCallingWithGemini() throws InterruptedException {
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

        // Setup demo1 order 1001 data
        User demo1 = User.builder().id(1L).email("demo1@example.com").name("Demo Customer 1").role(Role.CUSTOMER).build();
        Order order1001 = Order.builder()
                .id(1L)
                .orderNumber("1001")
                .user(demo1)
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("2999.00"))
                .currency("INR")
                .placedAt(LocalDateTime.now().minusDays(10))
                .expectedDelivery(LocalDateTime.now().minusDays(7))
                .deliveredAt(LocalDateTime.now().minusDays(6))
                .carrier("BlueDart")
                .trackingNumber("BD90283411")
                .lastUpdateNote("Delivered to recipient at front door")
                .build();

        order1001.addItem(OrderItem.builder().itemName("Blue Cotton Shirt").quantity(2).unitPrice(new BigDecimal("799.50")).build());
        order1001.addItem(OrderItem.builder().itemName("Running Shoes").quantity(1).unitPrice(new BigDecimal("1400.00")).build());

        OrderRepository mockRepository = Mockito.mock(OrderRepository.class);
        when(mockRepository.findByOrderNumber("1001")).thenReturn(Optional.of(order1001));

        OrderToolService toolService = new OrderToolService(mockRepository);

        GeminiChatResult result = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                result = client.generateContentWithTools(
                        ChatService.SYSTEM_INSTRUCTION_ORDER_ONLY,
                        "Mera order 1001 kahan hai?",
                        toolService.getToolDeclarations(),
                        (fnName, args) -> toolService.executeTool(fnName, args, 1L)
                );
                break;
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < 3) {
                    Thread.sleep(2000L);
                }
            }
        }

        if (result == null && lastException != null) {
            throw new AssertionError("Live test failed after 3 attempts", lastException);
        }

        assertThat(result).isNotNull();
        assertThat(result.getAnswer()).isNotBlank();
        assertThat(result.getToolsUsed()).contains("get_order_status");
        assertThat(result.getOrderNumbersReferenced()).contains("1001");
        // Verify answer mentions order status (e.g. DELIVERED / delivered)
        assertThat(result.getAnswer().toLowerCase()).containsAnyOf("deliver", "delivered", "bluedart", "1001");
    }

    @Test
    @DisplayName("Live Integration Test: Ask real Gemini fallback model gemini-3.1-flash-lite about order 1001 and verify tool calling")
    void testLiveOrderToolCallingWithFallbackModel_FlashLite() throws InterruptedException {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }

        // Test fallback model directly as primary
        GeminiChatClient client = new GeminiChatClient(
                apiKey,
                "gemini-3.1-flash-lite",
                "gemini-3.1-flash-lite",
                0.2,
                500,
                30
        );

        User demo1 = User.builder().id(1L).email("demo1@example.com").name("Demo Customer 1").role(Role.CUSTOMER).build();
        Order order1001 = Order.builder()
                .id(1L)
                .orderNumber("1001")
                .user(demo1)
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("2999.00"))
                .currency("INR")
                .placedAt(LocalDateTime.now().minusDays(10))
                .expectedDelivery(LocalDateTime.now().minusDays(7))
                .deliveredAt(LocalDateTime.now().minusDays(6))
                .carrier("BlueDart")
                .trackingNumber("BD90283411")
                .lastUpdateNote("Delivered to recipient at front door")
                .build();

        order1001.addItem(OrderItem.builder().itemName("Blue Cotton Shirt").quantity(2).unitPrice(new BigDecimal("799.50")).build());

        OrderRepository mockRepository = Mockito.mock(OrderRepository.class);
        when(mockRepository.findByOrderNumber("1001")).thenReturn(Optional.of(order1001));

        OrderToolService toolService = new OrderToolService(mockRepository);

        GeminiChatResult result = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                result = client.generateContentWithTools(
                        ChatService.SYSTEM_INSTRUCTION_ORDER_ONLY,
                        "Mera order 1001 kahan hai?",
                        toolService.getToolDeclarations(),
                        (fnName, args) -> toolService.executeTool(fnName, args, 1L)
                );
                break;
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < 3) {
                    Thread.sleep(2000L);
                }
            }
        }

        if (result == null && lastException != null) {
            throw new AssertionError("Live test with gemini-3.1-flash-lite failed after 3 attempts", lastException);
        }

        assertThat(result).isNotNull();
        assertThat(result.getAnswer()).isNotBlank();
        assertThat(result.getToolsUsed()).contains("get_order_status");
        assertThat(result.getOrderNumbersReferenced()).contains("1001");
        assertThat(result.getAnswer().toLowerCase()).containsAnyOf("deliver", "delivered", "bluedart", "1001");
    }
}
