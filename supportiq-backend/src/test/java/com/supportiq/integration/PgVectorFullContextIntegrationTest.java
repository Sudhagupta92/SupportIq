package com.supportiq.integration;

import com.supportiq.service.embedding.GeminiEmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the FULL Spring Boot application context against the real PostgreSQL container
 * to catch startup wiring errors, verify PgVectorStoreAutoConfiguration, and confirm
 * that only one VectorStore bean and one EmbeddingModel bean are registered.
 * Automatically skipped when PostgreSQL on localhost:5432 is not reachable.
 */
@Tag("integration")
@ExtendWith(PostgresReachableCondition.class)
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/supportiq",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "app.rag.gemini.api-key=test-key-mock"
})
class PgVectorFullContextIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    @DisplayName("Should successfully boot full Spring context and auto-wire PgVectorStore with GeminiEmbeddingModel")
    void testFullContextBootsWithPgVectorStoreAndGeminiEmbeddingModel() {
        // 1. Verify ApplicationContext loaded
        assertThat(applicationContext).isNotNull();

        // 2. Verify vectorStore bean exists, is a PgVectorStore instance, and is unique
        assertThat(vectorStore)
                .isNotNull()
                .isInstanceOf(PgVectorStore.class);

        String[] vectorStoreBeans = applicationContext.getBeanNamesForType(VectorStore.class);
        assertThat(vectorStoreBeans)
                .as("Only one VectorStore bean should be registered in the application context")
                .hasSize(1)
                .contains("vectorStore");

        // 3. Verify embeddingModel bean exists, is our GeminiEmbeddingModel instance, and is unique
        assertThat(embeddingModel)
                .isNotNull()
                .isInstanceOf(GeminiEmbeddingModel.class);

        String[] embeddingModelBeans = applicationContext.getBeanNamesForType(EmbeddingModel.class);
        assertThat(embeddingModelBeans)
                .as("Only one EmbeddingModel bean should be registered in the application context")
                .hasSize(1);

        GeminiEmbeddingModel geminiModel = (GeminiEmbeddingModel) embeddingModel;
        assertThat(geminiModel.getModel()).isEqualTo("gemini-embedding-001");
        assertThat(geminiModel.dimensions()).isEqualTo(768);
    }
}
