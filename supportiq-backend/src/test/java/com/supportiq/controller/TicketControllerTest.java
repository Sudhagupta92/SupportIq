package com.supportiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.TicketMessageRequest;
import com.supportiq.model.Order;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.Ticket;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.model.TicketStatus;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
import com.supportiq.repository.TicketMessageRepository;
import com.supportiq.repository.TicketRepository;
import com.supportiq.repository.UserRepository;
import com.supportiq.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private VectorStore vectorStore;

    private User customer1;
    private User customer2;
    private String token1;
    private String token2;
    private Order order1;

    @BeforeEach
    void setUp() {
        ticketMessageRepository.deleteAll();
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        customer1 = User.builder().name("Demo 1").email("demo1@example.com").password("Password@123").role(Role.CUSTOMER).build();
        customer1 = userRepository.save(customer1);
        token1 = jwtService.generateToken(customer1);

        customer2 = User.builder().name("Demo 2").email("demo2@example.com").password("Password@123").role(Role.CUSTOMER).build();
        customer2 = userRepository.save(customer2);
        token2 = jwtService.generateToken(customer2);

        order1 = Order.builder()
                .orderNumber("1004")
                .user(customer1)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("850.00"))
                .currency("INR")
                .placedAt(LocalDateTime.now())
                .build();
        order1 = orderRepository.save(order1);
    }

    @Test
    @DisplayName("POST /api/tickets creates new ticket and returns 201 Created")
    void testCreateTicket_Success() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .subject("Refund question")
                .description("Where is my refund for cancelled order?")
                .category(TicketCategory.REFUND)
                .priority(TicketPriority.HIGH)
                .relatedOrderNumber("1004")
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("TKT-1001"))
                .andExpect(jsonPath("$.existing").value(false))
                .andExpect(jsonPath("$.category").value("REFUND"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.relatedOrderNumber").value("1004"));
    }

    @Test
    @DisplayName("POST /api/tickets duplicate protection returns existing ticket with existing: true and status 200 OK")
    void testCreateTicket_DuplicateProtection() throws Exception {
        // First ticket
        CreateTicketRequest request1 = CreateTicketRequest.builder()
                .subject("Initial refund question")
                .description("Where is my refund?")
                .category(TicketCategory.REFUND)
                .relatedOrderNumber("1004")
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.existing").value(false));

        // Duplicate ticket for same order
        CreateTicketRequest request2 = CreateTicketRequest.builder()
                .subject("Second refund question")
                .description("Still waiting for my refund!")
                .category(TicketCategory.REFUND)
                .relatedOrderNumber("1004")
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber").value("TKT-1001"))
                .andExpect(jsonPath("$.existing").value(true));
    }

    @Test
    @DisplayName("Ticket ownership isolation: Customer 2 receives 404 for Customer 1's ticket")
    void testGetTicket_OwnershipIsolationReturns404() throws Exception {
        // Create ticket for customer 1
        CreateTicketRequest request = CreateTicketRequest.builder()
                .subject("Private Customer 1 issue")
                .description("Confidential details")
                .category(TicketCategory.ACCOUNT)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Customer 2 tries to access Customer 1's ticket -> 404 Not Found (same as missing ticket)
        mockMvc.perform(get("/api/tickets/TKT-1001")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Customer 1 can successfully view it
        mockMvc.perform(get("/api/tickets/TKT-1001")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber").value("TKT-1001"))
                .andExpect(jsonPath("$.messages", hasSize(1)));
    }

    @Test
    @DisplayName("Customer reply reopens RESOLVED ticket to OPEN and adds system status message")
    void testCustomerReply_ReopensResolvedTicket() throws Exception {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-1001")
                .user(customer1)
                .subject("Resolved issue")
                .category(TicketCategory.ORDER_ISSUE)
                .priority(TicketPriority.LOW)
                .status(TicketStatus.RESOLVED)
                .source(TicketSource.CUSTOMER_FORM)
                .resolvedAt(LocalDateTime.now())
                .build();
        ticketRepository.save(ticket);

        TicketMessageRequest reply = TicketMessageRequest.builder()
                .message("Wait, the problem is not resolved yet")
                .build();

        mockMvc.perform(post("/api/tickets/TKT-1001/messages")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.messages", hasSize(2))) // System status message + customer reply
                .andExpect(jsonPath("$.messages[0].body").value("Status changed from RESOLVED to OPEN"));
    }

    @Test
    @DisplayName("Customer reply on CLOSED ticket is rejected with 400 Bad Request")
    void testCustomerReply_ClosedTicketRejected() throws Exception {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-1001")
                .user(customer1)
                .subject("Closed issue")
                .category(TicketCategory.ORDER_ISSUE)
                .priority(TicketPriority.LOW)
                .status(TicketStatus.CLOSED)
                .source(TicketSource.CUSTOMER_FORM)
                .build();
        ticketRepository.save(ticket);

        TicketMessageRequest reply = TicketMessageRequest.builder()
                .message("Can I still reply?")
                .build();

        mockMvc.perform(post("/api/tickets/TKT-1001/messages")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reply)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Cannot reply to a closed ticket."));
    }
}
