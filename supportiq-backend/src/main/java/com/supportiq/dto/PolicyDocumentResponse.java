package com.supportiq.dto;

import com.supportiq.model.DocumentStatus;
import com.supportiq.model.PolicyDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDocumentResponse {

    private Long id;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private Integer pageCount;
    private Integer chunkCount;
    private DocumentStatus status;
    private String errorMessage;
    private Long uploadedBy;
    private LocalDateTime createdAt;

    public static PolicyDocumentResponse fromEntity(PolicyDocument doc) {
        return PolicyDocumentResponse.builder()
                .id(doc.getId())
                .filename(doc.getFilename())
                .contentType(doc.getContentType())
                .sizeBytes(doc.getSizeBytes())
                .pageCount(doc.getPageCount())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .uploadedBy(doc.getUploadedBy())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
