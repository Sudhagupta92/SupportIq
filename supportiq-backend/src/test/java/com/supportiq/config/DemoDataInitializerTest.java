package com.supportiq.config;

import com.supportiq.model.Order;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
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
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("DemoDataInitializer must be idempotent: running multiple times never creates duplicates")
    void testDemoDataInitializer_IsIdempotent() {
        DemoDataInitializer initializer = new DemoDataInitializer(
                userRepository,
                orderRepository,
                passwordEncoder
        );

        // Run 1st time
        initializer.run();

        List<User> demoCustomersFirstRun = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .toList();
        List<Order> ordersFirstRun = orderRepository.findAll();

        assertThat(demoCustomersFirstRun).hasSize(3);
        assertThat(ordersFirstRun).hasSize(12);

        // Verify all 3 demo accounts have BCrypt-hashed password matching Demo@12345
        for (User customer : demoCustomersFirstRun) {
            assertThat(passwordEncoder.matches("Demo@12345", customer.getPassword())).isTrue();
        }

        // Verify all 6 OrderStatus enum values are covered across the 12 orders
        Set<OrderStatus> observedStatuses = ordersFirstRun.stream()
                .map(Order::getStatus)
                .collect(Collectors.toSet());
        assertThat(observedStatuses).containsAll(EnumSet.allOf(OrderStatus.class));

        // Run 2nd time (should be completely idempotent and not add anything)
        initializer.run();

        List<User> demoCustomersSecondRun = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .toList();
        List<Order> ordersSecondRun = orderRepository.findAll();

        assertThat(demoCustomersSecondRun).hasSize(3);
        assertThat(ordersSecondRun).hasSize(12);

        // Run 3rd time
        initializer.run();
        assertThat(orderRepository.count()).isEqualTo(12);
    }
}
