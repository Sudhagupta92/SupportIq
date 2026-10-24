package com.supportiq.dto;

import com.supportiq.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order response model for administrative verification.
 * Strictly never exposes passwords, tokens, or credentials.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderResponse {

    private Long id;
    private String orderNumber;
    private Long customerId;
    private String customerEmail;
    private String customerName;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime placedAt;
    private LocalDateTime expectedDelivery;
    private LocalDateTime deliveredAt;
    private String carrier;
    private String trackingNumber;
    private String lastUpdateNote;
    private List<AdminOrderItemDto> items;
}
