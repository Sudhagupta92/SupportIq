package com.supportiq.config;

import com.supportiq.service.embedding.GeminiEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration for embeddings and vector storage.
 *
 * Keeps the embedding model behind Spring AI's {@link EmbeddingModel} interface
 * so the underlying embedding provider can be swapped at any time.
 */
@Configuration
public class RagConfig {

    /**
     * Declares the default GeminiEmbeddingModel bean if no other EmbeddingModel is present
     * (e.g. in automated tests where an EmbeddingModel mock is provided).
     *
     * Environment variables configured:
     * - GEMINI_API_KEY: Authentication key for Google AI Studio
     * - GEMINI_EMBEDDING_MODEL: Embedding model name (defaults to gemini-embedding-001)
     * - VECTOR_DIMENSION: Vector length (768 for gemini-embedding-001 with outputDimensionality 768; matches pgvector)
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel(
            @Value("${app.rag.gemini.api-key:}") String apiKey,
            @Value("${app.rag.gemini.model:gemini-embedding-001}") String model,
            @Value("${app.rag.gemini.dimension:768}") int dimension,
            @Value("${app.rag.gemini.batch-size:16}") int batchSize) {
        return new GeminiEmbeddingModel(apiKey, model, dimension, batchSize);
    }
}
