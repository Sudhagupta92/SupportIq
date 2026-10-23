package com.supportiq.exception;

/**
 * Thrown when communicating with the Gemini chat model times out.
 */
public class ChatTimeoutException extends RuntimeException {

    public ChatTimeoutException(String message) {
        super(message);
    }
}
