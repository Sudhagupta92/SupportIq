package com.supportiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchResultResponse {

    private Long documentId;
    private String filename;
    private Integer pageNumber;
    private Integer chunkIndex;
    private Double similarityScore;
    private String text;
}
