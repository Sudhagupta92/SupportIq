package com.supportiq.util;

import java.nio.file.Paths;

/**
 * Utility functions for document sanitization and verification.
 */
public final class DocumentUtils {

    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46}; // "%PDF"

    private DocumentUtils() {
        // Utility class
    }

    /**
     * Sanitizes untrusted user filenames to prevent directory traversal and filesystem attacks.
     * Never executes or trusts filename strings.
     *
     * @param originalFilename the raw filename from the upload
     * @return safe, sanitized filename ending in .pdf
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "policy_document.pdf";
        }

        // Strip path directories and get simple filename
        String name = Paths.get(originalFilename).getFileName().toString();

        // Strip dangerous characters, allowing only alphanumeric, dash, underscore, space, dot
        name = name.replaceAll("[^a-zA-Z0-9._\\- ]", "_");

        // Prevent path traversal dots like ".."
        name = name.replaceAll("\\.{2,}", ".");

        // Clean leading/trailing spaces or dots
        name = name.trim();
        if (name.startsWith(".")) {
            name = name.substring(1);
        }

        if (name.isBlank()) {
            name = "policy_document";
        }

        // Ensure proper .pdf extension
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }

        // Limit length to 150 characters to prevent database column overflow
        if (name.length() > 150) {
            name = name.substring(0, 146) + ".pdf";
        }

        return name;
    }

    /**
     * Verifies if byte array begins with standard %PDF magic bytes (0x25, 0x50, 0x44, 0x46).
     */
    public static boolean hasPdfMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (bytes[i] != PDF_MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }
}
