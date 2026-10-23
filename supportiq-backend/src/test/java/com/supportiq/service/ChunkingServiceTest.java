package com.supportiq.service;

import com.supportiq.model.DocumentChunk;
import com.supportiq.model.PageText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        // Standard chunk-size 800, overlap 100
        chunkingService = new ChunkingService(800, 100);
    }

    @Test
    @DisplayName("Should skip empty pages and pages with only whitespace")
    void testEmptyAndWhitespaceText() {
        List<PageText> pages = List.of(
                new PageText(1, ""),
                new PageText(2, "   \n\t  \n  "),
                new PageText(3, null)
        );

        List<DocumentChunk> chunks = chunkingService.chunkPages(pages);

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("Should produce a single chunk for page text under chunk size")
    void testSingleShortPage() {
        String shortText = "SupportIQ allows customers to return items within 30 calendar days of delivery.";
        List<PageText> pages = List.of(new PageText(1, shortText));

        List<DocumentChunk> chunks = chunkingService.chunkPages(pages);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).text()).isEqualTo(shortText);
    }

    @Test
    @DisplayName("Should maintain text overlap between adjacent chunks")
    void testChunkingWithOverlap() {
        // Create custom small chunk service: 100 chars chunkSize, 30 chars overlap
        ChunkingService smallChunker = new ChunkingService(100, 30);

        String longSentence = "The standard return window for apparel and footwear is strictly thirty days from delivery. " +
                "Returned items must be unworn, undamaged, with original tags intact. " +
                "Refunds will be processed back to the original method of payment within 5 business days.";

        List<PageText> pages = List.of(new PageText(1, longSentence));
        List<DocumentChunk> chunks = smallChunker.chunkPages(pages);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);

        // Verify sequential chunkIndex
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
            assertThat(chunks.get(i).pageNumber()).isEqualTo(1);
        }

        // Verify that consecutive chunks share overlapping tokens
        for (int i = 0; i < chunks.size() - 1; i++) {
            String current = chunks.get(i).text();
            String next = chunks.get(i + 1).text();

            // The end of current chunk and start of next chunk should share words/text
            String currentTail = current.substring(Math.max(0, current.length() - 25));
            // Find a common substring or token
            String[] words = currentTail.split("\\s+");
            boolean sharedWordFound = false;
            for (String word : words) {
                if (word.length() > 3 && next.contains(word)) {
                    sharedWordFound = true;
                    break;
                }
            }
            assertThat(sharedWordFound)
                    .as("Adjacent chunks at index %d and %d should have overlapping context", i, i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should split very long multi-paragraph text gracefully across natural boundaries")
    void testVeryLongTextWithBoundarySplitting() {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            builder.append("Section ").append(i).append(": This is a detailed policy paragraph explaining rule ")
                    .append(i).append(" for e-commerce store customers.\n\n");
        }
        String longText = builder.toString();

        List<PageText> pages = List.of(new PageText(1, longText));
        List<DocumentChunk> chunks = chunkingService.chunkPages(pages);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(2);

        // Every chunk should be roughly bounded by chunkSize + reasonable tolerance
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(800 + 50);
            assertThat(chunk.text()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Should accurately track page numbers across multi-page documents")
    void testPageNumbersTracking() {
        List<PageText> pages = List.of(
                new PageText(1, "Page 1: Overview of our store shipping guidelines and delivery timelines."),
                new PageText(2, "Page 2: Standard domestic delivery takes between two to five business days."),
                new PageText(3, "Page 3: International priority shipping delivers within three to seven business days.")
        );

        List<DocumentChunk> chunks = chunkingService.chunkPages(pages);

        assertThat(chunks).hasSize(3);

        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).text()).contains("Page 1");

        assertThat(chunks.get(1).pageNumber()).isEqualTo(2);
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
        assertThat(chunks.get(1).text()).contains("Page 2");

        assertThat(chunks.get(2).pageNumber()).isEqualTo(3);
        assertThat(chunks.get(2).chunkIndex()).isEqualTo(2);
        assertThat(chunks.get(2).text()).contains("Page 3");
    }

    @Test
    @DisplayName("Should validate constructor arguments")
    void testInvalidConstructorArguments() {
        assertThatThrownBy(() -> new ChunkingService(0, 50))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ChunkingService(500, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ChunkingService(500, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
