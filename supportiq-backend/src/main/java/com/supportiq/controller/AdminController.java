package com.supportiq.controller;

import com.supportiq.dto.AdminOrderItemDto;
import com.supportiq.dto.AdminOrderResponse;
import com.supportiq.dto.PageResponse;
import com.supportiq.model.Order;
import com.supportiq.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderRepository orderRepository;

    /**
     * Admin health/ping endpoint used strictly to verify role-based access control.
     * Accessible only by users possessing the ADMIN role.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "message", "Admin access confirmed. Pong!",
                "status", "UP",
                "role", "ADMIN"
        ));
    }

    /**
     * Read-only paginated order inspection endpoint for verifying demo data.
     * Restricted strictly to users with the ADMIN role.
     * Maximum page size is 50. Never exposes passwords, hashes, or tokens.
     */
    @GetMapping("/orders")
    public ResponseEntity<PageResponse<AdminOrderResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.min(Math.max(1, size), 50);

        Pageable pageable = PageRequest.of(effectivePage, effectiveSize);
        Page<Order> orderPage = orderRepository.findAllByOrderByPlacedAtDesc(pageable);

        List<AdminOrderResponse> content = orderPage.getContent().stream().map(order -> {
            List<AdminOrderItemDto> items = order.getItems().stream().map(item ->
                    AdminOrderItemDto.builder()
                            .id(item.getId())
                            .itemName(item.getItemName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .build()
            ).toList();

            return AdminOrderResponse.builder()
                    .id(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .customerId(order.getUser() != null ? order.getUser().getId() : null)
                    .customerEmail(order.getUser() != null ? order.getUser().getEmail() : null)
                    .customerName(order.getUser() != null ? order.getUser().getName() : null)
                    .status(order.getStatus())
                    .totalAmount(order.getTotalAmount())
                    .currency(order.getCurrency())
                    .placedAt(order.getPlacedAt())
                    .expectedDelivery(order.getExpectedDelivery())
                    .deliveredAt(order.getDeliveredAt())
                    .carrier(order.getCarrier())
                    .trackingNumber(order.getTrackingNumber())
                    .lastUpdateNote(order.getLastUpdateNote())
                    .items(items)
                    .build();
        }).toList();

        PageResponse<AdminOrderResponse> response = PageResponse.<AdminOrderResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .last(orderPage.isLast())
                .build();

        return ResponseEntity.ok(response);
    }
}
