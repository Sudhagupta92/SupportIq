package com.supportiq.controller;

import com.supportiq.model.Order;
import com.supportiq.model.OrderItem;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private VectorStore vectorStore;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User customer = User.builder()
                .name("Test Customer")
                .email("customer@example.com")
                .password("Password@123")
                .role(Role.CUSTOMER)
                .build();
        customer = userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        User admin = User.builder()
                .name("Test Admin")
                .email("admin@example.com")
                .password("Password@123")
                .role(Role.ADMIN)
                .build();
        admin = userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);
    }

    @Test
    @DisplayName("GET /api/admin/orders must return 403 Forbidden for CUSTOMER role")
    void testGetOrders_ForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /api/admin/orders must return 401 Unauthorized for unauthenticated requests")
    void testGetOrders_UnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/admin/orders must return 200 OK with paginated orders for ADMIN role, without exposing passwords")
    void testGetOrders_SuccessForAdmin() throws Exception {
        User user = userRepository.findByEmail("customer@example.com").orElseThrow();

        Order order = Order.builder()
                .id(1L)
                .orderNumber("1001")
                .user(user)
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("2999.00"))
                .currency("INR")
                .placedAt(LocalDateTime.now().minusDays(5))
                .carrier("BlueDart")
                .trackingNumber("BD123")
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .order(order)
                .itemName("Running Shoes")
                .quantity(1)
                .unitPrice(new BigDecimal("2999.00"))
                .build();
        order.addItem(item);

        when(orderRepository.findAllByOrderByPlacedAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/admin/orders?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].orderNumber").value("1001"))
                .andExpect(jsonPath("$.content[0].customerEmail").value("customer@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.content[0].items[0].itemName").value("Running Shoes"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].token").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }
}
