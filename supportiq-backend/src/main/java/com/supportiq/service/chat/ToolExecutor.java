package com.supportiq.service.chat;

import java.util.Map;

/**
 * Functional callback interface for executing model tool calls.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Executes the requested tool function.
     *
     * @param functionName name of the tool function
     * @param arguments map of arguments provided by the model
     * @return tool execution result
     */
    ToolExecutionResult execute(String functionName, Map<String, Object> arguments);
}
