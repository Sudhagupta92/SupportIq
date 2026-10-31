package com.supportiq.service.ticket;

import com.supportiq.exception.InvalidStatusTransitionException;
import com.supportiq.model.TicketStatus;
import org.springframework.stereotype.Component;

/**
 * Validates ticket status transitions according to strict business state rules.
 * 
 * Allowed transitions:
 * - OPEN -> IN_PROGRESS
 * - IN_PROGRESS -> RESOLVED
 * - RESOLVED -> CLOSED
 * - RESOLVED -> OPEN (reopen)
 * - OPEN -> RESOLVED
 * - ANY (OPEN, IN_PROGRESS, RESOLVED) -> CLOSED
 * 
 * Everything else is rejected with 400 Bad Request.
 */
@Component
public class TicketStatusValidator {

    public void validateTransition(TicketStatus currentStatus, TicketStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new InvalidStatusTransitionException("Status cannot be null");
        }

        if (currentStatus == targetStatus) {
            throw new InvalidStatusTransitionException(
                    "Ticket is already in status " + currentStatus + ". Cannot transition to the same status."
            );
        }

        boolean allowed = switch (currentStatus) {
            case OPEN -> targetStatus == TicketStatus.IN_PROGRESS
                    || targetStatus == TicketStatus.RESOLVED
                    || targetStatus == TicketStatus.CLOSED;

            case IN_PROGRESS -> targetStatus == TicketStatus.RESOLVED
                    || targetStatus == TicketStatus.CLOSED;

            case RESOLVED -> targetStatus == TicketStatus.CLOSED
                    || targetStatus == TicketStatus.OPEN;

            case CLOSED -> false; // A closed ticket cannot transition to any other status
        };

        if (!allowed) {
            throw new InvalidStatusTransitionException(
                    "Invalid status transition from " + currentStatus + " to " + targetStatus + "."
            );
        }
    }
}
