package com.supportiq.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when vector database storage, indexing, or retrieval operations fail.
 */
public class VectorStoreException extends RuntimeException {

    private final HttpStatus httpStatus;

    public VectorStoreException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public VectorStoreException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public VectorStoreException(String message, Throwable cause) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public VectorStoreException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
