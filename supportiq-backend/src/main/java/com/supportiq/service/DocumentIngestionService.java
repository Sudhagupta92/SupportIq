package com.supportiq.service;

import com.supportiq.dto.PolicyDocumentResponse;
import com.supportiq.exception.NoExtractableTextException;
import com.supportiq.exception.ResourceNotFoundException;
import com.supportiq.exception.VectorStoreException;
import com.supportiq.model.DocumentChunk;
import com.supportiq.model.DocumentStatus;
import com.supportiq.model.PageText;
import com.supportiq.model.PolicyDocument;
import com.supportiq.repository.PolicyDocumentRepository;
import com.supportiq.util.DocumentUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing the synchronous ingestion lifecycle of policy documents:
 * extraction -> chunking -> embedding batching -> vector storage -> status management.
 *
 * Structured cleanly so that execution can easily be offloaded to asynchronous background
 * worker queues in future modules without refactoring the core extraction/chunking logic.
 */
@Slf4j
@Service
public class DocumentIngestionService {

    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkingService chunkingService;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public DocumentIngestionService(
            PdfTextExtractor pdfTextExtractor,
            ChunkingService chunkingService,
            PolicyDocumentRepository policyDocumentRepository,
            VectorStore vectorStore,
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunkingService = chunkingService;
        this.policyDocumentRepository = policyDocumentRepository;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ingests an uploaded PDF document: validates, extracts text, chunks, embeds, and indexes vectors.
     * Cleans up half-stored vector chunks and records document status FAILED if any error occurs.
     *
     * @param file the uploaded PDF file
     * @param uploadedBy the ID of the administrator performing the upload
     * @return PolicyDocumentResponse with document metadata
     */
    public PolicyDocumentResponse ingestDocument(MultipartFile file, Long uploadedBy) {
        // 1. Sanitize original filename (treat all filename inputs as untrusted data)
        String safeFilename = DocumentUtils.sanitizeFilename(file.getOriginalFilename());

        // 2. Validate and extract text page by page (throws 400 or 413 or 422 if invalid)
        List<PageText> pages = pdfTextExtractor.extractPages(file);

        // 3. Initialize document metadata row with status PROCESSING
        PolicyDocument doc = PolicyDocument.builder()
                .filename(safeFilename)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/pdf")
                .sizeBytes(file.getSize())
                .pageCount(pages.size())
                .chunkCount(0)
                .status(DocumentStatus.PROCESSING)
                .uploadedBy(uploadedBy)
                .build();

        doc = policyDocumentRepository.save(doc);
        final Long documentId = doc.getId();

        try {
            // 4. Split extracted text into boundary-aware overlapping chunks
            List<DocumentChunk> chunks = chunkingService.chunkPages(pages);
            if (chunks.isEmpty()) {
                throw new NoExtractableTextException("PDF document does not contain any chunkable text.");
            }

            // 5. Convert to Spring AI Documents with deterministic UUID ids and mandatory metadata fields
            List<Document> springAiDocs = new ArrayList<>(chunks.size());
            for (DocumentChunk chunk : chunks) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("filename", safeFilename);
                metadata.put("pageNumber", chunk.pageNumber());
                metadata.put("chunkIndex", chunk.chunkIndex());

                String chunkId = UUID.nameUUIDFromBytes(
                        ("doc_" + documentId + "_chunk_" + chunk.chunkIndex()).getBytes(StandardCharsets.UTF_8)
                ).toString();

                Document springAiDoc = Document.builder()
                        .id(chunkId)
                        .text(chunk.text())
                        .metadata(metadata)
                        .build();

                springAiDocs.add(springAiDoc);
            }

            // 6. Ingest into VectorStore in small batches as RETRIEVAL_DOCUMENT
            int batchSize = 16;
            for (int i = 0; i < springAiDocs.size(); i += batchSize) {
                int toIndex = Math.min(i + batchSize, springAiDocs.size());
                List<Document> batch = springAiDocs.subList(i, toIndex);
                try {
                    com.supportiq.service.embedding.TaskTypeContext.withTaskType(
                            com.supportiq.service.embedding.GeminiEmbeddingModel.TASK_TYPE_RETRIEVAL_DOCUMENT,
                            () -> vectorStore.add(batch)
                    );
                } catch (Exception ex) {
                    // Propagate EmbeddingException without wrapping so GlobalExceptionHandler receives the exact status
                    Throwable current = ex;
                    while (current != null) {
                        if (current instanceof com.supportiq.exception.EmbeddingException embeddingException) {
                            throw embeddingException;
                        }
                        current = current.getCause();
                    }

                    log.error("Failed to store document chunks in vector store for document ID {}: {}", documentId, ex.getMessage());
                    throw new VectorStoreException("Failed to store document chunks", ex);
                }
            }

            // 7. Mark document READY on successful completion
            doc.setStatus(DocumentStatus.READY);
            doc.setChunkCount(chunks.size());
            doc.setErrorMessage(null);
            doc = policyDocumentRepository.save(doc);

            log.info("Successfully ingested policy document ID {} ({}) with {} chunks across {} pages",
                    documentId, safeFilename, chunks.size(), pages.size());

            return PolicyDocumentResponse.fromEntity(doc);

        } catch (Exception ex) {
            log.error("Failed to complete vector storage for document ID {}: {}", documentId, ex.getMessage());

            // Clean up any partially stored chunks from vectorStore
            cleanupVectorChunks(documentId);

            // Propagate EmbeddingException without wrapping so GlobalExceptionHandler receives the exact status
            Throwable current = ex;
            while (current != null) {
                if (current instanceof com.supportiq.exception.EmbeddingException embeddingException) {
                    doc.setStatus(DocumentStatus.FAILED);
                    doc.setErrorMessage(embeddingException.getMessage());
                    policyDocumentRepository.save(doc);
                    throw embeddingException;
                }
                current = current.getCause();
            }

            if (ex instanceof NoExtractableTextException noExtractableTextException) {
                doc.setStatus(DocumentStatus.FAILED);
                doc.setErrorMessage(noExtractableTextException.getMessage());
                policyDocumentRepository.save(doc);
                throw noExtractableTextException;
            }

            if (ex instanceof VectorStoreException vectorStoreException) {
                doc.setStatus(DocumentStatus.FAILED);
                doc.setErrorMessage("Failed to store document chunks");
                policyDocumentRepository.save(doc);
                throw vectorStoreException;
            }

            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage("Failed to store document chunks");
            policyDocumentRepository.save(doc);

            throw new VectorStoreException("Failed to store document chunks", ex);
        }
    }

