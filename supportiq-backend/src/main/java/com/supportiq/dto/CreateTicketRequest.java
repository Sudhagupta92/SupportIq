package com.supportiq.dto;

import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 120, message = "Subject must not exceed 120 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Category is required")
    private TicketCategory category;

    private TicketPriority priority;

    @Size(max = 32, message = "Order number must not exceed 32 characters")
    private String relatedOrderNumber;
}
