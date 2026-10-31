package com.supportiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.dto.TicketMessageRequest;
import com.supportiq.dto.UpdateTicketStatusRequest;
import com.supportiq.model.Role;
import com.supportiq.model.Ticket;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.model.TicketStatus;
import com.supportiq.model.User;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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

    private User customer;
    private User admin;
    private String customerToken;
    private String adminToken;
    private Ticket sampleTicket;

    @BeforeEach
    void setUp() {
        ticketMessageRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        customer = User.builder().name("Demo Customer").email("demo@example.com").password("Password@123").role(Role.CUSTOMER).build();
        customer = userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        admin = User.builder().name("Support Agent").email("admin@supportiq.com").password("Password@123").role(Role.ADMIN).build();
        admin = userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        sampleTicket = Ticket.builder()
                .ticketNumber("TKT-1001")
                .user(customer)
                .subject("Damaged item received")
                .category(TicketCategory.DAMAGED_ITEM)
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.OPEN)
                .source(TicketSource.CUSTOMER_FORM)
                .build();
        sampleTicket = ticketRepository.save(sampleTicket);
    }

    @Test
    @DisplayName("Security: CUSTOMER role receives 403 Forbidden on /api/admin/tickets")
    void testGetTickets_ForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/admin/tickets")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Admin: Lists tickets with filters and pagination; never exposes password or token")
    void testGetTickets_AdminSuccess() throws Exception {
        mockMvc.perform(get("/api/admin/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "OPEN")
                        .param("category", "DAMAGED_ITEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ticketNumber").value("TKT-1001"))
                .andExpect(jsonPath("$.content[0].customerEmail").value("demo@example.com"))
                .andExpect(jsonPath("$.content[0].customerName").value("Demo Customer"))
                // Security check: password is NOT exposed
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Admin: Adds message reply to ticket")
    void testAdminReply() throws Exception {
        TicketMessageRequest reply = TicketMessageRequest.builder()
                .message("Hello! We are looking into your damaged item issue.")
                .build();

        mockMvc.perform(post("/api/admin/tickets/TKT-1001/messages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].senderName").value("Support Agent"))
                .andExpect(jsonPath("$.messages[0].senderType").value("ADMIN"));
    }

    @Test
    @DisplayName("Admin: Updates status successfully when transition is valid")
    void testAdminUpdateStatus_ValidTransition() throws Exception {
        UpdateTicketStatusRequest req = UpdateTicketStatusRequest.builder()
                .status(TicketStatus.IN_PROGRESS)
                .build();

        mockMvc.perform(patch("/api/admin/tickets/TKT-1001/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.messages[0].body").value("Status changed from OPEN to IN_PROGRESS"))
                .andExpect(jsonPath("$.messages[0].senderType").value("SYSTEM"));
    }

    @Test
    @DisplayName("Admin: Status transition rejection on invalid transition returns 400 Bad Request")
    void testAdminUpdateStatus_InvalidTransitionReturns400() throws Exception {
        // OPEN -> OPEN is invalid (already open)
        UpdateTicketStatusRequest req = UpdateTicketStatusRequest.builder()
                .status(TicketStatus.OPEN)
                .build();

        mockMvc.perform(patch("/api/admin/tickets/TKT-1001/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Ticket is already in status OPEN. Cannot transition to the same status."));
    }

    @Test
    @DisplayName("CORS Preflight: PATCH on /api/admin/tickets/TKT-1001/status from allowed origin is accepted")
    void testCorsPreflight_PatchAllowedOrigin_Accepted() throws Exception {
        mockMvc.perform(options("/api/admin/tickets/TKT-1001/status")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    @DisplayName("CORS Preflight: PATCH on /api/admin/tickets/TKT-1001/status from different origin is rejected")
    void testCorsPreflight_PatchDifferentOrigin_Rejected() throws Exception {
        mockMvc.perform(options("/api/admin/tickets/TKT-1001/status")
                        .header("Origin", "http://malicious-site.com")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isForbidden());
    }
}
