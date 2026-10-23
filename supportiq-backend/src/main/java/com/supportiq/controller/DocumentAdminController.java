package com.supportiq.controller;

import com.supportiq.dto.PolicyDocumentResponse;
import com.supportiq.dto.RagSearchRequest;
import com.supportiq.dto.RagSearchResultResponse;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.DocumentIngestionService;
import com.supportiq.service.RagSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller handling policy document uploads, metadata management, and retrieval testing.
 * Protected by Spring Security and accessible exclusively to users with the ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DocumentAdminController {

    private final DocumentIngestionService documentIngestionService;
    private final RagSearchService ragSearchService;
    private final UserRepository userRepository;

    /**
     * Uploads and synchronously ingests a policy PDF document.
     * Validates magic bytes, file size, extracts text with PDFBox 3.x, chunks, and stores embeddings.
     *
     * @param file the PDF file (multipart/form-data field name: "file")
     * @param authentication security authentication principal
     * @return 201 Created with document metadata
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PolicyDocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        Long uploaderId = resolveUserId(authentication);
        PolicyDocumentResponse response = documentIngestionService.ingestDocument(file, uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all uploaded policy documents, ordered newest first, with status and chunk count.
     */
    @GetMapping
    public ResponseEntity<List<PolicyDocumentResponse>> listDocuments() {
        List<PolicyDocumentResponse> documents = documentIngestionService.getAllDocuments();
        return ResponseEntity.ok(documents);
    }

    /**
     * Deletes a policy document metadata row and all associated vector chunks from the vector store.
     * Returns 404 if not found.
     *
     * @param id the document ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") Long id) {
        documentIngestionService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tests retrieval against indexed embeddings using similarity search.
     *
     * @param searchRequest contains query string and topK (1-10)
     * @return list of matching chunks with similarity score, page number, and chunk text
     */
    @PostMapping("/search")
    public ResponseEntity<List<RagSearchResultResponse>> searchDocuments(
            @Valid @RequestBody RagSearchRequest searchRequest) {

        List<RagSearchResultResponse> results = ragSearchService.searchSimilarChunks(searchRequest);
        return ResponseEntity.ok(results);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }
}
