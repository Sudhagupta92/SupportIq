package com.supportiq.config;

import com.supportiq.model.Order;
import com.supportiq.model.OrderItem;
import com.supportiq.model.OrderStatus;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.OrderRepository;
import com.supportiq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo data initializer for SupportIQ Module 5.
 * 
 * Configured by property 'app.demo-data.enabled' (default true for local development and testing).
 * MUST be set to false for any production deployment!
 * 
 * Idempotent: Never creates duplicate customers or orders across repeated restarts.
 * Seeds 3 demo customers (demo1@example.com to demo3@example.com) with password Demo@12345,
 * and 12 realistic orders (1001 to 1012, 4 per customer) covering all OrderStatus enum values.
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(2)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    public static final String DEMO_PASSWORD_RAW = "Demo@12345";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting SupportIQ Module 5 DemoDataInitializer (idempotent seeding)...");

        // 1. Seed or retrieve the 3 demo customers
        User demo1 = getOrCreateCustomer("demo1@example.com", "Demo Customer 1");
        User demo2 = getOrCreateCustomer("demo2@example.com", "Demo Customer 2");
        User demo3 = getOrCreateCustomer("demo3@example.com", "Demo Customer 3");

        LocalDateTime now = LocalDateTime.now();

        // 2. Seed Customer 1 orders (1001 to 1004)
        createOrderIfMissing("1001", demo1, OrderStatus.DELIVERED,
                new BigDecimal("2999.00"), "INR",
                now.minusDays(10), now.minusDays(7), now.minusDays(6),
                "BlueDart", "BD90283411", "Delivered to recipient at front door",
                List.of(
                        createItem("Blue Cotton Shirt", 2, new BigDecimal("799.50")),
                        createItem("Running Shoes", 1, new BigDecimal("1400.00"))
                ));

        createOrderIfMissing("1002", demo1, OrderStatus.SHIPPED,
                new BigDecimal("1999.00"), "INR",
                now.minusDays(3), now.plusDays(2), null,
                "Delhivery", "DL44820193", "In transit between sorting hubs",
                List.of(
                        createItem("Wireless Earphones", 1, new BigDecimal("1999.00"))
                ));

        createOrderIfMissing("1003", demo1, OrderStatus.PLACED,
                new BigDecimal("1299.00"), "INR",
                now.minusDays(1), now.plusDays(4), null,
                null, null, "Order received and payment confirmed",
                List.of(
                        createItem("Waterproof Backpack", 1, new BigDecimal("1299.00"))
                ));

        createOrderIfMissing("1004", demo1, OrderStatus.CANCELLED,
                new BigDecimal("850.00"), "INR",
                now.minusDays(7), null, null,
                null, null, "Cancelled by customer before dispatch",
                List.of(
                        createItem("Stainless Steel Water Bottle", 1, new BigDecimal("850.00"))
                ));

        // 3. Seed Customer 2 orders (1005 to 1008)
        createOrderIfMissing("1005", demo2, OrderStatus.OUT_FOR_DELIVERY,
                new BigDecimal("3499.00"), "INR",
                now.minusDays(2), now, null,
                "Shadowfax", "SF88291034", "Courier agent out for delivery",
                List.of(
                        createItem("Smart Fitness Watch", 1, new BigDecimal("3499.00"))
                ));

        createOrderIfMissing("1006", demo2, OrderStatus.PACKED,
                new BigDecimal("2200.00"), "INR",
                now.minusDays(1), now.plusDays(3), null,
                null, null, "Package packed and awaiting courier pickup",
                List.of(
                        createItem("Denim Jacket", 1, new BigDecimal("1800.00")),
                        createItem("Cotton Socks Pack", 1, new BigDecimal("400.00"))
                ));

        createOrderIfMissing("1007", demo2, OrderStatus.DELIVERED,
                new BigDecimal("1599.00"), "INR",
                now.minusDays(14), now.minusDays(11), now.minusDays(10),
                "BlueDart", "BD33829104", "Package handed over to customer",
                List.of(
                        createItem("Noise-Cancelling Earphones", 1, new BigDecimal("1599.00"))
                ));

        createOrderIfMissing("1008", demo2, OrderStatus.PLACED,
                new BigDecimal("999.00"), "INR",
                now, now.plusDays(5), null,
                null, null, "Order placed, awaiting warehouse processing",
                List.of(
                        createItem("Casual Running Shoes", 1, new BigDecimal("999.00"))
                ));

        // 4. Seed Customer 3 orders (1009 to 1012)
        createOrderIfMissing("1009", demo3, OrderStatus.SHIPPED,
                new BigDecimal("2499.00"), "INR",
                now.minusDays(2), now.plusDays(2), null,
                "FedEx", "FX66291045", "Package scanned at origin terminal",
                List.of(
                        createItem("Ergonomic Laptop Stand", 1, new BigDecimal("2499.00"))
                ));

        createOrderIfMissing("1010", demo3, OrderStatus.DELIVERED,
                new BigDecimal("4500.00"), "INR",
                now.minusDays(25), now.minusDays(21), now.minusDays(20),
                "Delhivery", "DL77291024", "Delivered at reception desk",
                List.of(
                        createItem("Mechanical Keyboard", 1, new BigDecimal("4500.00"))
                ));

        createOrderIfMissing("1011", demo3, OrderStatus.OUT_FOR_DELIVERY,
                new BigDecimal("1750.00"), "INR",
                now.minusDays(3), now, null,
                "BlueDart", "BD55920193", "Courier out for delivery, expected before 7 PM",
                List.of(
                        createItem("Canvas Travel Backpack", 1, new BigDecimal("1750.00"))
                ));

        createOrderIfMissing("1012", demo3, OrderStatus.CANCELLED,
                new BigDecimal("1200.00"), "INR",
                now.minusDays(6), null, null,
                null, null, "Payment authorization failed or cancelled by buyer",
                List.of(
                        createItem("Classic Blue Shirt", 2, new BigDecimal("600.00"))
                ));

        log.info("Demo data initialization completed successfully.");
    }

    private User getOrCreateCustomer(String email, String name) {
        String cleanEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(cleanEmail).orElseGet(() -> {
            User newUser = User.builder()
                    .email(cleanEmail)
                    .name(name)
                    .password(passwordEncoder.encode(DEMO_PASSWORD_RAW))
                    .role(Role.CUSTOMER)
                    .build();
            User saved = userRepository.save(newUser);
            log.info("Created demo customer: {}", cleanEmail);
            return saved;
        });
    }

    private void createOrderIfMissing(
            String orderNumber,
            User user,
            OrderStatus status,
            BigDecimal totalAmount,
            String currency,
            LocalDateTime placedAt,
            LocalDateTime expectedDelivery,
            LocalDateTime deliveredAt,
            String carrier,
            String trackingNumber,
            String lastUpdateNote,
            List<OrderItem> items) {

        if (orderRepository.existsByOrderNumber(orderNumber)) {
            return;
        }

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(status)
                .totalAmount(totalAmount)
                .currency(currency)
                .placedAt(placedAt)
                .expectedDelivery(expectedDelivery)
                .deliveredAt(deliveredAt)
                .carrier(carrier)
                .trackingNumber(trackingNumber)
                .lastUpdateNote(lastUpdateNote)
                .build();

        for (OrderItem item : items) {
            order.addItem(item);
        }

        orderRepository.save(order);
        log.info("Seeded demo order #{} for user {}", orderNumber, user.getEmail());
    }

    private OrderItem createItem(String name, int qty, BigDecimal unitPrice) {
        return OrderItem.builder()
                .itemName(name)
                .quantity(qty)
                .unitPrice(unitPrice)
                .build();
    }
}
