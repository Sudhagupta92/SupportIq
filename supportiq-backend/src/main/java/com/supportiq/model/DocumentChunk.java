package com.supportiq.model;

/**
 * Encapsulates an individual chunk produced by ChunkingService with document position metadata.
 */
public record DocumentChunk(
        int pageNumber,
        int chunkIndex,
        String text
) {}
