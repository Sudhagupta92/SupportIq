package com.supportiq.exception;

/**
 * Thrown when the Gemini LLM responds with HTTP 429 Too Many Requests (rate-limited / quota exhausted).
 */
public class ChatAssistantBusyException extends RuntimeException {

    public ChatAssistantBusyException(String message) {
        super(message);
    }
}
