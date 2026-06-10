package com.labmentix.docsign.audit.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.labmentix.docsign.audit.entity.AuditLog;
import com.labmentix.docsign.audit.repository.AuditLogRepository;
import com.labmentix.docsign.audit.service.AuditService.ChainVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Generates a legally-formatted audit trail PDF for a document
 * using iText 8.
 *
 * The PDF contains:
 * - Cover page: document metadata, chain verification status, generated timestamp
 * - Event table: one row per audit entry with timestamp, actor, event, IP
 * - Footer: DocSign branding and HMAC chain status on every page
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditPdfService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'UTC'")
                             .withZone(ZoneOffset.UTC);

    private static final DeviceRgb COLOR_HEADER    = new DeviceRgb(0x0D, 0x11, 0x17);  // ink-900
    private static final DeviceRgb COLOR_ACCENT    = new DeviceRgb(0x00, 0xD4, 0xAA);  // teal
    private static final DeviceRgb COLOR_ROW_ALT   = new DeviceRgb(0xF5, 0xF7, 0xFA);
    private static final DeviceRgb COLOR_ROW_EVEN  = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb COLOR_WARN      = new DeviceRgb(0xFF, 0x4D, 0x4D);

    private final AuditLogRepository auditLogRepository;
    private final AuditService       auditService;

    // ═══════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates a full audit trail PDF for a document.
     *
     * @param documentId the document to generate the trail for
     * @return PDF bytes ready to stream to the client
     */
    @Transactional(readOnly = true)
    public byte[] generateAuditTrailPdf(UUID documentId) throws IOException {
        List<AuditLog>          entries      = auditLogRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        ChainVerificationResult chainResult  = auditService.verifyDocumentChain(documentId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PdfWriter  writer  = new PdfWriter(out);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document    layout = new Document(pdfDoc, PageSize.A4)) {

            layout.setMargins(50, 50, 60, 50);

            PdfFont fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont fontBold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontMono    = PdfFontFactory.createFont(StandardFonts.COURIER);

            // ── Cover section ──────────────────────────────────
            addCoverSection(layout, documentId, entries, chainResult, fontBold, fontRegular, fontMono);

            // ── Page break ─────────────────────────────────────
            layout.add(new AreaBreak());

            // ── Events section header ──────────────────────────
            addSectionHeader(layout, "Audit Event Log", fontBold);

            if (entries.isEmpty()) {
                layout.add(new Paragraph("No audit events recorded for this document.")
                        .setFont(fontRegular).setFontSize(10)
                        .setFontColor(ColorConstants.GRAY));
            } else {
                addEventTable(layout, entries, chainResult, fontBold, fontRegular, fontMono);
            }

            // ── Footer on every page ───────────────────────────
            addPageFooters(pdfDoc, fontRegular, fontMono, chainResult.intact());
        }

        log.info("Audit trail PDF generated for document {} ({} events)", documentId, entries.size());
        return out.toByteArray();
    }

    // ═══════════════════════════════════════════════════════════
    // COVER SECTION
    // ═══════════════════════════════════════════════════════════

    private void addCoverSection(
            Document layout,
            UUID documentId,
            List<AuditLog> entries,
            ChainVerificationResult chain,
            PdfFont bold, PdfFont regular, PdfFont mono
    ) throws IOException {

        // DocSign logo wordmark
        layout.add(new Paragraph("DocSign")
                .setFont(bold)
                .setFontSize(22)
                .setFontColor(COLOR_ACCENT));

        layout.add(new Paragraph("Audit Trail Certificate")
                .setFont(bold)
                .setFontSize(16)
                .setFontColor(new DeviceRgb(0x1E, 0x2A, 0x38))
                .setMarginTop(2));

        // Horizontal rule
        layout.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f))
                .setMarginTop(10).setMarginBottom(16));

        // Metadata grid
        Table meta = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        addMetaRow(meta, "Document ID",    documentId.toString(), regular, mono);
        addMetaRow(meta, "Total Events",   String.valueOf(entries.size()), regular, regular);
        addMetaRow(meta, "Generated At",   DISPLAY_FMT.format(java.time.Instant.now()), regular, regular);

        if (!entries.isEmpty()) {
            addMetaRow(meta, "First Event",
                    DISPLAY_FMT.format(entries.get(0).getCreatedAt()), regular, regular);
            addMetaRow(meta, "Last Event",
                    DISPLAY_FMT.format(entries.get(entries.size() - 1).getCreatedAt()), regular, regular);
        }

        layout.add(meta);

        // Chain integrity status banner
        boolean intact = chain.intact();
        Table banner = new Table(1)
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        Cell bannerCell = new Cell()
                .setBackgroundColor(intact ? new DeviceRgb(0x00, 0x2A, 0x22) : new DeviceRgb(0x2A, 0x0E, 0x0E))
                .setBorder(new SolidBorder(intact ? COLOR_ACCENT : COLOR_WARN, 1))
                .setPadding(12);

        String icon    = intact ? "✓" : "✗";
        String status  = intact ? "CHAIN INTEGRITY VERIFIED" : "⚠ INTEGRITY FAILURE DETECTED";
        String subtext = intact
                ? "All " + chain.totalRows() + " audit entries have been cryptographically verified."
                : chain.tamperedRows() + " of " + chain.totalRows() + " entries failed HMAC verification.";

        bannerCell.add(new Paragraph(icon + "  " + status)
                .setFont(bold).setFontSize(11)
                .setFontColor(intact ? COLOR_ACCENT : COLOR_WARN));
        bannerCell.add(new Paragraph(subtext)
                .setFont(regular).setFontSize(9)
                .setFontColor(intact ? new DeviceRgb(0xA8, 0xBD, 0xD0) : new DeviceRgb(0xFF, 0x9A, 0x9A)));

        banner.addCell(bannerCell);
        layout.add(banner);

        // Legal notice
        layout.add(new Paragraph(
                "This document constitutes a legally admissible audit trail generated by the " +
                "DocSign electronic signature platform. Each event entry is secured by an " +
                "HMAC-SHA256 hash chain using a platform secret key. Any modification to the " +
                "audit log, including insertion, deletion, or alteration of entries, will " +
                "break the cryptographic chain and be detected upon verification.")
                .setFont(regular)
                .setFontSize(8.5f)
                .setFontColor(ColorConstants.GRAY)
                .setMarginTop(8));
    }

    // ═══════════════════════════════════════════════════════════
    // EVENT TABLE
    // ═══════════════════════════════════════════════════════════

    private void addEventTable(
            Document layout,
            List<AuditLog> entries,
            ChainVerificationResult chain,
            PdfFont bold, PdfFont regular, PdfFont mono
    ) {
        // Build a set of tampered row IDs for quick lookup
        java.util.Set<UUID> tamperedIds = new java.util.HashSet<>();
        chain.findings().forEach(f -> tamperedIds.add(f.rowId()));

        Table table = new Table(UnitValue.createPercentArray(new float[]{18, 20, 22, 18, 16, 6}))
                .setWidth(UnitValue.createPercentValue(100))
                .setFontSize(8.5f);

        // Header row
        String[] headers = {"Timestamp (UTC)", "Event", "Actor", "IP Address", "HMAC (first 12)", "OK"};
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .setBackgroundColor(COLOR_HEADER)
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(6)
                    .add(new Paragraph(h)
                            .setFont(bold)
                            .setFontSize(8)
                            .setFontColor(COLOR_ACCENT)));
        }

        // Data rows
        for (int i = 0; i < entries.size(); i++) {
            AuditLog entry = entries.get(i);
            boolean tampered = tamperedIds.contains(entry.getId());
            DeviceRgb rowBg  = tampered
                    ? new DeviceRgb(0x2A, 0x0E, 0x0E)
                    : (i % 2 == 0 ? COLOR_ROW_EVEN : COLOR_ROW_ALT);

            addTableCell(table, DISPLAY_FMT.format(entry.getCreatedAt()), regular, rowBg, false);
            addTableCell(table, formatEventType(entry.getEventType().name()), bold, rowBg, false);
            addTableCell(table, entry.getActorEmail() != null ? entry.getActorEmail() : "—", regular, rowBg, false);
            addTableCell(table, entry.getIpAddress()  != null ? entry.getIpAddress()  : "—", mono, rowBg, false);
            addTableCell(table, entry.getHmacHash().substring(0, 12) + "…", mono, rowBg, false);

            // Integrity column
            Cell integrityCell = new Cell()
                    .setBackgroundColor(rowBg)
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(5)
                    .setTextAlignment(TextAlignment.CENTER);
            integrityCell.add(new Paragraph(tampered ? "✗" : "✓")
                    .setFont(bold)
                    .setFontColor(tampered ? COLOR_WARN : COLOR_ACCENT));
            table.addCell(integrityCell);
        }

        layout.add(table);

        // Tamper findings detail
        if (!chain.findings().isEmpty()) {
            layout.add(new Paragraph("Integrity Failures")
                    .setFont(bold).setFontSize(11).setMarginTop(20).setFontColor(COLOR_WARN));
            for (var f : chain.findings()) {
                layout.add(new Paragraph(
                        "Row " + f.rowId() + " — " + f.eventType() +
                        " at " + DISPLAY_FMT.format(f.createdAt()) + "\n" + f.detail())
                        .setFont(mono).setFontSize(8).setFontColor(COLOR_WARN));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PAGE FOOTERS
    // ═══════════════════════════════════════════════════════════

    private void addPageFooters(
            PdfDocument pdfDoc,
            PdfFont regular,
            PdfFont mono,
            boolean chainIntact
    ) throws IOException {
        String generated = DISPLAY_FMT.format(java.time.Instant.now());
        String status    = chainIntact ? "CHAIN VERIFIED" : "INTEGRITY FAILURE";

        for (int page = 1; page <= pdfDoc.getNumberOfPages(); page++) {
            PdfCanvas canvas = new PdfCanvas(pdfDoc.getPage(page));
            PageSize   ps    = pdfDoc.getDefaultPageSize();

            // Separator line
            canvas.setStrokeColor(new DeviceRgb(0x26, 0x33, 0x44))
                  .setLineWidth(0.4f)
                  .moveTo(50, 45)
                  .lineTo(ps.getWidth() - 50, 45)
                  .stroke();

            // Footer text
            canvas.beginText()
                  .setFontAndSize(regular, 7f)
                  .setFillColor(ColorConstants.GRAY)
                  .moveText(50, 32)
                  .showText("DocSign Audit Trail · Generated " + generated +
                            " · Cryptographic Status: " + status)
                  .moveText(ps.getWidth() - 160, 0)
                  .showText("Page " + page + " of " + pdfDoc.getNumberOfPages())
                  .endText()
                  .release();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private void addSectionHeader(Document layout, String title, PdfFont bold) {
        layout.add(new Paragraph(title)
                .setFont(bold)
                .setFontSize(13)
                .setFontColor(new DeviceRgb(0x1E, 0x2A, 0x38))
                .setMarginBottom(12));
    }

    private void addMetaRow(Table table, String label, String value, PdfFont labelFont, PdfFont valueFont) {
        table.addCell(new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingBottom(6)
                .add(new Paragraph(label)
                        .setFont(labelFont).setFontSize(9)
                        .setFontColor(ColorConstants.GRAY)));
        table.addCell(new Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingBottom(6)
                .add(new Paragraph(value)
                        .setFont(valueFont).setFontSize(9)));
    }

    private void addTableCell(Table table, String value, PdfFont font, DeviceRgb bg, boolean center) {
        Cell cell = new Cell()
                .setBackgroundColor(bg)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(5);
        Paragraph p = new Paragraph(value != null ? value : "—")
                .setFont(font).setFontSize(8);
        if (center) p.setTextAlignment(TextAlignment.CENTER);
        table.addCell(cell.add(p));
    }

    private String formatEventType(String raw) {
        return raw.replace('_', ' ');
    }
}