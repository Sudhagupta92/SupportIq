package com.supportiq.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * JUnit 5 ExecutionCondition that checks if PostgreSQL is reachable on localhost:5432.
 * Evaluated before Spring TestContext initializes, so the test is cleanly skipped
 * without failing context initialization if Docker PostgreSQL is not running.
 */
public class PostgresReachableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 1000);
            return ConditionEvaluationResult.enabled("PostgreSQL is reachable on localhost:5432");
        } catch (Exception e) {
            return ConditionEvaluationResult.disabled(
                    "Skipping full context integration test: PostgreSQL is not reachable on localhost:5432 (" + e.getMessage() + ")"
            );
        }
    }
}
