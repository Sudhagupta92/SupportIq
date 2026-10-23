package com.supportiq.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when embedding generation fails against Google Gemini or vector processing.
 * Retains the associated HTTP status to prevent generic 500 responses when upstream issues
 * (such as 404 model not found or 403 forbidden) occur.
 */
public class EmbeddingException extends RuntimeException {

    private final HttpStatus httpStatus;

    public EmbeddingException(String message) {
        this(message, HttpStatus.BAD_GATEWAY);
    }

    public EmbeddingException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus != null ? httpStatus : HttpStatus.BAD_GATEWAY;
    }

    public EmbeddingException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public EmbeddingException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus != null ? httpStatus : HttpStatus.BAD_GATEWAY;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
