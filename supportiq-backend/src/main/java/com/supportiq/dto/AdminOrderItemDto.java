package com.supportiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Item summary for admin order responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderItemDto {

    private Long id;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;
}
