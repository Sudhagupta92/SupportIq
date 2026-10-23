package com.supportiq.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchRequest {

    @NotBlank(message = "Search query must not be blank")
    private String query;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 10, message = "topK cannot exceed 10")
    @Builder.Default
    private Integer topK = 4;
}
