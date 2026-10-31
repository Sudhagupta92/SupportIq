package com.supportiq.dto;

import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketResponse {

    private Long id;
    private String ticketNumber;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String subject;
    private TicketCategory category;
    private TicketPriority priority;
    private TicketStatus status;
    private String relatedOrderNumber;
    private TicketSource source;
    private String escalationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}
