package com.supportiq.service.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Options for Gemini Embedding requests, including task_type and output_dimensionality.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiEmbeddingOptions implements EmbeddingOptions {

    @Builder.Default
    private String model = "gemini-embedding-001";

    @Builder.Default
    private Integer dimensions = 768;

    @Builder.Default
    private String taskType = "RETRIEVAL_DOCUMENT";

    @Override
    public String getModel() {
        return this.model;
    }

    @Override
    public Integer getDimensions() {
        return this.dimensions;
    }
}
