package com.supportiq.service.chat;

import com.supportiq.dto.TicketSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Result of a Gemini chat invocation with potential tool execution metadata.
 */
@Getter
@AllArgsConstructor
public class GeminiChatResult {

    private final String answer;
    private final List<String> toolsUsed;
    private final List<String> orderNumbersReferenced;
    private final TicketSummaryDto createdTicket;

    public GeminiChatResult(String answer, List<String> toolsUsed, List<String> orderNumbersReferenced) {
        this(answer, toolsUsed, orderNumbersReferenced, null);
    }

    public static GeminiChatResult textOnly(String answer) {
        return new GeminiChatResult(answer, Collections.emptyList(), Collections.emptyList(), null);
    }
}
