package com.supportiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload returned to the user for a chat query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String answer;
    private boolean answered;
    @Builder.Default
    private List<ChatSource> sources = new ArrayList<>();
    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();
}
