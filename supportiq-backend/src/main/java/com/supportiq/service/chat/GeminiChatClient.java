package com.supportiq.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.supportiq.exception.ChatAssistantBusyException;
import com.supportiq.exception.ChatModelUnavailableException;
import com.supportiq.exception.ChatServiceException;
import com.supportiq.exception.ChatTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.EOFException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Client for interacting with Google Gemini GenerateContent REST API.
 * 
 * Features:
 * - Default primary model: gemini-3.5-flash (via GEMINI_CHAT_MODEL or app.rag.chat-model).
 * - Fallback model: gemini-3.1-flash-lite (via GEMINI_CHAT_FALLBACK_MODEL or app.rag.fallback-chat-model).
 * - Per-attempt read timeout of 12 seconds; total wait under ~30 seconds.
 * - Falls back to secondary model on timeout, connection closed/reset, HTTP 404, 429, and 503.
 * - Full function-calling tool loop with up to 3 rounds.
 * - Preserves model content parts and thought signatures for Gemini 3.x and 3.5 models.
 * - RestClient with strict timeout configuration.
 * - Sends API key ONLY in x-goog-api-key header (never in URL or query params).
 * - Enforces temperature 0.2, max output tokens, and thinkingConfig: { thinkingLevel: 'low' }.
 * - Maps upstream HTTP error codes: 429/503 -> ChatAssistantBusyException, 404/403 -> ChatModelUnavailableException, timeouts -> ChatTimeoutException.
 * - Strict confidentiality: Never leaks API key, full URL, prompts, or sensitive arguments in logs or responses.
 */
@Slf4j
@Component
public class GeminiChatClient {

    private final String apiKey;
    private final String primaryModel;
    private final String fallbackModel;
    private final double temperature;
    private final int maxOutputTokens;
    private final RestClient restClient;

    private final ThreadLocal<String> lastAnsweredModel = new ThreadLocal<>();
    private final ThreadLocal<Boolean> lastFallbackUsed = ThreadLocal.withInitial(() -> false);

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiChatClient(
            @Value("${app.rag.gemini.api-key:}") String apiKey,
            @Value("${app.rag.chat-model:gemini-3.5-flash}") String primaryModel,
            @Value("${app.rag.fallback-chat-model:gemini-3.1-flash-lite}") String fallbackModel,
            @Value("${app.rag.temperature:0.2}") double temperature,
            @Value("${app.rag.max-output-tokens:1000}") int maxOutputTokens,
            @Value("${app.rag.chat-timeout-seconds:12}") int timeoutSeconds) {

        this(apiKey, primaryModel, fallbackModel, temperature, maxOutputTokens, createDefaultRestClient(timeoutSeconds));
    }

    public GeminiChatClient(
            String apiKey,
            String primaryModel,
            String fallbackModel,
            double temperature,
            int maxOutputTokens,
            RestClient restClient) {

        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.primaryModel = (primaryModel != null && !primaryModel.isBlank()) ? primaryModel.trim() : "gemini-3.5-flash";
        this.fallbackModel = (fallbackModel != null && !fallbackModel.isBlank()) ? fallbackModel.trim() : "gemini-3.1-flash-lite";
        this.temperature = temperature > 0 ? temperature : 0.2;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 1000;
        this.restClient = (restClient != null) ? restClient : RestClient.builder().build();
    }

    private static RestClient createDefaultRestClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int readTimeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 12;
        int connectTimeoutMs = Math.min(3000, readTimeoutSeconds * 1000);
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Calls Gemini generateContent with the system instruction and context/user prompt (text-only).
     *
     * @param systemInstruction system-level rules and persona
     * @param userPrompt user question with delimited context
     * @return generated answer text
     */
    public String generateContent(String systemInstruction, String userPrompt) {
        GeminiChatResult result = generateContentWithTools(systemInstruction, userPrompt, null, null);
        return result != null ? result.getAnswer() : null;
    }

