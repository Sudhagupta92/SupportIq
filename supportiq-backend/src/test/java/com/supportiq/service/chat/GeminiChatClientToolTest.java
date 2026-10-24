package com.supportiq.service.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiChatClientToolTest {

    private MockRestServiceServer mockServer;
    private GeminiChatClient chatClient;
    private final String apiKey = "dummy-test-key";
    private final String primaryModel = "gemini-3.5-flash";
    private final String fallbackModel = "gemini-3.1-flash-lite";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        chatClient = new GeminiChatClient(apiKey, primaryModel, fallbackModel, 0.2, 1000, restClient);
    }

    @Test
    @DisplayName("Function calling succeeds with 1 tool round and returns final text")
    void testToolCalling_SuccessOneRound() {
        // Turn 1: Model calls get_order_status
        String toolCallResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [
                          {
                            "thought": true,
                            "text": "User is asking about order 1001. I need to call get_order_status."
                          },
                          {
                            "functionCall": {
                              "name": "get_order_status",
                              "args": { "orderNumber": "1001" }
                            },
                            "thoughtSignature": "test-thought-sig-12345"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        // Turn 2: Model receives functionResponse and produces final text
        String finalAnswerResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [
                          {
                            "text": "Your order #1001 has been DELIVERED via BlueDart."
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", apiKey))
                .andRespond(withSuccess(toolCallResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", apiKey))
                .andRespond(withSuccess(finalAnswerResponse, MediaType.APPLICATION_JSON));

        ToolExecutor executor = (fnName, args) -> {
            assertThat(fnName).isEqualTo("get_order_status");
            assertThat(args.get("orderNumber")).isEqualTo("1001");
            return new ToolExecutionResult(
                    Map.of("found", true, "orderNumber", "1001", "status", "DELIVERED"),
                    List.of("1001")
            );
        };

        GeminiChatResult result = chatClient.generateContentWithTools(
                "System instructions",
                "Where is order 1001?",
                List.of(Map.of("name", "dummyTool")),
                executor
        );

        assertThat(result.getAnswer()).isEqualTo("Your order #1001 has been DELIVERED via BlueDart.");
        assertThat(result.getToolsUsed()).containsExactly("get_order_status");
        assertThat(result.getOrderNumbersReferenced()).containsExactly("1001");
        mockServer.verify();
    }

    @Test
    @DisplayName("Tool loop stops after 3 rounds and returns friendly message")
    void testToolCalling_LoopStopsAfter3Rounds() {
        String toolCallResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [
                          {
                            "functionCall": {
                              "name": "get_order_status",
                              "args": { "orderNumber": "1001" }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        // Initial call + 3 tool turns = 4 server responses where model keeps repeating tool calls
        for (int i = 0; i < 4; i++) {
            mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(toolCallResponse, MediaType.APPLICATION_JSON));
        }

        ToolExecutor executor = (fnName, args) -> new ToolExecutionResult(
                Map.of("found", true, "orderNumber", "1001"),
                List.of("1001")
        );

        GeminiChatResult result = chatClient.generateContentWithTools(
                "System instructions",
                "Loop test query",
                List.of(Map.of("name", "dummyTool")),
                executor
        );

        assertThat(result.getAnswer()).contains("unable to complete retrieving your order information within the allowed steps");
        assertThat(result.getToolsUsed()).contains("get_order_status");
        mockServer.verify();
    }
}
