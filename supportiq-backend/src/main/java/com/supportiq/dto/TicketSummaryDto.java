package com.supportiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryDto {

    private String ticketNumber;
    private String subject;
    private boolean existing;
}
