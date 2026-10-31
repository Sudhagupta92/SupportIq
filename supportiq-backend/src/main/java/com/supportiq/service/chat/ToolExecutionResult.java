package com.supportiq.service.chat;

import com.supportiq.dto.TicketSummaryDto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the execution result of an LLM tool call.
 *
 * @param responseData raw data map returned to the LLM inside functionResponse
 * @param referencedOrderNumbers order numbers referenced or accessed
 * @param createdTicket support ticket summary if created by this tool execution
 */
public record ToolExecutionResult(
        Map<String, Object> responseData,
        List<String> referencedOrderNumbers,
        TicketSummaryDto createdTicket
) {
    public ToolExecutionResult(Map<String, Object> responseData, List<String> referencedOrderNumbers) {
        this(responseData, referencedOrderNumbers, null);
    }

    public ToolExecutionResult(Map<String, Object> responseData, String singleOrderNumber) {
        this(responseData, singleOrderNumber != null ? List.of(singleOrderNumber) : Collections.emptyList(), null);
    }
}
