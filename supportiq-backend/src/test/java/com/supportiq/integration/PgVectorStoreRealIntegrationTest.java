package com.supportiq.integration;

import com.supportiq.dto.PolicyDocumentResponse;
import com.supportiq.dto.RagSearchRequest;
import com.supportiq.dto.RagSearchResultResponse;
import com.supportiq.model.DocumentStatus;
import com.supportiq.repository.PolicyDocumentRepository;
import com.supportiq.service.DocumentIngestionService;
import com.supportiq.service.RagSearchService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Integration test validating chunk indexing, similarity search, and metadata-based deletion
 * against the REAL PgVectorStore and PostgreSQL docker container.
 *
 * Automatically skipped when PostgreSQL is not reachable on localhost:5432.
 * Never uses SimpleVectorStore because SimpleVectorStore accepts arbitrary string IDs and masks
 * UUID conversion errors.
 */
@Tag("integration")
@ExtendWith(PostgresReachableCondition.class)
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/supportiq",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.vectorstore.pgvector.initialize-schema=true",
        "spring.ai.vectorstore.pgvector.id-type=uuid",
        "app.rag.gemini.api-key=test-mock-key"
})
class PgVectorStoreRealIntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentIngestionService documentIngestionService;

    @Autowired
    private RagSearchService ragSearchService;

    @Autowired
    private PolicyDocumentRepository policyDocumentRepository;

    @MockBean
    private EmbeddingModel embeddingModel;

    private static final int VECTOR_DIMENSION = 768;
    private final float[] fixedVector = new float[VECTOR_DIMENSION];

    @BeforeEach
    void setUp() {
        // Prepare normalized fixed 768-dimension vector
        float unitVal = (float) (1.0 / Math.sqrt(VECTOR_DIMENSION));
        Arrays.fill(fixedVector, unitVal);

        when(embeddingModel.dimensions()).thenReturn(VECTOR_DIMENSION);
        when(embeddingModel.embed(any(Document.class))).thenReturn(fixedVector);
        when(embeddingModel.embed(any(String.class))).thenReturn(fixedVector);
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<?> list = inv.getArgument(0);
            return list.stream().map(x -> fixedVector).toList();
        });
        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class))).thenAnswer(inv -> {
            List<?> list = inv.getArgument(0);
            return list.stream().map(x -> fixedVector).toList();
        });
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
            EmbeddingRequest req = inv.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < req.getInstructions().size(); i++) {
                embeddings.add(new Embedding(fixedVector, i));
            }
            return new EmbeddingResponse(embeddings);
        });
    }

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("PgVectorStore must be the real PgVectorStore and not SimpleVectorStore")
    void verifyRealPgVectorStoreIsActive() {
        assertThat(vectorStore)
                .as("VectorStore must be a real PgVectorStore instance")
                .isNotNull()
                .isInstanceOf(PgVectorStore.class);
    }

    @Test
    @DisplayName("PgVectorStore should reject non-UUID document IDs and throw IllegalArgumentException")
    void nonUuidIdThrowsIllegalArgumentExceptionInPgVectorStore() {
        Document legacyDoc = Document.builder()
                .id("doc_999_chunk_0") // Non-UUID ID that previously caused the bug
                .text("Legacy format text")
                .metadata(Map.of("documentId", 999L))
                .build();

        assertThatThrownBy(() -> vectorStore.add(List.of(legacyDoc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid UUID string: doc_999_chunk_0");
    }

    @Test
    @DisplayName("Real PgVectorStore should add chunks with deterministic UUIDs, search, delete by documentId, and confirm gone")
    void testAddSearchDeleteAndConfirmGoneWithRealPgVectorStore() {
        final Long documentId = 424242L;
        final String filename = "real-pgvector-test.pdf";

        // 1. Generate chunks with deterministic UUID IDs exactly as DocumentIngestionService does
        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String chunkId = UUID.nameUUIDFromBytes(
                    ("doc_" + documentId + "_chunk_" + i).getBytes(StandardCharsets.UTF_8)
            ).toString();

            Document chunk = Document.builder()
                    .id(chunkId)
                    .text("Policy details for document " + documentId + " chunk " + i + ": 30-day money back guarantee.")
                    .metadata(Map.of(
                            "documentId", documentId,
                            "filename", filename,
                            "pageNumber", 1,
                            "chunkIndex", i
                    ))
                    .build();

            chunks.add(chunk);
        }

        // 2. Add chunks to the real PgVectorStore
        vectorStore.add(chunks);

        // Verify rows exist in PostgreSQL vector_store table
        Integer countInDb = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE (metadata->>'documentId') = ?",
                Integer.class,
                String.valueOf(documentId)
        );
        assertThat(countInDb).isEqualTo(3);

        // 3. Run similarity search to verify retrieval
        SearchRequest searchRequest = SearchRequest.builder()
                .query("money back guarantee")
                .topK(5)
                .filterExpression("documentId == " + documentId)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        assertThat(results)
                .isNotEmpty()
                .hasSize(3);

        for (Document res : results) {
            assertThat(res.getMetadata().get("documentId")).hasToString(String.valueOf(documentId));
            assertThat(res.getMetadata().get("filename")).isEqualTo(filename);
            assertThat(res.getText()).contains("30-day money back guarantee");
        }

        // 4. Delete chunks by documentId using the metadata filter
        vectorStore.delete("documentId == " + documentId);

        // 5. Confirm they are completely gone
        List<Document> afterDeleteResults = vectorStore.similaritySearch(searchRequest);
        assertThat(afterDeleteResults)
                .as("Search results should be empty after deleting by documentId")
                .isEmpty();

        Integer remainingInDb = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE (metadata->>'documentId') = ?",
                Integer.class,
                String.valueOf(documentId)
        );
        assertThat(remainingInDb)
                .as("Database row count for documentId should be 0 after delete")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("End-to-end ingestion lifecycle with DocumentIngestionService and real PgVectorStore")
    void testEndToEndIngestionLifecycleWithRealPgVectorStore() throws Exception {
        byte[] pdfBytes = createPdf("SupportIQ warranty covers hardware defects for up to two full years.");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "warranty-terms.pdf",
                "application/pdf",
                pdfBytes
        );

        // Ingest through service
        PolicyDocumentResponse response = documentIngestionService.ingestDocument(file, 1L);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(response.getChunkCount()).isGreaterThan(0);

        Long docId = response.getId();

        // Search through RagSearchService
        RagSearchRequest searchReq = RagSearchRequest.builder()
                .query("warranty hardware defects")
                .topK(3)
                .build();
        List<RagSearchResultResponse> searchResults = ragSearchService.searchSimilarChunks(searchReq);
        assertThat(searchResults).isNotEmpty();
        assertThat(searchResults.stream().anyMatch(r -> docId.equals(r.getDocumentId()))).isTrue();

        // Delete through DocumentIngestionService
        documentIngestionService.deleteDocument(docId);

        // Confirm database and vectorStore no longer contain chunks for this document
        assertThat(policyDocumentRepository.findById(docId)).isEmpty();

        Integer remainingChunks = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE (metadata->>'documentId') = ?",
                Integer.class,
                String.valueOf(docId)
        );
        assertThat(remainingChunks).isEqualTo(0);
    }
}
