package com.supportiq.exception;

/**
 * Thrown for general failures or upstream HTTP errors from the Gemini chat service.
 */
public class ChatServiceException extends RuntimeException {

    public ChatServiceException(String message) {
        super(message);
    }

    public ChatServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
