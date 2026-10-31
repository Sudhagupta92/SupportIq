package com.supportiq.config;

import com.supportiq.model.Order;
import com.supportiq.model.OrderItem;
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
 * Demo data initializer for SupportIQ Module 5 & Module 6.
 * 
 * Configured by property 'app.demo-data.enabled' (default true for local development and testing).
 * MUST be set to false for any production deployment!
 * 
 * Idempotent: Never creates duplicate customers, orders, or tickets across repeated restarts.
 * Seeds 3 demo customers (demo1@example.com to demo3@example.com) with password Demo@12345,
 * 12 realistic orders (1001 to 1012, 4 per customer), and 3 demo tickets (OPEN, IN_PROGRESS, RESOLVED).
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(2)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {

    public static final String DEMO_PASSWORD_RAW = "Demo@12345";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    public DemoDataInitializer(
            UserRepository userRepository,
            OrderRepository orderRepository,
            TicketRepository ticketRepository,
            TicketMessageRepository ticketMessageRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public DemoDataInitializer(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PasswordEncoder passwordEncoder) {
        this(userRepository, orderRepository, null, null, passwordEncoder);
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting SupportIQ Module 6 DemoDataInitializer (idempotent seeding)...");

        // 1. Seed or retrieve the 3 demo customers
        User demo1 = getOrCreateCustomer("demo1@example.com", "Demo Customer 1");
        User demo2 = getOrCreateCustomer("demo2@example.com", "Demo Customer 2");
        User demo3 = getOrCreateCustomer("demo3@example.com", "Demo Customer 3");
        User admin = userRepository.findByEmail("admin@supportiq.com").orElse(null);

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

        // 5. Seed Module 6 Demo Tickets
        if (ticketRepository != null && ticketMessageRepository != null) {
            // Ticket 1: OPEN for demo1 about cancelled order 1004 (REFUND)
            createTicketIfMissing(
                    "TKT-1001",
                    demo1,
                    "Refund inquiry for cancelled order #1004",
                    TicketCategory.REFUND,
                    TicketPriority.HIGH,
                    TicketStatus.OPEN,
                    "1004",
                    TicketSource.CUSTOMER_FORM,
                    "REFUND_REQUESTED",
                    now.minusDays(2),
                    now.minusDays(2),
                    null,
                    List.of(
                            new SeedMessage(SenderType.CUSTOMER, demo1, "Hello, my order 1004 was cancelled last week but I have not received the refund in my bank account yet. Could you please check the refund status?", now.minusDays(2))
                    )
            );

            // Ticket 2: IN_PROGRESS for demo2 with an admin reply
            createTicketIfMissing(
                    "TKT-1002",
                    demo2,
                    "Inquiry about package delivery timeline",
                    TicketCategory.DELIVERY,
                    TicketPriority.MEDIUM,
                    TicketStatus.IN_PROGRESS,
                    "1005",
                    TicketSource.CUSTOMER_FORM,
                    null,
                    now.minusDays(1),
                    now.minusHours(3),
                    null,
                    List.of(
                            new SeedMessage(SenderType.CUSTOMER, demo2, "Hi, I noticed the courier status has been out for delivery since yesterday morning. Can you confirm when it will arrive?", now.minusDays(1)),
                            new SeedMessage(SenderType.SYSTEM, null, "Status changed from OPEN to IN_PROGRESS", now.minusHours(5)),
                            new SeedMessage(SenderType.ADMIN, admin, "Hello! Our fulfillment team contacted Shadowfax logistics. The driver had an unexpected route delay and will deliver your package today before 6 PM.", now.minusHours(3))
                    )
            );

            // Ticket 3: RESOLVED for demo3
            createTicketIfMissing(
                    "TKT-1003",
                    demo3,
                    "Return request for size exchange",
                    TicketCategory.ORDER_ISSUE,
                    TicketPriority.LOW,
                    TicketStatus.RESOLVED,
                    "1010",
                    TicketSource.CUSTOMER_FORM,
                    null,
                    now.minusDays(5),
                    now.minusDays(1),
                    now.minusDays(1),
                    List.of(
                            new SeedMessage(SenderType.CUSTOMER, demo3, "The mechanical keyboard I received works great, but I would like to exchange it for the quiet red-switch version if possible.", now.minusDays(5)),
                            new SeedMessage(SenderType.SYSTEM, null, "Status changed from OPEN to RESOLVED", now.minusDays(1)),
                            new SeedMessage(SenderType.ADMIN, admin, "We have processed your exchange request. Return label #RL-99201 has been generated and sent to your email.", now.minusDays(1))
                    )
            );
        }

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

    private void createTicketIfMissing(
            String ticketNumber,
            User user,
            String subject,
            TicketCategory category,
            TicketPriority priority,
            TicketStatus status,
            String relatedOrderNumber,
            TicketSource source,
            String escalationReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime resolvedAt,
            List<SeedMessage> messages) {

        if (ticketRepository.existsByTicketNumber(ticketNumber)) {
            return;
        }

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .user(user)
                .subject(subject)
                .category(category)
                .priority(priority)
                .status(status)
                .relatedOrderNumber(relatedOrderNumber)
                .source(source)
                .escalationReason(escalationReason)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .resolvedAt(resolvedAt)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);

        for (SeedMessage sm : messages) {
            TicketMessage tm = TicketMessage.builder()
                    .ticket(savedTicket)
                    .senderType(sm.senderType())
                    .sender(sm.sender())
                    .body(sm.body())
                    .createdAt(sm.createdAt())
                    .build();
            ticketMessageRepository.save(tm);
        }

        log.info("Seeded demo ticket #{} for user {}", ticketNumber, user.getEmail());
    }

    private OrderItem createItem(String name, int qty, BigDecimal unitPrice) {
        return OrderItem.builder()
                .itemName(name)
                .quantity(qty)
                .unitPrice(unitPrice)
                .build();
    }

    private record SeedMessage(
            SenderType senderType,
            User sender,
            String body,
            LocalDateTime createdAt
    ) {}
}
