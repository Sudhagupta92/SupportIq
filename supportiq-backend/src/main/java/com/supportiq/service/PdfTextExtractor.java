package com.supportiq.service;

import com.supportiq.exception.FileTooLargeException;
import com.supportiq.exception.InvalidPdfException;
import com.supportiq.exception.NoExtractableTextException;
import com.supportiq.model.PageText;
import com.supportiq.util.DocumentUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for validating PDF integrity and extracting page-by-page text using Apache PDFBox 3.x.
 *
 * All extracted text is treated strictly as untrusted DATA and is never executed or logged in full.
 */
@Slf4j
@Service
public class PdfTextExtractor {

    private final long maxFileSizeBytes;

    public PdfTextExtractor(@Value("${app.rag.max-file-size:10485760}") long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * Validates and extracts text from a MultipartFile.
     */
    public List<PageText> extractPages(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new InvalidPdfException("Uploaded file is empty or missing.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            long maxMb = maxFileSizeBytes / (1024 * 1024);
            throw new FileTooLargeException("File size exceeds the maximum allowed limit of " + maxMb + " MB.");
        }

        String contentType = file.getContentType();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidPdfException("Failed to read uploaded file content: " + e.getMessage());
        }

        return extractPages(bytes, contentType);
    }

    /**
     * Validates and extracts text from raw byte content.
     */
    public List<PageText> extractPages(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidPdfException("PDF byte content is empty.");
        }

        if (bytes.length > maxFileSizeBytes) {
            long maxMb = maxFileSizeBytes / (1024 * 1024);
            throw new FileTooLargeException("File size exceeds the maximum allowed limit of " + maxMb + " MB.");
        }

        // Validate content type
        if (contentType != null && !contentType.isBlank()) {
            String lower = contentType.toLowerCase().trim();
            if (!lower.equals("application/pdf") && !lower.equals("application/x-pdf")) {
                throw new InvalidPdfException("Invalid file type: expected 'application/pdf' but received '" + contentType + "'.");
            }
        }

        // Validate magic bytes
        if (!DocumentUtils.hasPdfMagicBytes(bytes)) {
            throw new InvalidPdfException("Invalid file format: file does not begin with the '%PDF' magic header bytes.");
        }

        List<PageText> pages = new ArrayList<>();
        boolean hasExtractableText = false;

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new InvalidPdfException("Encrypted or password-protected PDF files are not supported.");
            }

            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new NoExtractableTextException("PDF document contains no pages.");
            }

            PDFTextStripper stripper = new PDFTextStripper();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);

                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    hasExtractableText = true;
                    pages.add(new PageText(pageNum, pageText.trim()));
                } else {
                    pages.add(new PageText(pageNum, ""));
                }
            }

            log.info("Extracted text from {} pages in PDF document", totalPages);
        } catch (NoExtractableTextException | InvalidPdfException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Apache PDFBox 3.x failed to parse PDF: {}", e.getMessage());
            throw new InvalidPdfException("Corrupt or invalid PDF format: " + e.getMessage());
        }

        if (!hasExtractableText) {
            throw new NoExtractableTextException("PDF contains no extractable text. Scanned or image-only documents are not supported.");
        }

        return pages;
    }
}
