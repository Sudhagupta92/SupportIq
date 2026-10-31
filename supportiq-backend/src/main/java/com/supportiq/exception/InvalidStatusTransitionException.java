package com.supportiq.exception;

/**
 * Exception thrown when a ticket status transition violates the allowed workflow state machine.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
