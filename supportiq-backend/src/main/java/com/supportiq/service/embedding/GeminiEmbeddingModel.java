package com.supportiq.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.supportiq.exception.EmbeddingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom Spring AI EmbeddingModel implementation targeting Google Gemini's Embedding API (gemini-embedding-001).
 *
 * Implements the Spring AI {@link EmbeddingModel} interface so that the underlying embedding provider
 * remains fully modular and swappable.
 *
 * Key features:
 * - Default model: gemini-embedding-001 (configurable via GEMINI_EMBEDDING_MODEL environment variable).
 * - Output dimensionality: sets outputDimensionality to 768 to match the pgvector column.
 * - Runtime dimension verification: validates that the returned vector length matches the configured dimension.
 * - L2 vector normalization: normalizes truncated vectors to unit length as required by official Gemini documentation.
 * - Task type optimization: supports RETRIEVAL_DOCUMENT for ingested chunks and RETRIEVAL_QUERY for search queries.
 * - Security: API key transmitted exclusively via x-goog-api-key header (never in query parameters).
 *   Redacts keys and full request URLs from all logs, exception messages, and client responses.
 * - Clear error handling: returns specific messages on 404 (model not found) and 403 instead of generic 500s.
 */
@Slf4j
public class GeminiEmbeddingModel implements EmbeddingModel {

    public static final String TASK_TYPE_RETRIEVAL_DOCUMENT = "RETRIEVAL_DOCUMENT";
    public static final String TASK_TYPE_RETRIEVAL_QUERY = "RETRIEVAL_QUERY";

    private final String apiKey;
    private final String model;
    private final int dimension;
    private final int batchSize;
    private final RestClient restClient;

    public GeminiEmbeddingModel(String apiKey, String model, int dimension, int batchSize) {
        this(apiKey, model, dimension, batchSize, RestClient.builder().build());
    }

    public GeminiEmbeddingModel(String apiKey, String model, int dimension, int batchSize, RestClient restClient) {
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gemini-embedding-001";
        this.dimension = dimension > 0 ? dimension : 768;
        this.batchSize = batchSize > 0 ? batchSize : 16;
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        String resolvedTaskType = resolveTaskType(request);
        return call(request, resolvedTaskType);
    }

    /**
     * Executes embedding request with an explicit task_type parameter
     * (e.g. RETRIEVAL_DOCUMENT or RETRIEVAL_QUERY).
     *
     * @param request the Spring AI embedding request
     * @param taskType downstream optimization task type (RETRIEVAL_DOCUMENT or RETRIEVAL_QUERY)
     * @return EmbeddingResponse containing the normalized vectors
     */
    public EmbeddingResponse call(EmbeddingRequest request, String taskType) {
        validateApiKey();

        List<String> texts = request != null ? request.getInstructions() : null;
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResponse(Collections.emptyList());
        }

        String effectiveTaskType = (taskType != null && !taskType.isBlank())
                ? taskType.trim()
                : resolveTaskType(request);

        log.info("Requesting Gemini embeddings for {} text segments (model: {}, taskType: {}, dimension: {})",
                texts.size(), model, effectiveTaskType, dimension);

        List<Embedding> allEmbeddings = new ArrayList<>(texts.size());
        String modelResourceName = model.startsWith("models/") ? model : "models/" + model;
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/" + modelResourceName + ":batchEmbedContents";

        int globalIndex = 0;

        for (int i = 0; i < texts.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, toIndex);

            List<Map<String, Object>> requestsList = new ArrayList<>(batch.size());
            for (String chunkText : batch) {
                Map<String, Object> reqItem = new LinkedHashMap<>();
                reqItem.put("model", modelResourceName);
                reqItem.put("content", Map.of(
                        "parts", List.of(Map.of("text", chunkText != null ? chunkText : ""))
                ));
                if (effectiveTaskType != null && !effectiveTaskType.isBlank()) {
                    reqItem.put("taskType", effectiveTaskType);
                }
                reqItem.put("outputDimensionality", this.dimension);
                requestsList.add(reqItem);
            }

            Map<String, Object> requestPayload = Map.of("requests", requestsList);

            try {
                // Header-only authentication: x-goog-api-key (never in URL)
                JsonNode responseNode = restClient.post()
                        .uri(endpoint)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestPayload)
                        .retrieve()
                        .body(JsonNode.class);

                if (responseNode == null) {
                    throw new EmbeddingException(
                            "Received empty response from Google Gemini Embeddings API.",
                            HttpStatus.BAD_GATEWAY
                    );
                }

                JsonNode embeddingsArray = responseNode.path("embeddings");
                if (embeddingsArray.isMissingNode() || !embeddingsArray.isArray()) {
                    throw new EmbeddingException(
                            "Gemini response missing 'embeddings' array.",
                            HttpStatus.BAD_GATEWAY
                    );
                }

