package com.supportiq.exception;

/**
 * Thrown when a user exceeds the allowed chat requests rate limit.
 */
public class ChatRateLimitException extends RuntimeException {

    public ChatRateLimitException(String message) {
        super(message);
    }
}
