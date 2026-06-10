package com.labmentix.docsign.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmentix.docsign.audit.entity.AuditEventType;
import com.labmentix.docsign.audit.entity.AuditLog;
import com.labmentix.docsign.audit.repository.AuditLogRepository;
import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.common.crypto.CryptoUtil;
import com.labmentix.docsign.document.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core audit writing and verification service.
 *
 * Key design decisions:
 *
 * 1. SEPARATE TRANSACTION — each audit write runs in its own transaction
 *    (Propagation.REQUIRES_NEW). This ensures audit rows are committed even
 *    if the caller's transaction later rolls back, and prevents audit writes
 *    from blocking the caller on slow I/O.
 *
 * 2. ASYNC DISPATCH — the AOP aspect calls {@link #recordAsync} which runs
 *    on a separate thread pool. Audit logging never adds latency to API responses.
 *
 * 3. HMAC CHAIN — each row's HMAC covers its own fields PLUS the previous row's
 *    hmac_hash, creating a hash chain. Any retrospective insertion or modification
 *    breaks the chain from that point forward.
 *
 * 4. NO SETTER on AuditLog — entity is fully immutable after construction.
 *    DB triggers (V4 migration) enforce this at the database level too.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final AuditLogRepository auditLogRepository;
    private final CryptoUtil          cryptoUtil;
    private final ObjectMapper        objectMapper;
    private final AuditContext        auditContext;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    // ═══════════════════════════════════════════════════════════
    // PUBLIC WRITE API
    // ═══════════════════════════════════════════════════════════

    /**
     * Fire-and-forget audit write. Called by the AOP aspect.
     * Runs asynchronously on the dedicated audit thread pool.
     */
    /**
     * Fire-and-forget audit write.
     * IP and User-Agent are captured from AuditContext SYNCHRONOUSLY (on the caller's thread)
     * before handing off to the async executor — the request-scoped bean is not available
     * on background threads.
     */
    public void recordAsync(
            AuditEventType eventType,
            User           actor,
            Document       document,
            Map<String, Object> detail
    ) {
        // Capture request-scoped values NOW, on the request thread
        String ip = safeGetIp();
        String ua = safeGetUserAgent();
        // Dispatch to background thread with captured values
        recordAsyncInternal(eventType, actor, document, detail, ip, ua);
    }

    @Async("auditExecutor")
    protected void recordAsyncInternal(
            AuditEventType eventType,
            User           actor,
            Document       document,
            Map<String, Object> detail,
            String         ipAddress,
            String         userAgent
    ) {
        try {
            record(eventType, actor, document, detail, ipAddress, userAgent);
        } catch (Exception e) {
            // Audit failures must NEVER propagate to callers
            log.error("Async audit write failed for event {}: {}", eventType, e.getMessage(), e);
        }
    }

    private String safeGetIp() {
        try { return auditContext.getIpAddress(); } catch (Exception e) { return "unknown"; }
    }

    private String safeGetUserAgent() {
        try { return auditContext.getUserAgent(); } catch (Exception e) { return "unknown"; }
    }

    /**
     * Synchronous audit write with explicit IP/UA (used by public endpoints
     * like PublicSigningController that already have HttpServletRequest).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(
            AuditEventType      eventType,
            User                actor,
            Document            document,
            Map<String, Object> detail,
            String              ipAddress,
            String              userAgent
    ) {
        Instant now        = Instant.now();
        String  isoNow     = ISO_FMT.format(now);
        String  actorEmail = resolveEmail(actor);
        String  docId      = document != null ? document.getId().toString() : "none";
        String  detailJson = toJson(detail);

        // ── Build HMAC input (includes previous chain hash) ──
        String prevHash   = latestHmacHash();
        String hmacInput  = buildChainInput(eventType, actorEmail, docId, isoNow, prevHash);
        String hmacHash   = cryptoUtil.hmacSha256Hex(hmacInput, hmacSecret);

        AuditLog entry = AuditLog.builder()
                .document(document)
                .actor(actor)
                .actorEmail(actorEmail)
                .eventType(eventType)
                .eventDetail(detailJson)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .hmacHash(hmacHash)
                .createdAt(now)
                .build();

        AuditLog saved = auditLogRepository.save(entry);
        log.debug("Audit: {} | actor={} | doc={} | ip={}",
                eventType, actorEmail, docId, ipAddress);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // CHAIN VERIFICATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Verifies the HMAC chain integrity for a specific document's audit log.
     *
     * For each row (in insertion order), recomputes the expected HMAC and
     * compares it to the stored value. A mismatch means either the row
     * was tampered with, or a row was inserted/deleted out of sequence.
     *
     * @return verification result with per-row findings
     */
    @Transactional(readOnly = true)
    public ChainVerificationResult verifyDocumentChain(UUID documentId) {
        List<AuditLog> rows = auditLogRepository.findByDocumentForChainVerification(documentId);
        return verifyChain(rows);
    }

    /**
     * Verifies the entire platform audit log chain.
     * For large logs, this may be slow — run offline or on-demand only.
     */
    @Transactional(readOnly = true)
    public ChainVerificationResult verifyFullChain() {
        List<AuditLog> rows = auditLogRepository.findAllForChainVerification();
        return verifyChain(rows);
    }

    private ChainVerificationResult verifyChain(List<AuditLog> rows) {
        if (rows.isEmpty()) {
            return new ChainVerificationResult(true, rows.size(), 0, List.of(),
                    "No audit entries to verify");
        }

        String prevHash = "GENESIS"; // sentinel for the first row
        int    tampered = 0;
        var    findings = new java.util.ArrayList<ChainFinding>();

        for (AuditLog row : rows) {
            String isoTs    = ISO_FMT.format(row.getCreatedAt());
            String docId    = row.getDocument() != null ? row.getDocument().getId().toString() : "none";
            String input    = buildChainInput(row.getEventType(), row.getActorEmail(), docId, isoTs, prevHash);
            String expected = cryptoUtil.hmacSha256Hex(input, hmacSecret);
            boolean intact  = expected.equals(row.getHmacHash());

            if (!intact) {
                tampered++;
                findings.add(new ChainFinding(
                        row.getId(), row.getEventType().name(),
                        row.getCreatedAt(), false,
                        "HMAC mismatch — expected " + expected + " got " + row.getHmacHash()
                ));
                log.warn("AUDIT CHAIN BREAK at row {} ({})", row.getId(), row.getEventType());
            }
            prevHash = row.getHmacHash();
        }

        boolean allIntact = tampered == 0;
        String  summary   = allIntact
                ? "All " + rows.size() + " audit entries verified — chain intact"
                : "⚠ INTEGRITY FAILURE: " + tampered + " of " + rows.size() + " entries failed verification";

        return new ChainVerificationResult(allIntact, rows.size(), tampered, findings, summary);
    }

    // ═══════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Builds the canonical HMAC input string.
     * Format: "eventType|actorEmail|documentId|isoTimestamp|prevHash"
     * The prevHash links each row to the one before it.
     */
    private String buildChainInput(
            AuditEventType eventType,
            String actorEmail,
            String documentId,
            String isoTimestamp,
            String prevHash
    ) {
        return String.join("|",
                eventType.canonical(),
                actorEmail != null ? actorEmail : "anonymous",
                documentId,
                isoTimestamp,
                prevHash
        );
    }

    /**
     * Fetches the most recent HMAC hash to include in the next row's input.
     * Returns "GENESIS" when the table is empty.
     */
    private String latestHmacHash() {
        return auditLogRepository.findLatestEntry()
                .map(AuditLog::getHmacHash)
                .orElse("GENESIS");
    }

    private String resolveEmail(User actor) {
        return actor != null ? actor.getEmail() : "system";
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            log.warn("Could not serialize audit detail: {}", e.getMessage());
            return "{}";
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VALUE TYPES
    // ═══════════════════════════════════════════════════════════

    public record ChainVerificationResult(
            boolean          intact,
            int              totalRows,
            int              tamperedRows,
            List<ChainFinding> findings,
            String           summary
    ) {}

    public record ChainFinding(
            UUID    rowId,
            String  eventType,
            Instant createdAt,
            boolean intact,
            String  detail
    ) {}
}