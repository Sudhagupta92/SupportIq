package com.supportiq.service;

import com.supportiq.exception.FileTooLargeException;
import com.supportiq.exception.InvalidPdfException;
import com.supportiq.exception.NoExtractableTextException;
import com.supportiq.model.PageText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

    private PdfTextExtractor extractor;

    @BeforeEach
    void setUp() {
        // Max size 10MB = 10485760 bytes
        extractor = new PdfTextExtractor(10485760L);
    }

    private byte[] createTestPdf(List<String> pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);

                if (text != null && !text.isBlank()) {
                    try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                        stream.beginText();
                        stream.setFont(font, 12);
                        stream.newLineAtOffset(50, 700);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Should extract text page by page from a valid multi-page PDF")
    void testValidPdfTextExtraction() throws IOException {
        byte[] pdfBytes = createTestPdf(List.of(
                "Welcome to the SupportIQ policy document. Page 1 text.",
                "Here are the return conditions on Page 2."
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                pdfBytes
        );

        List<PageText> pages = extractor.extractPages(file);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).pageNumber()).isEqualTo(1);
        assertThat(pages.get(0).text()).contains("Welcome to the SupportIQ policy document");

        assertThat(pages.get(1).pageNumber()).isEqualTo(2);
        assertThat(pages.get(1).text()).contains("Here are the return conditions on Page 2");
    }

    @Test
    @DisplayName("Should throw InvalidPdfException when magic bytes are missing or invalid")
    void testNonPdfMagicBytesThrowsInvalidPdfException() {
        byte[] fakeBytes = "Hello this is a plain text file, not a real PDF.".getBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                fakeBytes
        );

        assertThatThrownBy(() -> extractor.extractPages(file))
                .isInstanceOf(InvalidPdfException.class)
                .hasMessageContaining("magic header bytes");
    }

    @Test
    @DisplayName("Should throw InvalidPdfException when content type is not application/pdf")
    void testInvalidContentTypeThrowsInvalidPdfException() throws IOException {
        byte[] pdfBytes = createTestPdf(List.of("Valid text content"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                pdfBytes
        );

        assertThatThrownBy(() -> extractor.extractPages(file))
                .isInstanceOf(InvalidPdfException.class)
                .hasMessageContaining("expected 'application/pdf'");
    }

    @Test
    @DisplayName("Should throw NoExtractableTextException when PDF contains only blank pages (simulating scanned image)")
    void testBlankPdfWithNoExtractableTextThrowsNoExtractableTextException() throws IOException {
        // Create PDF with empty pages
        byte[] blankPdf = createTestPdf(List.of("", "   "));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scanned.pdf",
                "application/pdf",
                blankPdf
        );

        assertThatThrownBy(() -> extractor.extractPages(file))
                .isInstanceOf(NoExtractableTextException.class)
                .hasMessageContaining("no extractable text");
    }

    @Test
    @DisplayName("Should throw InvalidPdfException when file is empty")
    void testEmptyFileThrowsInvalidPdfException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> extractor.extractPages(emptyFile))
                .isInstanceOf(InvalidPdfException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    @DisplayName("Should throw FileTooLargeException when file exceeds maximum allowed bytes")
    void testFileExceedingMaxSizeThrowsFileTooLargeException() {
        PdfTextExtractor smallExtractor = new PdfTextExtractor(100L); // 100 bytes max

        byte[] data = new byte[200];
        data[0] = 0x25; data[1] = 0x50; data[2] = 0x44; data[3] = 0x46; // %PDF

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                data
        );

        assertThatThrownBy(() -> smallExtractor.extractPages(largeFile))
                .isInstanceOf(FileTooLargeException.class)
                .hasMessageContaining("File size exceeds");
    }
}
