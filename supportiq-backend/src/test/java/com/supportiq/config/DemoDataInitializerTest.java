package com.supportiq.config;

import com.supportiq.model.Order;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.Ticket;
import com.supportiq.model.TicketStatus;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
import com.supportiq.repository.TicketMessageRepository;
import com.supportiq.repository.TicketRepository;
import com.supportiq.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(BCryptPasswordEncoder.class)
class DemoDataInitializerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("DemoDataInitializer must be idempotent: running multiple times never creates duplicate orders or tickets")
    void testDemoDataInitializer_IsIdempotent() {
        DemoDataInitializer initializer = new DemoDataInitializer(
                userRepository,
                orderRepository,
                ticketRepository,
                ticketMessageRepository,
                passwordEncoder
        );

        // Run 1st time
        initializer.run();

        List<User> demoCustomersFirstRun = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .toList();
        List<Order> ordersFirstRun = orderRepository.findAll();
        List<Ticket> ticketsFirstRun = ticketRepository.findAll();

        assertThat(demoCustomersFirstRun).hasSize(3);
        assertThat(ordersFirstRun).hasSize(12);
        assertThat(ticketsFirstRun).hasSize(3);

        // Verify all 3 demo accounts have BCrypt-hashed password matching Demo@12345
        for (User customer : demoCustomersFirstRun) {
            assertThat(passwordEncoder.matches("Demo@12345", customer.getPassword())).isTrue();
        }

        // Verify all 6 OrderStatus enum values are covered across the 12 orders
        Set<OrderStatus> observedStatuses = ordersFirstRun.stream()
                .map(Order::getStatus)
                .collect(Collectors.toSet());
        assertThat(observedStatuses).containsAll(EnumSet.allOf(OrderStatus.class));

        // Verify the 3 demo tickets
        Ticket t1 = ticketRepository.findByTicketNumber("TKT-1001").orElseThrow();
        assertThat(t1.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(t1.getRelatedOrderNumber()).isEqualTo("1004");

        Ticket t2 = ticketRepository.findByTicketNumber("TKT-1002").orElseThrow();
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        Ticket t3 = ticketRepository.findByTicketNumber("TKT-1003").orElseThrow();
        assertThat(t3.getStatus()).isEqualTo(TicketStatus.RESOLVED);

        // Run 2nd time (should be completely idempotent and not add anything)
        initializer.run();

        List<User> demoCustomersSecondRun = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .toList();
        List<Order> ordersSecondRun = orderRepository.findAll();
        List<Ticket> ticketsSecondRun = ticketRepository.findAll();

        assertThat(demoCustomersSecondRun).hasSize(3);
        assertThat(ordersSecondRun).hasSize(12);
        assertThat(ticketsSecondRun).hasSize(3);

        // Run 3rd time
        initializer.run();
        assertThat(orderRepository.count()).isEqualTo(12);
        assertThat(ticketRepository.count()).isEqualTo(3);
    }
}
