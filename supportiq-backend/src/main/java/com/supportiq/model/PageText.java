package com.supportiq.model;

/**
 * Encapsulates the extracted raw text for a specific 1-indexed page of a PDF document.
 */
public record PageText(
        int pageNumber,
        String text
) {}
