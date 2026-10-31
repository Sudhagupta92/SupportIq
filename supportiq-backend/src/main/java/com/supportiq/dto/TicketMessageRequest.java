package com.supportiq.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageRequest {

    @NotBlank(message = "Message text is required")
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    @JsonAlias({"body", "text"})
    private String message;

    public String getMessageBody() {
        return message != null ? message.trim() : "";
    }
}
