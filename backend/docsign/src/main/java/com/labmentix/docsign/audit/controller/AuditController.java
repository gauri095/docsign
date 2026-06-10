package com.labmentix.docsign.audit.controller;

import com.labmentix.docsign.audit.dto.AuditDto.*;
import com.labmentix.docsign.audit.entity.AuditEventType;
import com.labmentix.docsign.audit.entity.AuditLog;
import com.labmentix.docsign.audit.repository.AuditLogRepository;
import com.labmentix.docsign.audit.service.AuditPdfService;
import com.labmentix.docsign.audit.service.AuditService;
import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.common.exception.ForbiddenException;
import com.labmentix.docsign.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log REST API.
 *
 * Base: /api/audit
 *
 * Access:
 * - OWNER: can access logs for their own documents only
 * - ADMIN: can access all logs and run platform-wide verification
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final AuditService       auditService;
    private final AuditPdfService    auditPdfService;
    private final DocumentRepository documentRepository;

    // ── Document audit trail ───────────────────────────────────

    /**
     * GET /api/audit/documents/{documentId}?page=0&size=50
     * Paginated audit log for a specific document.
     */
    @GetMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<AuditPageResponse> getDocumentAuditLog(
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        assertDocumentAccess(documentId, currentUser);

        Page<AuditLog> result = auditLogRepository.findByDocumentIdOrderByCreatedAtDesc(
                documentId, PageRequest.of(page, Math.min(size, 100))
        );

        return ResponseEntity.ok(new AuditPageResponse(
                result.getContent().stream().map(AuditLogResponse::from).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast()
        ));
    }

    /**
     * GET /api/audit/documents/{documentId}/full
     * Complete ordered audit trail (no pagination) — for PDF generation.
     */
    @GetMapping("/documents/{documentId}/full")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> getFullDocumentAuditLog(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User currentUser
    ) {
        assertDocumentAccess(documentId, currentUser);

        List<AuditLogResponse> entries = auditLogRepository
                .findByDocumentIdOrderByCreatedAtAsc(documentId)
                .stream().map(AuditLogResponse::from).toList();

        return ResponseEntity.ok(entries);
    }

    // ── PDF export ─────────────────────────────────────────────

    /**
     * GET /api/audit/documents/{documentId}/export
     * Downloads the audit trail as a formatted PDF.
     */
    @GetMapping("/documents/{documentId}/export")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<byte[]> exportAuditPdf(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User currentUser
    ) throws IOException {
        assertDocumentAccess(documentId, currentUser);

        byte[] pdf = auditPdfService.generateAuditTrailPdf(documentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-trail-" + documentId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── Chain verification ─────────────────────────────────────

    /**
     * POST /api/audit/documents/{documentId}/verify
     * Verifies the HMAC chain integrity for a document's audit log.
     */
    @PostMapping("/documents/{documentId}/verify")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ChainVerificationResponse> verifyDocumentChain(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User currentUser
    ) {
        assertDocumentAccess(documentId, currentUser);

        AuditService.ChainVerificationResult result = auditService.verifyDocumentChain(documentId);
        log.info("Chain verification for document {}: intact={}, rows={}",
                documentId, result.intact(), result.totalRows());

        return ResponseEntity.ok(ChainVerificationResponse.from(result));
    }

    /**
     * POST /api/audit/verify-full
     * Verifies the entire platform audit log chain.
     * ADMIN only — this can be slow on large deployments.
     */
    @PostMapping("/verify-full")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChainVerificationResponse> verifyFullChain() {
        log.warn("Full platform chain verification initiated by admin");
        AuditService.ChainVerificationResult result = auditService.verifyFullChain();
        return ResponseEntity.ok(ChainVerificationResponse.from(result));
    }

    // ── Stats ──────────────────────────────────────────────────

    /**
     * GET /api/audit/documents/{documentId}/stats
     * Event count breakdown for a document.
     */
    @GetMapping("/documents/{documentId}/stats")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Map<String, Long>> getDocumentStats(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User currentUser
    ) {
        assertDocumentAccess(documentId, currentUser);

        List<Object[]> rows = auditLogRepository.countByEventTypeForDocument(documentId);
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        rows.forEach(r -> counts.put(r[0].toString(), (Long) r[1]));

        return ResponseEntity.ok(counts);
    }

    // ── Admin search ───────────────────────────────────────────

    /**
     * GET /api/audit/search?eventType=DOCUMENT_SIGNED&actorEmail=bob&from=...&to=...
     * ADMIN-only filtered search across the entire audit log.
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditPageResponse> search(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size
    ) {
        Page<AuditLog> result = auditLogRepository.search(
                eventType, actorEmail, from, to,
                PageRequest.of(page, Math.min(size, 100))
        );

        return ResponseEntity.ok(new AuditPageResponse(
                result.getContent().stream().map(AuditLogResponse::from).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast()
        ));
    }

    // ── Guard ──────────────────────────────────────────────────

    private void assertDocumentAccess(UUID documentId, User currentUser) {
        if (currentUser.getRole() == User.Role.ADMIN) return;

        documentRepository.findById(documentId).ifPresent(doc -> {
            if (!doc.getOwner().getId().equals(currentUser.getId())) {
                throw new ForbiddenException("You do not have access to this document's audit log");
            }
        });
    }
}