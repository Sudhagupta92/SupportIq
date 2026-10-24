package com.supportiq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata identifying a source policy document chunk or order cited in the response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSource {

    @Builder.Default
    private ChatSourceType type = ChatSourceType.POLICY;
    private String filename;
    private Integer pageNumber;
    private Double score;
    private String label;
}
