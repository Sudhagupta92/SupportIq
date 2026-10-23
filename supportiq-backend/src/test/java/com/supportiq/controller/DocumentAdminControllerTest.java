package com.supportiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.dto.RagSearchRequest;
import com.supportiq.model.PolicyDocument;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.PolicyDocumentRepository;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.JwtService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PolicyDocumentRepository policyDocumentRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private VectorStore vectorStore;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        policyDocumentRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create and persist Admin user
        User adminUser = User.builder()
                .name("Test Administrator")
                .email("admin.test@supportiq.com")
                .password("Password@123")
                .role(Role.ADMIN)
                .build();
        adminUser = userRepository.save(adminUser);

        adminToken = jwtService.generateToken(adminUser);

        // 2. Create and persist Customer user
        User customerUser = User.builder()
                .name("Normal Customer")
                .email("customer.test@supportiq.com")
                .password("Password@123")
                .role(Role.CUSTOMER)
                .build();
        customerUser = userRepository.save(customerUser);

        customerToken = jwtService.generateToken(customerUser);

        // Mock VectorStore operations
        doNothing().when(vectorStore).add(anyList());
        doNothing().when(vectorStore).delete(any(String.class));
    }

    private byte[] createTestPdf(String text) throws IOException {
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
    @DisplayName("CUSTOMER role accessing /api/admin/documents should return 403 Forbidden")
    void customerAccessingAdminEndpointsReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/documents")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message", containsString("Access denied")));
    }

    @Test
    @DisplayName("Unauthenticated request accessing /api/admin/documents should return 401 Unauthorized")
    void unauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("ADMIN uploading non-PDF file should return 400 Bad Request")
    void adminUploadingInvalidNonPdfReturns400() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "not-a-pdf.txt",
                "text/plain",
                "This is simple plain text, not a PDF document.".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("Invalid file type")));
    }

    @Test
    @DisplayName("ADMIN uploading file with forged extension but invalid magic bytes should return 400 Bad Request")
    void adminUploadingForgedExtensionReturns400() throws Exception {
        MockMultipartFile forgedFile = new MockMultipartFile(
                "file",
                "forged.pdf",
                "application/pdf",
                "FAKE BYTES NOT STARTING WITH %PDF".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(forgedFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("magic header bytes")));
    }

    @Test
    @DisplayName("ADMIN uploading valid PDF should succeed and return 201 Created with document metadata")
    void adminUploadingValidPdfSucceeds() throws Exception {
        byte[] pdfBytes = createTestPdf("SupportIQ return policy allows 30 days for undamaged items.");

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "store-policy.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.filename").value("store-policy.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sizeBytes").value(pdfBytes.length));
    }

    @Test
    @DisplayName("ADMIN listing documents should return 200 OK with list of documents newest first")
    void adminListingDocumentsReturns200() throws Exception {
        // Upload a document first
        byte[] pdfBytes = createTestPdf("Shipping rules and guidelines.");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "shipping-rules.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename").value("shipping-rules.pdf"))
                .andExpect(jsonPath("$[0].status").value("READY"));
    }

    @Test
    @DisplayName("ADMIN deleting existing document should return 204 No Content")
    void adminDeletingDocumentReturns204() throws Exception {
        byte[] pdfBytes = createTestPdf("Exchange policy details.");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "exchange-policy.pdf",
                "application/pdf",
                pdfBytes
        );

        String response = mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PolicyDocument doc = policyDocumentRepository.findAll().get(0);
        Long docId = doc.getId();

        mockMvc.perform(delete("/api/admin/documents/" + docId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(policyDocumentRepository.findById(docId)).isEmpty();
    }

    @Test
    @DisplayName("ADMIN deleting non-existent document ID should return 404 Not Found")
    void adminDeletingNonExistentDocumentReturns404() throws Exception {
        mockMvc.perform(delete("/api/admin/documents/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("ADMIN testing retrieval search should return 200 OK with matched chunks")
    void adminSearchingDocumentsReturns200() throws Exception {
        Document matchedDoc = Document.builder()
                .id(java.util.UUID.nameUUIDFromBytes("doc_1_chunk_0".getBytes()).toString())
                .text("SupportIQ items can be returned within 30 days.")
                .metadata(Map.of(
                        "documentId", 1L,
                        "filename", "store-policy.pdf",
                        "pageNumber", 1,
                        "chunkIndex", 0
                ))
                .score(0.885)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(matchedDoc));

        RagSearchRequest searchRequest = RagSearchRequest.builder()
                .query("What is the return window?")
                .topK(4)
                .build();

        mockMvc.perform(post("/api/admin/documents/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename").value("store-policy.pdf"))
                .andExpect(jsonPath("$[0].pageNumber").value(1))
                .andExpect(jsonPath("$[0].similarityScore").value(0.885))
                .andExpect(jsonPath("$[0].text", containsString("30 days")));
    }

    @Test
    @DisplayName("ADMIN uploading PDF with no extractable text should return 422 Unprocessable Entity")
    void adminUploadingScannedBlankPdfReturns422() throws Exception {
        byte[] blankPdf = createTestPdf(""); // Blank page with no text content

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "blank-scanned.pdf",
                "application/pdf",
                blankPdf
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message", containsString("no extractable text")));
    }

    @Test
    @DisplayName("ADMIN uploading file exceeding 10 MB limit should return 413 Payload Too Large")
    void adminUploadingFileExceedingMaxSizeReturns413() throws Exception {
        // Exceed 10 MB (10,485,760 bytes)
        byte[] largeBytes = new byte[10485760 + 10];
        largeBytes[0] = 0x25; largeBytes[1] = 0x50; largeBytes[2] = 0x44; largeBytes[3] = 0x46; // %PDF

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "huge.pdf",
                "application/pdf",
                largeBytes
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.message", containsString("exceeds the maximum allowed limit")));
    }

    @Test
    @DisplayName("ADMIN searching with invalid parameters should return 400 Bad Request")
    void adminSearchingWithInvalidParamsReturns400() throws Exception {
        RagSearchRequest invalidRequest = RagSearchRequest.builder()
                .query("") // Blank query violates @NotBlank
                .topK(20) // Exceeds @Max(10)
                .build();

        mockMvc.perform(post("/api/admin/documents/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.query", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.topK", notNullValue()));
    }

    @Test
    @DisplayName("ADMIN uploading PDF when vector store storage fails should return 500 with specific message 'Failed to store document chunks'")
    void adminUploadingPdfWhenVectorStoreFailsReturns500WithSpecificMessage() throws Exception {
        // Simulate a vector store failure (e.g. database error, UUID conversion, etc.)
        doThrow(new RuntimeException("PostgreSQL internal storage connection error: password=secret_db_pass"))
                .when(vectorStore).add(anyList());

        byte[] pdfBytes = createTestPdf("Valid policy content for testing vector store failure.");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "failure-test.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Failed to store document chunks"));

        // Verify document is recorded as FAILED with the safe error message
        List<PolicyDocument> docs = policyDocumentRepository.findAll();
        assertThat(docs).hasSize(1);
        PolicyDocument failedDoc = docs.get(0);
        assertThat(failedDoc.getStatus()).isEqualTo(com.supportiq.model.DocumentStatus.FAILED);
        assertThat(failedDoc.getErrorMessage()).isEqualTo("Failed to store document chunks");
    }
}

