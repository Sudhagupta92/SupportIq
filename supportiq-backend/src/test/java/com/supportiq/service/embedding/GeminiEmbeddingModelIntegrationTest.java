package com.supportiq.service.embedding;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test against the real Google Gemini Embedding API.
 * Automatically skipped when the GEMINI_API_KEY environment variable is not present or blank.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "^(?!\\s*$).+")
class GeminiEmbeddingModelIntegrationTest {

    private String apiKey;
    private GeminiEmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        apiKey = System.getenv("GEMINI_API_KEY");
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(),
                "Skipping live Gemini API integration test: GEMINI_API_KEY environment variable is not set."
        );

        String model = System.getenv("GEMINI_EMBEDDING_MODEL");
        if (model == null || model.isBlank()) {
            model = "gemini-embedding-001";
        }

        embeddingModel = new GeminiEmbeddingModel(apiKey, model, 768, 16);
    }

    @Test
    @DisplayName("Should generate real 768-dimensional normalized embedding for RETRIEVAL_DOCUMENT")
    void testLiveRetrievalDocumentEmbedding() {
        String documentChunk = "SupportIQ policy: customers may return unused items within 30 days of delivery.";

        float[] vector = embeddingModel.embed(documentChunk, GeminiEmbeddingModel.TASK_TYPE_RETRIEVAL_DOCUMENT);

        assertThat(vector)
                .isNotNull()
                .hasSize(768);

        // Verify that vector is L2 normalized (unit length = 1.0)
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        assertThat(Math.sqrt(sumSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    @DisplayName("Should generate real 768-dimensional normalized embedding for RETRIEVAL_QUERY")
    void testLiveRetrievalQueryEmbedding() {
        String query = "Can I return shoes after 30 days?";

        float[] vector = embeddingModel.embed(query, GeminiEmbeddingModel.TASK_TYPE_RETRIEVAL_QUERY);

        assertThat(vector)
                .isNotNull()
                .hasSize(768);

        // Verify that vector is L2 normalized (unit length = 1.0)
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        assertThat(Math.sqrt(sumSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
    }
}
