package com.supportiq.service.ticket;

import com.supportiq.dto.AdminTicketDetailResponse;
import com.supportiq.dto.AdminTicketResponse;
import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.TicketDetailResponse;
import com.supportiq.dto.TicketResponse;
import com.supportiq.exception.InvalidStatusTransitionException;
import com.supportiq.exception.ResourceNotFoundException;
import com.supportiq.exception.TicketRateLimitException;
import com.supportiq.model.Order;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMessageRepository ticketMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketNumberGenerator ticketNumberGenerator;

    private TicketStatusValidator ticketStatusValidator;
    private TicketService ticketService;

    private User customer1;
    private User customer2;
    private User adminUser;

    @BeforeEach
    void setUp() {
        ticketStatusValidator = new TicketStatusValidator();
        ticketService = new TicketService(
                ticketRepository,
                ticketMessageRepository,
                userRepository,
                orderRepository,
                ticketNumberGenerator,
                ticketStatusValidator
        );
        ReflectionTestUtils.setField(ticketService, "dailyTicketLimit", 5);

        customer1 = User.builder().id(1L).name("Customer One").email("demo1@example.com").role(Role.CUSTOMER).build();
        customer2 = User.builder().id(2L).name("Customer Two").email("demo2@example.com").role(Role.CUSTOMER).build();
        adminUser = User.builder().id(99L).name("Admin User").email("admin@supportiq.com").role(Role.ADMIN).build();
    }

    @Test
    @DisplayName("Ticket ownership isolation: Customer 2 receives 404 for Customer 1's ticket (identical to missing ticket)")
    void testOwnershipIsolation_CustomerBGets404ForCustomerATicket() {
        Ticket ticket = Ticket.builder()
                .id(10L)
                .ticketNumber("TKT-1001")
                .user(customer1)
                .subject("My order issue")
                .status(TicketStatus.OPEN)
                .build();

        when(ticketRepository.findByTicketNumber("TKT-1001")).thenReturn(Optional.of(ticket));

        // Customer 2 requests Customer 1's ticket -> throws ResourceNotFoundException (404)
        assertThatThrownBy(() -> ticketService.getUserTicket(customer2.getId(), "TKT-1001"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ticket not found: TKT-1001");

        // Completely missing ticket also returns identical ResourceNotFoundException
        when(ticketRepository.findByTicketNumber("TKT-9999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ticketService.getUserTicket(customer2.getId(), "TKT-9999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ticket not found: TKT-9999");
    }

    @Test
    @DisplayName("relatedOrderNumber belonging to another customer is silently dropped without revealing existence")
    void testForeignRelatedOrderNumber_IsSilentlyDropped() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer1));
        when(ticketNumberGenerator.generateNextTicketNumber()).thenReturn("TKT-1004");

        // Order 1005 belongs to customer 2
        Order foreignOrder = Order.builder()
                .id(5L)
                .orderNumber("1005")
                .user(customer2)
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("100.00"))
                .currency("INR")
                .build();

        when(orderRepository.findByOrderNumber("1005")).thenReturn(Optional.of(foreignOrder));
        when(ticketRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTicketRequest request = CreateTicketRequest.builder()
                .subject("Need help")
                .description("Please check this order")
                .category(TicketCategory.ORDER_ISSUE)
                .relatedOrderNumber("1005") // Foreign order
                .build();

        TicketResponse response = ticketService.createTicket(1L, request, TicketSource.CUSTOMER_FORM, null);

        // Assert relatedOrderNumber is stored as NULL silently
        assertThat(response.getRelatedOrderNumber()).isNull();
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getRelatedOrderNumber()).isNull();
    }

    @Test
    @DisplayName("Duplicate protection: If user has OPEN/IN_PROGRESS ticket for order, adds note to existing ticket and returns existing: true")
    void testDuplicateProtection_ReturnsExistingTicketWithNote() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer1));

        Order ownOrder = Order.builder()
                .id(1L)
                .orderNumber("1004")
                .user(customer1)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("850.00"))
                .currency("INR")
                .build();

        when(orderRepository.findByOrderNumber("1004")).thenReturn(Optional.of(ownOrder));

        Ticket existingTicket = Ticket.builder()
                .id(1L)
                .ticketNumber("TKT-1001")
                .user(customer1)
                .subject("Original refund ticket")
                .category(TicketCategory.REFUND)
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.OPEN)
                .relatedOrderNumber("1004")
                .createdAt(LocalDateTime.now().minusHours(2))
                .updatedAt(LocalDateTime.now().minusHours(2))
                .build();

        when(ticketRepository.findByUserIdAndRelatedOrderNumberAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq("1004"), any()
        )).thenReturn(List.of(existingTicket));

        CreateTicketRequest request = CreateTicketRequest.builder()
                .subject("New question about refund")
                .description("Any update on my refund?")
                .category(TicketCategory.REFUND)
                .relatedOrderNumber("1004")
                .build();

        TicketResponse response = ticketService.createTicket(1L, request, TicketSource.CUSTOMER_FORM, null);

        assertThat(response.isExisting()).isTrue();
        assertThat(response.getTicketNumber()).isEqualTo("TKT-1001");

        // Verify description was saved as a customer message
        ArgumentCaptor<TicketMessage> msgCaptor = ArgumentCaptor.forClass(TicketMessage.class);
        verify(ticketMessageRepository).save(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getBody()).isEqualTo("Any update on my refund?");
        assertThat(msgCaptor.getValue().getSenderType()).isEqualTo(SenderType.CUSTOMER);
    }

    @Test
    @DisplayName("5-per-24h rate limit: Throws TicketRateLimitException when user reaches 5 tickets in 24 hours")
    void testDailyLimit_ThrowsTicketRateLimitException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer1));
        when(ticketRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(5L);

        CreateTicketRequest request = CreateTicketRequest.builder()
                .subject("Sixth ticket")
                .description("Please help me")
                .category(TicketCategory.OTHER)
                .build();

        assertThatThrownBy(() -> ticketService.createTicket(1L, request, TicketSource.CUSTOMER_FORM, null))
                .isInstanceOf(TicketRateLimitException.class)
                .hasMessageContaining("limit of 5 new tickets per 24 hours");
    }

    @Test
    @DisplayName("Status transition rules: Valid transitions succeed and add SYSTEM message; invalid ones reject with 400")
    void testStatusTransitions_StrictRulesAndSystemMessage() {
        Ticket ticket = Ticket.builder()
                .id(1L)
                .ticketNumber("TKT-1001")
                .user(customer1)
                .status(TicketStatus.OPEN)
                .subject("Issue")
                .build();

        when(ticketRepository.findByTicketNumber("TKT-1001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. OPEN -> IN_PROGRESS: ALLOWED
        ticketService.updateTicketStatus(adminUser.getId(), "TKT-1001", TicketStatus.IN_PROGRESS);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        ArgumentCaptor<TicketMessage> msgCaptor = ArgumentCaptor.forClass(TicketMessage.class);
        verify(ticketMessageRepository).save(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getBody()).isEqualTo("Status changed from OPEN to IN_PROGRESS");
        assertThat(msgCaptor.getValue().getSenderType()).isEqualTo(SenderType.SYSTEM);

        // 2. IN_PROGRESS -> OPEN: REJECTED (400)
        assertThatThrownBy(() -> ticketService.updateTicketStatus(adminUser.getId(), "TKT-1001", TicketStatus.OPEN))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid status transition from IN_PROGRESS to OPEN");

        // 3. IN_PROGRESS -> RESOLVED: ALLOWED
        ticketService.updateTicketStatus(adminUser.getId(), "TKT-1001", TicketStatus.RESOLVED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();

        // 4. Customer reply on RESOLVED reopens ticket to OPEN
        when(ticketRepository.findByTicketNumber("TKT-1001")).thenReturn(Optional.of(ticket));
        ticketService.addCustomerMessage(customer1.getId(), "TKT-1001", "Actually I still have a question");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();

        // 5. ANY -> CLOSED by ADMIN: ALLOWED
        ticketService.updateTicketStatus(adminUser.getId(), "TKT-1001", TicketStatus.CLOSED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);

        // 6. CLOSED -> OPEN: REJECTED (400)
        assertThatThrownBy(() -> ticketService.updateTicketStatus(adminUser.getId(), "TKT-1001", TicketStatus.OPEN))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid status transition from CLOSED to OPEN");

        // 7. Customer cannot reply to CLOSED ticket
        assertThatThrownBy(() -> ticketService.addCustomerMessage(customer1.getId(), "TKT-1001", "Can I reply?"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot reply to a closed ticket");
    }

    @Test
    @DisplayName("Admin ticket listing pagination limit is respected")
    void testAdminTicketsPagination() {
        Pageable pageable = PageRequest.of(0, 50);
        Ticket t = Ticket.builder().id(1L).ticketNumber("TKT-1001").user(customer1).subject("Sub").build();
        when(ticketRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(t), pageable, 1));

        Page<AdminTicketResponse> result = ticketService.getAdminTickets(null, null, null, null, pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerEmail()).isEqualTo("demo1@example.com");
    }
}
