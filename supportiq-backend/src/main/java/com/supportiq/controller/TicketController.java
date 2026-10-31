package com.supportiq.controller;

import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.PageResponse;
import com.supportiq.dto.TicketDetailResponse;
import com.supportiq.dto.TicketMessageRequest;
import com.supportiq.dto.TicketResponse;
import com.supportiq.exception.ResourceNotFoundException;
import com.supportiq.model.TicketSource;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.ticket.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer ticket endpoints.
 * Accessible to any authenticated user (CUSTOMER or ADMIN), always scoped to the logged-in principal.
 */
@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final UserRepository userRepository;

    /**
     * Creates a new support ticket or updates an existing one for duplicate order inquiries.
     */
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        TicketResponse response = ticketService.createTicket(userId, request, TicketSource.CUSTOMER_FORM, null);
        HttpStatus status = response.isExisting() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Lists own tickets, newest first, paginated.
     */
    @GetMapping
    public ResponseEntity<PageResponse<TicketResponse>> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.min(Math.max(1, size), 50);

        Pageable pageable = PageRequest.of(effectivePage, effectiveSize);
        Page<TicketResponse> ticketPage = ticketService.getUserTickets(userId, pageable);

        PageResponse<TicketResponse> response = PageResponse.<TicketResponse>builder()
                .content(ticketPage.getContent())
                .page(ticketPage.getNumber())
                .size(ticketPage.getSize())
                .totalElements(ticketPage.getTotalElements())
                .totalPages(ticketPage.getTotalPages())
                .last(ticketPage.isLast())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves own ticket with its messages.
     * Returns 404 for tickets owned by other users (identical to missing tickets).
     */
    @GetMapping("/{ticketNumber}")
    public ResponseEntity<TicketDetailResponse> getMyTicket(
            @PathVariable String ticketNumber,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        TicketDetailResponse response = ticketService.getUserTicket(userId, ticketNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * Customer follow-up reply to a ticket thread.
     * Reopens RESOLVED tickets to OPEN; rejects replies if CLOSED.
     */
    @PostMapping("/{ticketNumber}/messages")
    public ResponseEntity<TicketDetailResponse> addMessage(
            @PathVariable String ticketNumber,
            @Valid @RequestBody TicketMessageRequest request,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        TicketDetailResponse response = ticketService.addCustomerMessage(userId, ticketNumber, request.getMessageBody());
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        return userRepository.findByEmail(authentication.getName().toLowerCase().trim())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }
}
