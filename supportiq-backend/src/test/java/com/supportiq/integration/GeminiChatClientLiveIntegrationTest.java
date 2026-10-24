package com.supportiq.integration;

import com.supportiq.service.chat.ChatService;
import com.supportiq.service.chat.GeminiChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional live integration test verifying real end-to-end communication with the Google Gemini Chat API.
 * Automatically skipped if GEMINI_API_KEY environment variable is not set.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "^(?!\\s*$).+")
class GeminiChatClientLiveIntegrationTest {

    @Test
    @DisplayName("Live Integration Test: Call real Gemini chat model gemini-3.5-flash")
    void testLiveGeminiChatGeneration() throws InterruptedException {
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
                12
        );

        String context = """
                --- BEGIN CONTEXT ---
                [Source: Return_Policy.pdf, Page: 1]
                SupportIQ customers can return eligible items within 30 days of delivery for a full refund.
                Items must be unused and in original packaging.
                --- END CONTEXT ---

                Customer question: What is the return period?
                """;

        String answer = null;
        Exception lastException = null;

        // Retry up to 3 times in case of transient upstream network / 503 issues
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                answer = client.generateContent(ChatService.SYSTEM_INSTRUCTION, context);
                break;
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < 3) {
                    Thread.sleep(2000L);
                }
            }
        }

        if (answer == null && lastException != null) {
            throw new RuntimeException("Live Gemini call failed after 3 attempts", lastException);
        }

        assertThat(answer).isNotBlank();
        assertThat(answer.toLowerCase()).contains("30");
    }
}
