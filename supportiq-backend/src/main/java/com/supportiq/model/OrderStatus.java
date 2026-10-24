package com.supportiq.model;

/**
 * Enumeration representing the lifecycle states of an order.
 */
public enum OrderStatus {
    PLACED,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
