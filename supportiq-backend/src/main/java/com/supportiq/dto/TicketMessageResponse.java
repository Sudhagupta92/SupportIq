package com.supportiq.dto;

import com.supportiq.model.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageResponse {

    private Long id;
    private SenderType senderType;
    private Long senderId;
    private String senderName;
    private String senderEmail;
    private String body;
    private LocalDateTime createdAt;
}
