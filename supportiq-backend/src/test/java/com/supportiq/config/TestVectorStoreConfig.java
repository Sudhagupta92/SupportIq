package com.supportiq.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration providing a fallback in-memory SimpleVectorStore for automated tests
 * running under the 'test' profile (which uses H2 instead of PostgreSQL + pgvector).
 *
 * This configuration is placed in src/test/java and only loaded when the 'test' profile is active,
 * guaranteeing zero bean definition conflicts with Spring AI's PgVectorStoreAutoConfiguration
 * in production.
 */
@Configuration
@Profile("test")
public class TestVectorStoreConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore testVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
