package com.supportiq.service;

import com.supportiq.dto.RagSearchRequest;
import com.supportiq.dto.RagSearchResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for testing semantic retrieval against the indexed vector embeddings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private final VectorStore vectorStore;

    /**
     * Performs similarity search for testing retrieval accuracy against indexed policy document chunks.
     *
     * @param request search request with query and topK (1-10)
     * @return list of matching chunks with similarity scores and page metadata
     */
    public List<RagSearchResultResponse> searchSimilarChunks(RagSearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return Collections.emptyList();
        }

        int topK = (request.getTopK() != null && request.getTopK() >= 1 && request.getTopK() <= 10)
                ? request.getTopK()
                : 4;

        log.info("Executing vector similarity search: topK={}", topK);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getQuery().trim())
                .topK(topK)
                .build();

        List<Document> documents = com.supportiq.service.embedding.TaskTypeContext.withTaskType(
                com.supportiq.service.embedding.GeminiEmbeddingModel.TASK_TYPE_RETRIEVAL_QUERY,
                () -> vectorStore.similaritySearch(searchRequest)
        );
        if (documents == null || documents.isEmpty()) {
            log.info("Similarity search returned 0 matching chunks");
            return Collections.emptyList();
        }

        List<RagSearchResultResponse> results = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();

            Long documentId = parseLong(metadata != null ? metadata.get("documentId") : null);
            String filename = metadata != null ? (String) metadata.get("filename") : "Unknown";
            Integer pageNumber = parseInteger(metadata != null ? metadata.get("pageNumber") : null);
            Integer chunkIndex = parseInteger(metadata != null ? metadata.get("chunkIndex") : null);
            Double score = doc.getScore() != null ? doc.getScore() : 0.0;

            results.add(RagSearchResultResponse.builder()
                    .documentId(documentId)
                    .filename(filename)
                    .pageNumber(pageNumber)
                    .chunkIndex(chunkIndex)
                    .similarityScore(score)
                    .text(doc.getText())
                    .build());
        }

        return results;
    }

    private Long parseLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Integer parseInteger(Object val) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
