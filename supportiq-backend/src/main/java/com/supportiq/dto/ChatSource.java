package com.supportiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata identifying a source policy document chunk cited in the response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSource {

    private String filename;
    private Integer pageNumber;
    private Double score;
}
