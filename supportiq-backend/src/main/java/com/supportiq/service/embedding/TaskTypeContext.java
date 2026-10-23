package com.supportiq.service.embedding;

import java.util.function.Supplier;

/**
 * ThreadLocal context holding the active task type for Google Gemini embedding calls
 * (e.g. RETRIEVAL_DOCUMENT for document chunks, RETRIEVAL_QUERY for search queries).
 * Allows VectorStore calls to automatically propagate the correct task type down to GeminiEmbeddingModel.
 */
public final class TaskTypeContext {

    private static final ThreadLocal<String> CURRENT_TASK_TYPE = new ThreadLocal<>();

    private TaskTypeContext() {
    }

    public static void set(String taskType) {
        CURRENT_TASK_TYPE.set(taskType);
    }

    public static String get() {
        return CURRENT_TASK_TYPE.get();
    }

    public static void clear() {
        CURRENT_TASK_TYPE.remove();
    }

    public static <T> T withTaskType(String taskType, Supplier<T> supplier) {
        String previous = CURRENT_TASK_TYPE.get();
        try {
            CURRENT_TASK_TYPE.set(taskType);
            return supplier.get();
        } finally {
            if (previous != null) {
                CURRENT_TASK_TYPE.set(previous);
            } else {
                CURRENT_TASK_TYPE.remove();
            }
        }
    }

    public static void withTaskType(String taskType, Runnable runnable) {
        String previous = CURRENT_TASK_TYPE.get();
        try {
            CURRENT_TASK_TYPE.set(taskType);
            runnable.run();
        } finally {
            if (previous != null) {
                CURRENT_TASK_TYPE.set(previous);
            } else {
                CURRENT_TASK_TYPE.remove();
            }
        }
    }
}
