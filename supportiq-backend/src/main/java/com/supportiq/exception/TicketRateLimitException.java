package com.supportiq.exception;

/**
 * Exception thrown when a user exceeds the daily ticket creation limit (e.g. 5 tickets per 24 hours).
 */
public class TicketRateLimitException extends RuntimeException {

    public TicketRateLimitException(String message) {
        super(message);
    }
}