    /**
     * Lists all uploaded policy documents, ordered newest first.
     */
    @Transactional(readOnly = true)
    public List<PolicyDocumentResponse> getAllDocuments() {
        return policyDocumentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(PolicyDocumentResponse::fromEntity)
                .toList();
    }

    /**
     * Deletes a policy document metadata row and all associated vector chunks from the vector store.
     *
     * @param documentId ID of the document to delete
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        PolicyDocument doc = policyDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy document not found with id: " + documentId));

        // 1. Purge all vector chunks associated with this documentId
        cleanupVectorChunks(documentId);

        // 2. Remove the metadata entity
        policyDocumentRepository.delete(doc);
        log.info("Deleted policy document ID {} and purged its vector store embeddings", documentId);
    }

    /**
     * Cleans up all vector chunks in the vector database matching the specified documentId.
     */
    public void cleanupVectorChunks(Long documentId) {
        if (documentId == null) {
            return;
        }

        try {
            // Attempt deletion via Spring AI VectorStore expression
            vectorStore.delete("documentId == " + documentId);
        } catch (Exception ex) {
            log.debug("VectorStore filter delete encountered: {}", ex.getMessage());
        }

        // Direct SQL safety cleanup if JdbcTemplate is available (for PostgreSQL pgvector)
        if (jdbcTemplate != null) {
            try {
                int purged = jdbcTemplate.update(
                        "DELETE FROM vector_store WHERE (metadata->>'documentId') = ?",
                        String.valueOf(documentId)
                );
                log.info("Purged {} chunks for documentId {} via direct vector table cleanup", purged, documentId);
            } catch (Exception ex) {
                log.debug("Direct SQL purge skipped: {}", ex.getMessage());
            }
        }
    }
}