                for (JsonNode embNode : embeddingsArray) {
                    JsonNode valuesNode = embNode.path("values");
                    int actualDim = valuesNode.size();

                    // Runtime check: Verify returned vector length matches configured vector dimension
                    if (actualDim != this.dimension) {
                        throw new EmbeddingException(
                                "Returned vector length (" + actualDim + ") does not match configured vector dimension (" + this.dimension + "). Verify outputDimensionality support for model " + this.model,
                                HttpStatus.BAD_GATEWAY
                        );
                    }

                    float[] rawVector = new float[actualDim];
                    double sumSquares = 0.0;
                    for (int v = 0; v < actualDim; v++) {
                        float val = (float) valuesNode.get(v).asDouble();
                        rawVector[v] = val;
                        sumSquares += (double) val * val;
                    }

                    // L2 Normalization: Reduced dimensions in gemini-embedding-001 require L2 normalization to unit length
                    float[] normalizedVector = normalizeVector(rawVector, sumSquares);

                    allEmbeddings.add(new Embedding(normalizedVector, globalIndex++));
                }
            } catch (RestClientResponseException ex) {
                int statusCode = ex.getStatusCode().value();
                log.error("Google Gemini Embeddings API error: HTTP {}", statusCode);

                if (statusCode == 404 || statusCode == 403) {
                    throw new EmbeddingException(
                            "Embedding model not available. Check GEMINI_EMBEDDING_MODEL and the API key",
                            HttpStatus.BAD_GATEWAY
                    );
                } else if (statusCode == 401) {
                    throw new EmbeddingException(
                            "Google Gemini API authentication failed. Check GEMINI_API_KEY.",
                            HttpStatus.UNAUTHORIZED
                    );
                } else {
                    throw new EmbeddingException(
                            "Google Gemini API error: HTTP " + statusCode + " - " + ex.getStatusText(),
                            HttpStatus.BAD_GATEWAY
                    );
                }
            } catch (EmbeddingException ex) {
                throw ex;
            } catch (Exception ex) {
                String safeMessage = sanitize(ex.getMessage());
                log.error("Failed connecting to Google Gemini Embedding API: {}", safeMessage);
                throw new EmbeddingException(
                        "Failed to generate embeddings from Gemini API: " + safeMessage,
                        HttpStatus.BAD_GATEWAY
                );
            }
        }

        return new EmbeddingResponse(allEmbeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document, TASK_TYPE_RETRIEVAL_DOCUMENT);
    }

    /**
     * Embeds a single Document with an explicit task type parameter.
     */
    public float[] embed(Document document, String taskType) {
        if (document == null || document.getText() == null) {
            return new float[dimension];
        }
        return embed(document.getText(), taskType);
    }

    @Override
    public float[] embed(String text) {
        String taskType = TaskTypeContext.get() != null ? TaskTypeContext.get() : TASK_TYPE_RETRIEVAL_QUERY;
        return embed(text, taskType);
    }

    /**
     * Embeds a single text string with an explicit task type parameter.
     */
    public float[] embed(String text, String taskType) {
        validateApiKey();
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null), taskType);
        if (response.getResults().isEmpty()) {
            throw new EmbeddingException("Gemini API returned no embedding results for input text.", HttpStatus.BAD_GATEWAY);
        }
        return response.getResults().get(0).getOutput();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        String taskType = TaskTypeContext.get() != null ? TaskTypeContext.get() : TASK_TYPE_RETRIEVAL_DOCUMENT;
        return embed(texts, taskType);
    }

    /**
     * Embeds multiple text strings with an explicit task type parameter.
     */
    public List<float[]> embed(List<String> texts, String taskType) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        EmbeddingResponse response = call(new EmbeddingRequest(texts, null), taskType);
        List<float[]> results = new ArrayList<>(response.getResults().size());
        for (Embedding emb : response.getResults()) {
            results.add(emb.getOutput());
        }
        return results;
    }

    @Override
    public int dimensions() {
        return this.dimension;
    }

    public String getModel() {
        return this.model;
    }

    /**
     * Normalizes a vector to unit length (Euclidean L2 norm = 1.0).
     */
    private float[] normalizeVector(float[] vector, double sumSquares) {
        double norm = Math.sqrt(sumSquares);
        if (norm <= 0.0 || Math.abs(norm - 1.0) < 1e-6) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    private String resolveTaskType(EmbeddingRequest request) {
        // 1. Check custom GeminiEmbeddingOptions
        if (request != null && request.getOptions() instanceof GeminiEmbeddingOptions geminiOptions) {
            if (geminiOptions.getTaskType() != null && !geminiOptions.getTaskType().isBlank()) {
                return geminiOptions.getTaskType().trim();
            }
        }

        // 2. Check ThreadLocal context
        String contextTaskType = TaskTypeContext.get();
        if (contextTaskType != null && !contextTaskType.isBlank()) {
            return contextTaskType.trim();
        }

        // 3. Sensible default based on instruction count (multiple = document corpus, single = query)
        if (request != null && request.getInstructions() != null && request.getInstructions().size() > 1) {
            return TASK_TYPE_RETRIEVAL_DOCUMENT;
        }
        return TASK_TYPE_RETRIEVAL_QUERY;
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmbeddingException(
                    "Google Gemini API key is missing. Please set the GEMINI_API_KEY environment variable.",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    /**
     * Sanitizes messages to guarantee no API keys or full request URLs appear in logs or errors.
     */
    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message;
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED_KEY]");
        }
        sanitized = sanitized.replaceAll("https?://[^\\s\"'<>]+", "[REDACTED_URL]");
        return sanitized;
    }
}
