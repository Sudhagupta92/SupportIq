package com.supportiq.controller;

import com.supportiq.dto.AdminTicketDetailResponse;
import com.supportiq.dto.AdminTicketResponse;
import com.supportiq.dto.PageResponse;
import com.supportiq.dto.TicketMessageRequest;
import com.supportiq.dto.UpdateTicketStatusRequest;
import com.supportiq.exception.ResourceNotFoundException;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketStatus;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.ticket.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin ticket management endpoints.
 * Restricted strictly to users with the ADMIN role.
 * Never exposes passwords, hashes, tokens, or unrelated customer data.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketController {

    private final TicketService ticketService;
    private final UserRepository userRepository;

    /**
     * Lists all support tickets across customers with filtering and pagination.
     * Maximum page size is strictly capped at 50.
     */
    @GetMapping
    public ResponseEntity<PageResponse<AdminTicketResponse>> getTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) String search) {

        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.min(Math.max(1, size), 50);

        Pageable pageable = PageRequest.of(effectivePage, effectiveSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminTicketResponse> ticketPage = ticketService.getAdminTickets(status, priority, category, search, pageable);

        PageResponse<AdminTicketResponse> response = PageResponse.<AdminTicketResponse>builder()
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
     * Retrieves full ticket conversation and basic customer information (name and email only).
     */
    @GetMapping("/{ticketNumber}")
    public ResponseEntity<AdminTicketDetailResponse> getTicket(@PathVariable String ticketNumber) {
        AdminTicketDetailResponse response = ticketService.getAdminTicket(ticketNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * Posts an admin reply to a ticket thread.
     */
    @PostMapping("/{ticketNumber}/messages")
    public ResponseEntity<AdminTicketDetailResponse> addMessage(
            @PathVariable String ticketNumber,
            @Valid @RequestBody TicketMessageRequest request,
            Authentication authentication) {

        Long adminUserId = resolveUserId(authentication);
        AdminTicketDetailResponse response = ticketService.addAdminMessage(adminUserId, ticketNumber, request.getMessageBody());
        return ResponseEntity.ok(response);
    }

    /**
     * Updates ticket status enforcing strict state transition rules.
     */
    @PatchMapping("/{ticketNumber}/status")
    public ResponseEntity<AdminTicketDetailResponse> updateStatus(
            @PathVariable String ticketNumber,
            @Valid @RequestBody UpdateTicketStatusRequest request,
            Authentication authentication) {

        Long adminUserId = resolveUserId(authentication);
        AdminTicketDetailResponse response = ticketService.updateTicketStatus(adminUserId, ticketNumber, request.getStatus());
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        return userRepository.findByEmail(authentication.getName().toLowerCase().trim())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + authentication.getName()));
    }
}
