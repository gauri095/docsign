package com.labmentix.docsign.document.service;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * PDF processing service using iText 8.
 *
 * Responsibilities:
 * - Extract page count from uploaded PDFs
 * - Validate MIME type is a real PDF
 * - Embed signature images at precise coordinates
 * - Stamp a completion footer + seal the document
 */
@Service
@Slf4j
public class PdfService {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                             .withZone(ZoneOffset.UTC);

    // ── Page count / validation ────────────────────────────────

    /**
     * Reads the PDF and returns the number of pages.
     * Also validates that the bytes are a valid PDF.
     *
     * @throws IllegalArgumentException if the bytes are not a valid PDF
     */
    public int extractPageCount(byte[] pdfBytes) {
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfDocument pdf  = new PdfDocument(reader)) {
            return pdf.getNumberOfPages();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or corrupt PDF file: " + e.getMessage(), e);
        }
    }

    /**
     * Lightweight check: confirms first 5 bytes are the PDF magic number %PDF-.
     */
    public boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) return false;
        return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
            && bytes[3] == 'F' && bytes[4] == '-';
    }

    // ── Signature embedding ────────────────────────────────────

    /**
     * Embeds one or more signature images into a PDF at specified coordinates,
     * then stamps a completion banner on the final page and seals the result.
     *
     * @param sourcePdf   original PDF bytes
     * @param placements  list of signature placement descriptors
     * @param documentId  used in the completion footer
     * @param sha256Hash  original document hash, stamped for traceability
     * @return sealed PDF bytes
     */
    public byte[] embedSignaturesAndSeal(
            byte[] sourcePdf,
            List<SignaturePlacement> placements,
            String documentId,
            String sha256Hash
    ) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PdfReader reader      = new PdfReader(new ByteArrayInputStream(sourcePdf));
             PdfWriter writer      = new PdfWriter(out);
             PdfDocument pdfDoc    = new PdfDocument(reader, writer);
             Document    layout    = new Document(pdfDoc)) {

            // ── Embed each signature image ─────────────────────
            for (SignaturePlacement p : placements) {
                PdfPage page = pdfDoc.getPage(p.pageNumber());

                // Decode base64 PNG → iText image
                byte[] imageBytes = Base64.getDecoder().decode(p.base64ImagePng());
                PdfImageXObject sigImage = new PdfImageXObject(
                        ImageDataFactory.create(imageBytes)
                );

                // Convert percentage coords to absolute points
                Rectangle pageSize = page.getPageSize();
                float absX = p.xPct() * pageSize.getWidth();
                float absY = p.yPct() * pageSize.getHeight();
                float absW = p.widthPct() * pageSize.getWidth();
                float absH = p.heightPct() * pageSize.getHeight();

                PdfCanvas canvas = new PdfCanvas(page);
                canvas.addXObjectAt(sigImage, absX, absY);
                canvas.release();
            }

            // ── Completion footer on last page ─────────────────
            stampCompletionFooter(pdfDoc, documentId, sha256Hash, layout);

            layout.close();
        }

        return out.toByteArray();
    }

    // ── Private helpers ────────────────────────────────────────

    private void stampCompletionFooter(
            PdfDocument pdfDoc,
            String documentId,
            String sha256Hash,
            Document layout
    ) throws IOException {

        int lastPage    = pdfDoc.getNumberOfPages();
        PdfPage page    = pdfDoc.getPage(lastPage);
        Rectangle size  = page.getPageSize();

        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfCanvas canvas = new PdfCanvas(page);

        // Draw a thin separator line
        canvas.setStrokeColor(ColorConstants.LIGHT_GRAY)
              .setLineWidth(0.5f)
              .moveTo(40, 60)
              .lineTo(size.getWidth() - 40, 60)
              .stroke();

        // Footer text
        String completedAt = TS_FMT.format(Instant.now());
        String footer1 = "Electronically signed via DocSign | Document ID: " + documentId;
        String footer2 = "Completed: " + completedAt + " | SHA-256: " + sha256Hash;

        canvas.beginText()
              .setFontAndSize(font, 6.5f)
              .setFillColor(ColorConstants.GRAY)
              .moveText(40, 48)
              .showText(footer1)
              .moveText(0, -10)
              .showText(footer2)
              .endText()
              .release();
    }

    // ── Value types ────────────────────────────────────────────

    /**
     * Describes where and what to embed as a signature.
     *
     * @param pageNumber     1-based page number
     * @param base64ImagePng base64-encoded PNG of the signature drawing
     * @param xPct           x position as fraction of page width  (0.0–1.0)
     * @param yPct           y position as fraction of page height (0.0–1.0)
     * @param widthPct       signature width as fraction of page width
     * @param heightPct      signature height as fraction of page height
     * @param signerEmail    for logging
     */
    public record SignaturePlacement(
            int    pageNumber,
            String base64ImagePng,
            float  xPct,
            float  yPct,
            float  widthPct,
            float  heightPct,
            String signerEmail
    ) {}
}