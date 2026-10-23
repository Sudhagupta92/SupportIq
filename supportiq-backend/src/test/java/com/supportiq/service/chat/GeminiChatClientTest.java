package com.supportiq.service.chat;

import com.supportiq.exception.ChatAssistantBusyException;
import com.supportiq.exception.ChatModelUnavailableException;
import com.supportiq.exception.ChatServiceException;
import com.supportiq.exception.ChatTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiChatClientTest {

    private MockRestServiceServer mockServer;
    private GeminiChatClient chatClient;
    private final String apiKey = "dummy-test-key";
    private final String primaryModel = "gemini-3.8-flash";
    private final String fallbackModel = "gemini-3.1-flash-lite";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        chatClient = new GeminiChatClient(apiKey, primaryModel, fallbackModel, 0.2, 1000, restClient);
    }

    @Test
    @DisplayName("Should extract answer text and exclude thoughts from candidate parts")
    void testGenerateContent_SuccessWithThoughtFiltering() {
        String jsonResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Analyzing the policy...", "thought": true },
                          { "text": "Our store return window is 30 days.", "thought": false }
                        ],
                        "role": "model"
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", apiKey))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        String answer = chatClient.generateContent("System instructions", "User prompt");

        assertThat(answer).isEqualTo("Our store return window is 30 days.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should map HTTP 429 to ChatAssistantBusyException")
    void testGenerateContent_Handles429Busy() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> chatClient.generateContent("System", "User"))
                .isInstanceOf(ChatAssistantBusyException.class)
                .hasMessage("The assistant is busy, please try again in a minute");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should fallback to secondary model when primary returns 404, and succeed")
    void testGenerateContent_FallbackOn404Success() {
        // Primary model returns 404
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Fallback model returns 200
        String fallbackResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Response from fallback model." }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fallbackResponse, MediaType.APPLICATION_JSON));

        String answer = chatClient.generateContent("System", "User");
        assertThat(answer).isEqualTo("Response from fallback model.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should map HTTP 404 on both models to ChatModelUnavailableException (502)")
    void testGenerateContent_BothModels404_ThrowsChatModelUnavailableException() {
        // Primary returns 404
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Fallback also returns 404
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> chatClient.generateContent("System", "User"))
                .isInstanceOf(ChatModelUnavailableException.class)
                .hasMessage("Chat model not available. Check GEMINI_CHAT_MODEL and the API key");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should map HTTP 403 to ChatModelUnavailableException")
    void testGenerateContent_Handles403Forbidden() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> chatClient.generateContent("System", "User"))
                .isInstanceOf(ChatModelUnavailableException.class)
                .hasMessage("Chat model not available. Check GEMINI_CHAT_MODEL and the API key");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should map generic 500 error to ChatServiceException")
    void testGenerateContent_Handles500Error() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> chatClient.generateContent("System", "User"))
                .isInstanceOf(ChatServiceException.class)
                .hasMessage("An error occurred while communicating with the chat model. Please try again.");

        mockServer.verify();
    }
}
