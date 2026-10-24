package com.supportiq.service.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the execution result of an LLM tool call.
 *
 * @param responseData raw data map returned to the LLM inside functionResponse
 * @param referencedOrderNumbers order numbers referenced or accessed
 */
public record ToolExecutionResult(
        Map<String, Object> responseData,
        List<String> referencedOrderNumbers
) {
    public ToolExecutionResult(Map<String, Object> responseData, String singleOrderNumber) {
        this(responseData, singleOrderNumber != null ? List.of(singleOrderNumber) : Collections.emptyList());
    }
}
