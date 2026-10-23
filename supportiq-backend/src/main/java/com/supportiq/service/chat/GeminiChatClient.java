package com.supportiq.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.supportiq.exception.ChatAssistantBusyException;
import com.supportiq.exception.ChatModelUnavailableException;
import com.supportiq.exception.ChatServiceException;
import com.supportiq.exception.ChatTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Client for interacting with Google Gemini GenerateContent REST API.
 * 
 * Features:
 * - Default model: gemini-3.8-flash (via GEMINI_CHAT_MODEL or app.rag.chat-model).
 * - Fallback model: gemini-3.1-flash-lite.
 * - RestClient with strict timeout configuration.
 * - Sends API key ONLY in x-goog-api-key header (never in URL or query params).
 * - Enforces temperature 0.2, max output tokens, and thinkingConfig: { thinkingLevel: 'low' }.
 * - Maps upstream HTTP error codes: 429 -> ChatAssistantBusyException, 404/403 -> ChatModelUnavailableException, timeouts -> ChatTimeoutException.
 * - Strict confidentiality: Never leaks API key, full URL, prompts, or stack traces in logs or responses.
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

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiChatClient(
            @Value("${app.rag.gemini.api-key:}") String apiKey,
            @Value("${app.rag.chat-model:gemini-3.8-flash}") String primaryModel,
            @Value("${app.rag.fallback-chat-model:gemini-3.1-flash-lite}") String fallbackModel,
            @Value("${app.rag.temperature:0.2}") double temperature,
            @Value("${app.rag.max-output-tokens:1000}") int maxOutputTokens,
            @Value("${app.rag.chat-timeout-seconds:30}") int timeoutSeconds) {

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
        this.primaryModel = (primaryModel != null && !primaryModel.isBlank()) ? primaryModel.trim() : "gemini-3.8-flash";
        this.fallbackModel = (fallbackModel != null && !fallbackModel.isBlank()) ? fallbackModel.trim() : "gemini-3.1-flash-lite";
        this.temperature = temperature > 0 ? temperature : 0.2;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 1000;
        this.restClient = (restClient != null) ? restClient : RestClient.builder().build();
    }

    private static RestClient createDefaultRestClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1000, timeoutSeconds * 1000);
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Calls Gemini generateContent with the system instruction and context/user prompt.
     * Attempts the primary model, and falls back to the fallback model if primary is not found (404).
     *
     * @param systemInstruction system-level rules and persona
     * @param userPrompt user question with delimited context
     * @return generated answer text
     */
    public String generateContent(String systemInstruction, String userPrompt) {
        if (apiKey.isBlank()) {
            throw new ChatModelUnavailableException("Chat model not available. Check GEMINI_CHAT_MODEL and the API key");
        }

        try {
            return callModel(primaryModel, systemInstruction, userPrompt);
        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if ((statusCode == 404 || statusCode == 503) && !primaryModel.equalsIgnoreCase(fallbackModel)) {
                log.warn("Primary chat model '{}' returned HTTP {}. Attempting fallback model '{}'", primaryModel, statusCode, fallbackModel);
                try {
                    return callModel(fallbackModel, systemInstruction, userPrompt);
                } catch (RestClientResponseException fallbackEx) {
                    handleRestClientResponseException(fallbackEx);
                } catch (Exception fallbackEx) {
                    handleGeneralException(fallbackEx);
                }
            }
            handleRestClientResponseException(ex);
            return null; // Unreachable
        } catch (Exception ex) {
            handleGeneralException(ex);
            return null; // Unreachable
        }
    }

    private String callModel(String model, String systemInstruction, String userPrompt) {
        String modelName = model.startsWith("models/") ? model.substring(7) : model;
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";

        Map<String, Object> payload = buildRequestBody(systemInstruction, userPrompt);

        JsonNode responseNode = restClient.post()
                .uri(endpoint)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        return extractAnswerText(responseNode);
    }

    private Map<String, Object> buildRequestBody(String systemInstruction, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("system_instruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction.trim()))
            ));
        }

        body.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt != null ? userPrompt : ""))
                )
        ));

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

    private void handleRestClientResponseException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        log.error("Gemini Chat API returned HTTP error: status={}", status);

        if (status == 429) {
            throw new ChatAssistantBusyException("The assistant is busy, please try again in a minute");
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
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof ResourceAccessException && current.getMessage() != null) {
                String msg = current.getMessage().toLowerCase();
                if (msg.contains("timed out") || msg.contains("timeout")) {
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
}