    /**
     * Executes the Gemini generateContent call with tool declarations and function calling loop.
     * Attempts the primary model, and falls back to the fallback model on timeout, connection closed/reset,
     * HTTP 404, 429, or 503.
     *
     * @param systemInstruction system-level rules and persona
     * @param userPrompt user prompt or question
     * @param tools tool declarations schema (or null)
     * @param toolExecutor callback for executing tool calls (or null)
     * @return chat result including final answer and tools used
     */
    public GeminiChatResult generateContentWithTools(
            String systemInstruction,
            String userPrompt,
            List<Map<String, Object>> tools,
            ToolExecutor toolExecutor) {

        if (apiKey.isBlank()) {
            throw new ChatModelUnavailableException("Chat model not available. Check GEMINI_CHAT_MODEL and the API key");
        }

        lastAnsweredModel.remove();
        lastFallbackUsed.set(false);

        try {
            GeminiChatResult result = runToolLoop(primaryModel, systemInstruction, userPrompt, tools, toolExecutor);
            lastAnsweredModel.set(primaryModel);
            lastFallbackUsed.set(false);
            return result;
        } catch (Exception primaryEx) {
            if (shouldFallback(primaryEx) && !primaryModel.equalsIgnoreCase(fallbackModel)) {
                log.warn("Primary chat model '{}' failed ({}) - attempting fallback model '{}'",
                        primaryModel, getSafeFailureDescription(primaryEx), fallbackModel);
                try {
                    GeminiChatResult result = runToolLoop(fallbackModel, systemInstruction, userPrompt, tools, toolExecutor);
                    lastAnsweredModel.set(fallbackModel);
                    lastFallbackUsed.set(true);
                    return result;
                } catch (RestClientResponseException fallbackRex) {
                    handleRestClientResponseException(fallbackRex);
                } catch (Exception fallbackEx) {
                    handleGeneralException(fallbackEx);
                }
            } else {
                if (primaryEx instanceof RestClientResponseException rex) {
                    handleRestClientResponseException(rex);
                } else {
                    handleGeneralException(primaryEx);
                }
            }
            return null; // Unreachable
        }
    }

