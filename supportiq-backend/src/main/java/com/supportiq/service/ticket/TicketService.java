package com.supportiq.service.ticket;

import com.supportiq.dto.AdminTicketDetailResponse;
import com.supportiq.dto.AdminTicketResponse;
import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.TicketDetailResponse;
import com.supportiq.dto.TicketMessageResponse;
import com.supportiq.dto.TicketResponse;
import com.supportiq.exception.InvalidStatusTransitionException;
import com.supportiq.exception.ResourceNotFoundException;
import com.supportiq.exception.TicketRateLimitException;
import com.supportiq.model.Order;
import com.supportiq.model.SenderType;
import com.supportiq.model.Ticket;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketMessage;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.model.TicketStatus;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
import com.supportiq.repository.TicketMessageRepository;
import com.supportiq.repository.TicketRepository;
import com.supportiq.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing customer and admin ticket operations.
 * 
 * Strict Logging Rules:
 * Logs ONLY metadata (userId, ticketNumber, category, priority, source, status).
 * NEVER logs message text, descriptions, or ticket bodies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final TicketStatusValidator ticketStatusValidator;

    @Value("${app.tickets.daily-limit:5}")
    private int dailyTicketLimit = 5;

    /**
     * Creates a new ticket or attaches note to existing ticket (duplicate protection).
     *
     * @param userId authenticated user ID
     * @param request ticket creation payload
     * @param source ticket origin (CUSTOMER_FORM, CHAT_BUTTON, CHAT_TOOL)
     * @param escalationReason short reason code if escalated from chat
     * @return TicketResponse with existing flag
     */
    @Transactional
    public TicketResponse createTicket(Long userId, CreateTicketRequest request, TicketSource source, String escalationReason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Validate relatedOrderNumber: If order does not belong to user or does not exist,
        // silently store null without revealing whether the order exists.
        String validatedOrderNumber = null;
        if (request.getRelatedOrderNumber() != null && !request.getRelatedOrderNumber().isBlank()) {
            String candidateOrderNumber = request.getRelatedOrderNumber().trim();
            Optional<Order> orderOpt = orderRepository.findByOrderNumber(candidateOrderNumber);
            if (orderOpt.isPresent() && orderOpt.get().getUser().getId().equals(userId)) {
                validatedOrderNumber = candidateOrderNumber;
            }
        }

        // DUPLICATE PROTECTION:
        // If the user already has an OPEN or IN_PROGRESS ticket with the same relatedOrderNumber,
        // add the description as a new CUSTOMER message to that ticket and return it with existing=true.
        if (validatedOrderNumber != null) {
            List<Ticket> existingTickets = ticketRepository.findByUserIdAndRelatedOrderNumberAndStatusInOrderByCreatedAtDesc(
                    userId,
                    validatedOrderNumber,
                    List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
            );

            if (!existingTickets.isEmpty()) {
                Ticket existingTicket = existingTickets.get(0);

                TicketMessage noteMessage = TicketMessage.builder()
                        .ticket(existingTicket)
                        .senderType(SenderType.CUSTOMER)
                        .sender(user)
                        .body(request.getDescription().trim())
                        .createdAt(LocalDateTime.now())
                        .build();

                ticketMessageRepository.save(noteMessage);
                existingTicket.setUpdatedAt(LocalDateTime.now());
                ticketRepository.save(existingTicket);

                log.info("Duplicate ticket protection triggered: userId={}, ticketNumber={}, category={}, priority={}, source={}",
                        userId, existingTicket.getTicketNumber(), existingTicket.getCategory(), existingTicket.getPriority(), source);

                return mapToTicketResponse(existingTicket, true);
            }
        }

        // 24-Hour Limit Check: at most dailyTicketLimit (default 5) new tickets per 24 hours
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        long recentTicketCount = ticketRepository.countByUserIdAndCreatedAtAfter(userId, twentyFourHoursAgo);
        if (recentTicketCount >= dailyTicketLimit) {
            log.warn("Ticket rate limit exceeded: userId={}, countIn24h={}, limit={}", userId, recentTicketCount, dailyTicketLimit);
            throw new TicketRateLimitException(
                    "You have reached the maximum limit of " + dailyTicketLimit + " new tickets per 24 hours."
            );
        }

        // Create new Ticket
        String ticketNumber = ticketNumberGenerator.generateNextTicketNumber();
        TicketPriority priority = request.getPriority() != null ? request.getPriority() : TicketPriority.MEDIUM;
        TicketSource ticketSource = source != null ? source : TicketSource.CUSTOMER_FORM;

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .user(user)
                .subject(request.getSubject().trim())
                .category(request.getCategory())
                .priority(priority)
                .status(TicketStatus.OPEN)
                .relatedOrderNumber(validatedOrderNumber)
                .source(ticketSource)
                .escalationReason(escalationReason)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ticket = ticketRepository.save(ticket);

        // First message is the customer's description
        TicketMessage initialMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderType(SenderType.CUSTOMER)
                .sender(user)
                .body(request.getDescription().trim())
                .createdAt(LocalDateTime.now())
                .build();

        ticketMessageRepository.save(initialMessage);

        log.info("Ticket created: userId={}, ticketNumber={}, category={}, priority={}, source={}",
                userId, ticket.getTicketNumber(), ticket.getCategory(), ticket.getPriority(), ticket.getSource());

        return mapToTicketResponse(ticket, false);
    }

    /**
     * Retrieves paginated tickets belonging to the authenticated customer.
     */
    @Transactional(readOnly = true)
    public Page<TicketResponse> getUserTickets(Long userId, Pageable pageable) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(t -> mapToTicketResponse(t, false));
    }

    /**
     * Retrieves customer ticket detail with message thread.
     * Enforces ownership isolation: returns 404 for tickets owned by other customers.
     */
    @Transactional(readOnly = true)
    public TicketDetailResponse getUserTicket(Long userId, String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketNumber));

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return mapToTicketDetailResponse(ticket, messages);
    }

    /**
     * Adds a customer follow-up message to their ticket.
     * Reopens RESOLVED tickets to OPEN; rejects messages on CLOSED tickets.
     */
    @Transactional
    public TicketDetailResponse addCustomerMessage(Long userId, String ticketNumber, String messageText) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketNumber));

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidStatusTransitionException("Cannot reply to a closed ticket.");
        }

        User user = ticket.getUser();

        // If ticket was RESOLVED, customer reply reopens it to OPEN
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setResolvedAt(null);

            TicketMessage systemMessage = TicketMessage.builder()
                    .ticket(ticket)
                    .senderType(SenderType.SYSTEM)
                    .sender(null)
                    .body("Status changed from RESOLVED to OPEN")
                    .createdAt(LocalDateTime.now())
                    .build();

            ticketMessageRepository.save(systemMessage);
            log.info("Ticket reopened by customer reply: ticketNumber={}, newStatus=OPEN", ticketNumber);
        }

        TicketMessage customerMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderType(SenderType.CUSTOMER)
                .sender(user)
                .body(messageText.trim())
                .createdAt(LocalDateTime.now())
                .build();

        ticketMessageRepository.save(customerMessage);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        log.info("Customer message added: userId={}, ticketNumber={}, senderType=CUSTOMER", userId, ticketNumber);

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return mapToTicketDetailResponse(ticket, messages);
    }

    /**
     * Admin: Retrieves paginated tickets with optional filters and free-text search.
     */
    @Transactional(readOnly = true)
    public Page<AdminTicketResponse> getAdminTickets(
            TicketStatus status,
            TicketPriority priority,
            TicketCategory category,
            String search,
            Pageable pageable) {

        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate matchNumber = cb.like(cb.lower(root.get("ticketNumber")), pattern);
                Predicate matchSubject = cb.like(cb.lower(root.get("subject")), pattern);
                predicates.add(cb.or(matchNumber, matchSubject));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ticketRepository.findAll(spec, pageable).map(this::mapToAdminTicketResponse);
    }

    /**
     * Admin: Retrieves full ticket details including customer name/email and message history.
     */
    @Transactional(readOnly = true)
    public AdminTicketDetailResponse getAdminTicket(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketNumber));

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return mapToAdminTicketDetailResponse(ticket, messages);
    }

    /**
     * Admin: Adds an agent reply to a ticket thread.
     */
    @Transactional
    public AdminTicketDetailResponse addAdminMessage(Long adminUserId, String ticketNumber, String messageText) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketNumber));

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        TicketMessage adminMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderType(SenderType.ADMIN)
                .sender(adminUser)
                .body(messageText.trim())
                .createdAt(LocalDateTime.now())
                .build();

        ticketMessageRepository.save(adminMessage);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        log.info("Admin message added: adminUserId={}, ticketNumber={}, senderType=ADMIN", adminUserId, ticketNumber);

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return mapToAdminTicketDetailResponse(ticket, messages);
    }

    /**
     * Admin: Updates ticket status adhering strictly to transition rules.
     */
    @Transactional
    public AdminTicketDetailResponse updateTicketStatus(Long adminUserId, String ticketNumber, TicketStatus newStatus) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketNumber));

        TicketStatus oldStatus = ticket.getStatus();
        ticketStatusValidator.validateTransition(oldStatus, newStatus);

        ticket.setStatus(newStatus);
        ticket.setUpdatedAt(LocalDateTime.now());

        if (newStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if (newStatus == TicketStatus.OPEN) {
            ticket.setResolvedAt(null);
        }

        TicketMessage systemMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderType(SenderType.SYSTEM)
                .sender(null)
                .body("Status changed from " + oldStatus + " to " + newStatus)
                .createdAt(LocalDateTime.now())
                .build();

        ticketMessageRepository.save(systemMessage);
        ticketRepository.save(ticket);

        log.info("Ticket status updated: ticketNumber={}, oldStatus={}, newStatus={}, adminUserId={}",
                ticketNumber, oldStatus, newStatus, adminUserId);

        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        return mapToAdminTicketDetailResponse(ticket, messages);
    }

    private TicketResponse mapToTicketResponse(Ticket t, boolean existing) {
        return TicketResponse.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .subject(t.getSubject())
                .category(t.getCategory())
                .priority(t.getPriority())
                .status(t.getStatus())
                .relatedOrderNumber(t.getRelatedOrderNumber())
                .source(t.getSource())
                .escalationReason(t.getEscalationReason())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .resolvedAt(t.getResolvedAt())
                .existing(existing)
                .build();
    }

    private TicketDetailResponse mapToTicketDetailResponse(Ticket t, List<TicketMessage> messages) {
        List<TicketMessageResponse> messageDtos = messages.stream()
                .map(this::mapToTicketMessageResponse)
                .toList();

        return TicketDetailResponse.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .subject(t.getSubject())
                .category(t.getCategory())
                .priority(t.getPriority())
                .status(t.getStatus())
                .relatedOrderNumber(t.getRelatedOrderNumber())
                .source(t.getSource())
                .escalationReason(t.getEscalationReason())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .resolvedAt(t.getResolvedAt())
                .messages(messageDtos)
                .build();
    }

    private AdminTicketResponse mapToAdminTicketResponse(Ticket t) {
        return AdminTicketResponse.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .customerId(t.getUser() != null ? t.getUser().getId() : null)
                .customerName(t.getUser() != null ? t.getUser().getName() : null)
                .customerEmail(t.getUser() != null ? t.getUser().getEmail() : null)
                .subject(t.getSubject())
                .category(t.getCategory())
                .priority(t.getPriority())
                .status(t.getStatus())
                .relatedOrderNumber(t.getRelatedOrderNumber())
                .source(t.getSource())
                .escalationReason(t.getEscalationReason())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .resolvedAt(t.getResolvedAt())
                .build();
    }

    private AdminTicketDetailResponse mapToAdminTicketDetailResponse(Ticket t, List<TicketMessage> messages) {
        List<TicketMessageResponse> messageDtos = messages.stream()
                .map(this::mapToTicketMessageResponse)
                .toList();

        return AdminTicketDetailResponse.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .customerId(t.getUser() != null ? t.getUser().getId() : null)
                .customerName(t.getUser() != null ? t.getUser().getName() : null)
                .customerEmail(t.getUser() != null ? t.getUser().getEmail() : null)
                .subject(t.getSubject())
                .category(t.getCategory())
                .priority(t.getPriority())
                .status(t.getStatus())
                .relatedOrderNumber(t.getRelatedOrderNumber())
                .source(t.getSource())
                .escalationReason(t.getEscalationReason())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .resolvedAt(t.getResolvedAt())
                .messages(messageDtos)
                .build();
    }

    private TicketMessageResponse mapToTicketMessageResponse(TicketMessage m) {
        return TicketMessageResponse.builder()
                .id(m.getId())
                .senderType(m.getSenderType())
                .senderId(m.getSender() != null ? m.getSender().getId() : null)
                .senderName(m.getSender() != null ? m.getSender().getName() : (m.getSenderType() == SenderType.SYSTEM ? "System" : null))
                .senderEmail(m.getSender() != null ? m.getSender().getEmail() : null)
                .body(m.getBody())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
