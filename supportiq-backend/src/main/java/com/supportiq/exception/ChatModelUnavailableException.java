package com.supportiq.exception;

/**
 * Thrown when the Gemini chat model returns 404 (Not Found) or 403 (Forbidden).
 */
public class ChatModelUnavailableException extends RuntimeException {

    public ChatModelUnavailableException(String message) {
        super(message);
    }
}
