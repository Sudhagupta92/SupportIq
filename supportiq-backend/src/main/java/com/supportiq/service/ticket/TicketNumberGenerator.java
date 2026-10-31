package com.supportiq.service.ticket;

import com.supportiq.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe generator for ticket numbers formatted as TKT-1001, TKT-1002, etc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketNumberGenerator {

    private static final String PREFIX = "TKT-";
    private static final int BASE_NUMBER = 1000;
    private static final Pattern TICKET_NUMBER_PATTERN = Pattern.compile("^TKT-(\\d+)$");

    private final TicketRepository ticketRepository;

    /**
     * Generates the next sequential unique ticket number.
     * Starts at TKT-1001 if no tickets exist.
     */
    public synchronized String generateNextTicketNumber() {
        List<String> latestTickets = ticketRepository.findLatestTicketNumbers(PageRequest.of(0, 20));

        int maxNumber = BASE_NUMBER;
        for (String ticketNum : latestTickets) {
            if (ticketNum != null) {
                Matcher matcher = TICKET_NUMBER_PATTERN.matcher(ticketNum.trim());
                if (matcher.matches()) {
                    try {
                        int num = Integer.parseInt(matcher.group(1));
                        if (num > maxNumber) {
                            maxNumber = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        String nextTicketNumber = PREFIX + (maxNumber + 1);
        while (ticketRepository.existsByTicketNumber(nextTicketNumber)) {
            maxNumber++;
            nextTicketNumber = PREFIX + (maxNumber + 1);
        }

        return nextTicketNumber;
    }
}
