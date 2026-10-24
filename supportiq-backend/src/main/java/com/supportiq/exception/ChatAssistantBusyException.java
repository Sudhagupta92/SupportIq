package com.supportiq.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the Gemini LLM responds with HTTP 429 (rate-limited / quota exhausted)
 * or HTTP 503 (service unavailable / assistant busy).
 */
public class ChatAssistantBusyException extends RuntimeException {

    private final HttpStatus httpStatus;

    public ChatAssistantBusyException(String message) {
        super(message);
        this.httpStatus = HttpStatus.TOO_MANY_REQUESTS;
    }

    public ChatAssistantBusyException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = (httpStatus != null) ? httpStatus : HttpStatus.TOO_MANY_REQUESTS;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
