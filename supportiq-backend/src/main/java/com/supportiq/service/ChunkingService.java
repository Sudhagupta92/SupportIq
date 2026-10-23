package com.supportiq.service;

import com.supportiq.model.DocumentChunk;
import com.supportiq.model.PageText;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for page-by-page text chunking with overlap and natural boundary detection.
 *
 * Chunks text into approximately 800 characters with approximately 100 characters overlap.
 * Splits on paragraph boundaries ("\n\n"), sentence boundaries (". ", "? ", "! "), or word boundaries
 * where possible, while skipping empty chunks.
 */
@Slf4j
@Getter
@Service
public class ChunkingService {

    private final int chunkSize;
    private final int chunkOverlap;

    public ChunkingService(
            @Value("${app.rag.chunk-size:800}") int chunkSize,
            @Value("${app.rag.chunk-overlap:100}") int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap cannot be negative");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap (" + chunkOverlap + ") must be less than chunkSize (" + chunkSize + ")");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * Splits extracted PDF pages into sequential, boundary-aware chunks.
     * Empty pages or pages with only whitespace are skipped.
     *
     * @param pages list of PageText items extracted from the document
     * @return list of DocumentChunks with pageNumber and global chunkIndex
     */
    public List<DocumentChunk> chunkPages(List<PageText> pages) {
        if (pages == null || pages.isEmpty()) {
            return Collections.emptyList();
        }

        List<DocumentChunk> allChunks = new ArrayList<>();
        int globalChunkIndex = 0;

        for (PageText page : pages) {
            if (page == null) {
                continue;
            }

            String rawText = page.text();
            if (rawText == null || rawText.isBlank()) {
                // Skip empty pages
                continue;
            }

            // Normalize line breaks to standard UNIX style for consistent boundary splitting
            String normalizedText = rawText.replace("\r\n", "\n").replace('\r', '\n').trim();
            if (normalizedText.isEmpty()) {
                continue;
            }

            List<DocumentChunk> pageChunks = chunkSingleText(page.pageNumber(), normalizedText, globalChunkIndex);
            allChunks.addAll(pageChunks);
            globalChunkIndex += pageChunks.size();
        }

        log.info("Produced {} chunks across {} input pages (chunkSize={}, chunkOverlap={})",
                allChunks.size(), pages.size(), chunkSize, chunkOverlap);
        return allChunks;
    }

    /**
     * Chunks text from a single page into overlapping segments.
     */
    private List<DocumentChunk> chunkSingleText(int pageNumber, String text, int startIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int currentChunkIndex = startIndex;

        if (text.length() <= chunkSize) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(new DocumentChunk(pageNumber, currentChunkIndex, trimmed));
            }
            return chunks;
        }

        int start = 0;
        final int textLength = text.length();

        while (start < textLength) {
            int targetEnd = Math.min(start + chunkSize, textLength);

            if (targetEnd < textLength) {
                int boundary = findNaturalBoundary(text, start, targetEnd);
                if (boundary > start) {
                    targetEnd = boundary;
                }
            }

            String chunkContent = text.substring(start, targetEnd).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(new DocumentChunk(pageNumber, currentChunkIndex++, chunkContent));
            }

            if (targetEnd >= textLength) {
                break;
            }

            // Compute next start index with overlap
            int nextStart = targetEnd - chunkOverlap;
            // Guarantee strictly forward progression to avoid infinite loops
            if (nextStart <= start) {
                nextStart = targetEnd;
            }
            start = nextStart;
        }

        return chunks;
    }

    /**
     * Searches backwards from targetEnd for a natural splitting boundary.
     * Hierarchy of boundaries:
     * 1. Paragraph boundary: "\n\n"
     * 2. Sentence boundary: ". ", "! ", "? ", ".\n"
     * 3. Line break: "\n"
     * 4. Word boundary: " "
     */
    private int findNaturalBoundary(String text, int start, int targetEnd) {
        // Only search backwards up to halfway into the chunk or overlap boundary
        int minSearchIndex = Math.max(start + (chunkSize / 2), targetEnd - chunkOverlap - 150);
        if (minSearchIndex >= targetEnd) {
            minSearchIndex = start + 1;
        }

        // 1. Look for paragraph breaks (\n\n)
        int paraIdx = text.lastIndexOf("\n\n", targetEnd);
        if (paraIdx >= minSearchIndex) {
            return paraIdx + 2; // Split after the paragraph break
        }

        // 2. Look for sentence endings (.!?) followed by space or newline
        for (int i = targetEnd - 1; i >= minSearchIndex; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (Character.isWhitespace(next)) {
                    return i + 1; // Split right after the punctuation
                }
            }
        }

        // 3. Look for newline breaks (\n)
        int lineIdx = text.lastIndexOf('\n', targetEnd);
        if (lineIdx >= minSearchIndex) {
            return lineIdx + 1;
        }

        // 4. Look for word space
        int spaceIdx = text.lastIndexOf(' ', targetEnd);
        if (spaceIdx >= minSearchIndex) {
            return spaceIdx + 1;
        }

        // Fallback: hard cut at targetEnd
        return targetEnd;
    }
}
