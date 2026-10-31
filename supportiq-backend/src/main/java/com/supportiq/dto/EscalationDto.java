package com.supportiq.dto;

import com.supportiq.model.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationDto {

    private boolean suggested;
    private String reason;
    private TicketPriority priority;

    public static EscalationDto notSuggested() {
        return EscalationDto.builder()
                .suggested(false)
                .reason(null)
                .priority(null)
                .build();
    }
}