    private GeminiChatResult runToolLoop(
            String model,
            String systemInstruction,
            String userPrompt,
            List<Map<String, Object>> tools,
            ToolExecutor toolExecutor) {

        List<Object> contents = new ArrayList<>();
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt != null ? userPrompt : ""))
        ));

        List<String> toolsUsed = new ArrayList<>();
        List<String> orderNumbersReferenced = new ArrayList<>();
        com.supportiq.dto.TicketSummaryDto createdTicket = null;
        int toolRounds = 0;

        while (true) {
            JsonNode responseNode = callModelRaw(model, systemInstruction, contents, tools);
            JsonNode candidates = responseNode.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
            }

            JsonNode contentNode = candidates.get(0).path("content");
            JsonNode parts = contentNode.path("parts");
            if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
                throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
            }

            // Find any function call parts
            List<JsonNode> functionCallParts = new ArrayList<>();
            for (JsonNode part : parts) {
                if (part.has("functionCall")) {
                    functionCallParts.add(part.get("functionCall"));
                }
            }

            // If no function call, model completed its text answer
            if (functionCallParts.isEmpty() || toolExecutor == null) {
                String answer = extractAnswerText(responseNode);
                return new GeminiChatResult(answer, toolsUsed, orderNumbersReferenced, createdTicket);
            }

            // Check 3-round loop limit
            if (toolRounds >= 3) {
                log.info("Tool calling exceeded max 3 rounds. Stopping loop gracefully.");
                return new GeminiChatResult(
                        "I am sorry, but I was unable to complete retrieving your order information within the allowed steps. Please check your order details or contact human support for assistance.",
                        toolsUsed,
                        orderNumbersReferenced,
                        createdTicket
                );
            }

            // CRITICAL FOR GEMINI 3.x / 3.5:
            // Append the model's exact content object (preserves thought signatures, functionCall, and parts)
            contents.add(contentNode);

            // Execute each functionCall and collect functionResponse parts
            List<Map<String, Object>> responseParts = new ArrayList<>();
            for (JsonNode fnCall : functionCallParts) {
                String fnName = fnCall.path("name").asText();
                Map<String, Object> args = new LinkedHashMap<>();
                JsonNode argsNode = fnCall.path("args");
                if (argsNode != null && argsNode.isObject()) {
                    argsNode.fields().forEachRemaining(entry -> {
                        if (entry.getValue().isTextual()) {
                            args.put(entry.getKey(), entry.getValue().asText());
                        } else if (entry.getValue().isNumber()) {
                            args.put(entry.getKey(), entry.getValue().numberValue());
                        } else if (entry.getValue().isBoolean()) {
                            args.put(entry.getKey(), entry.getValue().asBoolean());
                        } else {
                            args.put(entry.getKey(), entry.getValue().toString());
                        }
                    });
                }

                if (!toolsUsed.contains(fnName)) {
                    toolsUsed.add(fnName);
                }

                // Log tool execution safely:
                // "Do not log tool arguments with personal data beyond the order number, never log the full message, results or answer."
                String orderNumArg = args.get("orderNumber") != null ? String.valueOf(args.get("orderNumber")) : null;
                if (orderNumArg != null) {
                    log.info("Invoking model tool '{}' for orderNumber: {}", fnName, orderNumArg);
                } else {
                    log.info("Invoking model tool '{}'", fnName);
                }

                ToolExecutionResult toolResult = toolExecutor.execute(fnName, args);
                if (toolResult != null) {
                    if (toolResult.referencedOrderNumbers() != null) {
                        for (String ord : toolResult.referencedOrderNumbers()) {
                            if (ord != null && !ord.isBlank() && !orderNumbersReferenced.contains(ord)) {
                                orderNumbersReferenced.add(ord);
                            }
                        }
                    }
                    if (toolResult.createdTicket() != null) {
                        createdTicket = toolResult.createdTicket();
                    }
                    Map<String, Object> fnResponseMap = new LinkedHashMap<>();
                    fnResponseMap.put("name", fnName);
                    fnResponseMap.put("response", toolResult.responseData() != null ? toolResult.responseData() : Map.of());
                    responseParts.add(Map.of("functionResponse", fnResponseMap));
                }
            }

            // Add user turn containing functionResponse parts
            contents.add(Map.of("role", "user", "parts", responseParts));
            toolRounds++;
        }
    }

    private JsonNode callModelRaw(String model, String systemInstruction, List<Object> contents, List<Map<String, Object>> tools) {
        String modelName = model.startsWith("models/") ? model.substring(7) : model;
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";

        Map<String, Object> payload = buildRequestBody(systemInstruction, contents, tools);

        return restClient.post()
                .uri(endpoint)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }

    private Map<String, Object> buildRequestBody(String systemInstruction, List<Object> contents, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("system_instruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction.trim()))
            ));
        }

        body.put("contents", contents);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", this.temperature);
        generationConfig.put("maxOutputTokens", this.maxOutputTokens);
        generationConfig.put("thinkingConfig", Map.of("thinkingLevel", "low"));
        body.put("generationConfig", generationConfig);

        return body;
    }

    private String extractAnswerText(JsonNode responseNode) {
        if (responseNode == null) {
            throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
        }

        JsonNode candidates = responseNode.path("candidates");
        if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
            throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
            throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
        }

        StringBuilder answerBuilder = new StringBuilder();
        // Extract parts where thought is false or absent
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            String text = part.path("text").asText("");
            if (!text.isEmpty()) {
                answerBuilder.append(text);
            }
        }

        String answer = answerBuilder.toString().trim();
        // If thought filtering produced empty string, fallback to any text present
        if (answer.isEmpty()) {
            for (JsonNode part : parts) {
                String text = part.path("text").asText("");
                if (!text.isEmpty()) {
                    answerBuilder.append(text);
                }
            }
            answer = answerBuilder.toString().trim();
        }

        if (answer.isEmpty()) {
            throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
        }

        return answer;
    }

    private boolean shouldFallback(Throwable ex) {
        if (ex instanceof RestClientResponseException rex) {
            int code = rex.getStatusCode().value();
            return code == 404 || code == 429 || code == 503;
        }
        return isTimeout(ex) || isConnectionClosedOrReset(ex);
    }

    private String getSafeFailureDescription(Throwable ex) {
        if (ex instanceof RestClientResponseException rex) {
            return "HTTP " + rex.getStatusCode().value();
        }
        if (isTimeout(ex)) {
            return "Read timeout";
        }
        if (isConnectionClosedOrReset(ex)) {
            return "Connection closed/reset";
        }
        return "Communication failure";
    }

    private void handleRestClientResponseException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        log.error("Gemini Chat API returned HTTP error: status={}", status);

        if (status == 429) {
            throw new ChatAssistantBusyException("The assistant is busy, please try again in a minute", HttpStatus.TOO_MANY_REQUESTS);
        } else if (status == 503) {
            throw new ChatAssistantBusyException("The assistant is busy, please try again in a minute", HttpStatus.SERVICE_UNAVAILABLE);
        } else if (status == 404 || status == 403) {
            throw new ChatModelUnavailableException("Chat model not available. Check GEMINI_CHAT_MODEL and the API key");
        } else {
            throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
        }
    }

    private void handleGeneralException(Exception ex) {
        if (ex instanceof ChatAssistantBusyException
                || ex instanceof ChatModelUnavailableException
                || ex instanceof ChatTimeoutException
                || ex instanceof ChatServiceException) {
            throw (RuntimeException) ex;
        }

        if (isTimeout(ex)) {
            log.error("Gemini Chat API request timed out");
            throw new ChatTimeoutException("The assistant took too long to respond. Please try again.");
        }

        log.error("Failed connecting to Gemini Chat API: [Communication failure]");
        throw new ChatServiceException("An error occurred while communicating with the chat model. Please try again.");
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || "java.net.http.HttpTimeoutException".equals(current.getClass().getName())) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("timed out") || lower.contains("timeout")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isConnectionClosedOrReset(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketException
                    || current instanceof EOFException
                    || current instanceof ProtocolException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("connection reset")
                        || lower.contains("connection closed")
                        || lower.contains("broken pipe")
                        || lower.contains("connection refused")
                        || lower.contains("connection abort")
                        || lower.contains("premature eof")
                        || lower.contains("unexpected end of stream")
                        || lower.contains("unexpected end of file")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public String getLastAnsweredModel() {
        return lastAnsweredModel.get();
    }

    public boolean isLastFallbackUsed() {
        Boolean used = lastFallbackUsed.get();
        return used != null && used;
    }
}
