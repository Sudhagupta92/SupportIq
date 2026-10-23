package com.supportiq.service.embedding;

import com.supportiq.exception.EmbeddingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiEmbeddingModelTest {

    @Test
    @DisplayName("Should use gemini-embedding-001 as default model and send outputDimensionality 768")
    void testDefaultModelAndOutputDimensionality() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiEmbeddingModel model = new GeminiEmbeddingModel("test-key", null, 768, 16, restClient);
        assertThat(model.getModel()).isEqualTo("gemini-embedding-001");
        assertThat(model.dimensions()).isEqualTo(768);

        // Build sample 768-dim response values
        StringBuilder valuesArray = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            valuesArray.append("0.05").append(i < 767 ? "," : "");
        }
        valuesArray.append("]");

        String jsonResponse = "{\"embeddings\": [{\"values\": " + valuesArray + "}]}";

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.requests[0].model").value("models/gemini-embedding-001"))
                .andExpect(jsonPath("$.requests[0].outputDimensionality").value(768))
                .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_DOCUMENT"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<float[]> embeddings = model.embed(List.of("Test policy text"), "RETRIEVAL_DOCUMENT");
        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0)).hasSize(768);

        // Verify L2 normalization: Euclidean norm of returned vector must be 1.0
        float[] vec = embeddings.get(0);
        double sumSquares = 0.0;
        for (float f : vec) {
            sumSquares += (double) f * f;
        }
        assertThat(Math.sqrt(sumSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));

        server.verify();
    }

    @Test
    @DisplayName("Should send RETRIEVAL_QUERY task type when requested")
    void testRetrievalQueryTaskType() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiEmbeddingModel model = new GeminiEmbeddingModel("test-key", "gemini-embedding-001", 768, 16, restClient);

        StringBuilder valuesArray = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            valuesArray.append("0.02").append(i < 767 ? "," : "");
        }
        valuesArray.append("]");

        String jsonResponse = "{\"embeddings\": [{\"values\": " + valuesArray + "}]}";

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_QUERY"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        float[] vector = model.embed("What is the return window?", "RETRIEVAL_QUERY");
        assertThat(vector).hasSize(768);

        server.verify();
    }

    @Test
    @DisplayName("Should throw clear error with status BAD_GATEWAY when Google returns 404 (model not found)")
    void testGoogle404ReturnsClearMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiEmbeddingModel model = new GeminiEmbeddingModel("test-key", "gemini-embedding-001", 768, 16, restClient);

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> model.embed("Sample text", "RETRIEVAL_DOCUMENT"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessage("Embedding model not available. Check GEMINI_EMBEDDING_MODEL and the API key")
                .satisfies(ex -> {
                    EmbeddingException embEx = (EmbeddingException) ex;
                    assertThat(embEx.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });

        server.verify();
    }

    @Test
    @DisplayName("Should throw clear error with status BAD_GATEWAY when Google returns 403 (forbidden)")
    void testGoogle403ReturnsClearMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiEmbeddingModel model = new GeminiEmbeddingModel("test-key", "gemini-embedding-001", 768, 16, restClient);

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> model.embed("Sample text", "RETRIEVAL_DOCUMENT"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessage("Embedding model not available. Check GEMINI_EMBEDDING_MODEL and the API key")
                .satisfies(ex -> {
                    EmbeddingException embEx = (EmbeddingException) ex;
                    assertThat(embEx.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });

        server.verify();
    }

    @Test
    @DisplayName("Should throw clear error when returned vector length does not match configured dimension")
    void testDimensionMismatchThrowsClearError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GeminiEmbeddingModel model = new GeminiEmbeddingModel("test-key", "gemini-embedding-001", 768, 16, restClient);

        // Simulate model returning default 3072 dimensions instead of configured 768
        StringBuilder valuesArray = new StringBuilder("[");
        for (int i = 0; i < 3072; i++) {
            valuesArray.append("0.01").append(i < 3071 ? "," : "");
        }
        valuesArray.append("]");

        String jsonResponse = "{\"embeddings\": [{\"values\": " + valuesArray + "}]}";

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> model.embed("Sample text", "RETRIEVAL_DOCUMENT"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Returned vector length (3072) does not match configured vector dimension (768)");

        server.verify();
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when GEMINI_API_KEY is missing or blank")
    void testMissingApiKeyThrowsUnauthorized() {
        GeminiEmbeddingModel model = new GeminiEmbeddingModel("", "gemini-embedding-001", 768, 16);

        assertThatThrownBy(() -> model.embed("Sample text"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Google Gemini API key is missing")
                .satisfies(ex -> {
                    EmbeddingException embEx = (EmbeddingException) ex;
                    assertThat(embEx.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
