package com.labmentix.docsign.audit.dto;

import com.labmentix.docsign.audit.entity.AuditEventType;
import com.labmentix.docsign.audit.entity.AuditLog;
import com.labmentix.docsign.audit.service.AuditService.ChainVerificationResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AuditDto {

    // ── Single log entry ───────────────────────────────────────

    public record AuditLogResponse(
            UUID           id,
            UUID           documentId,
            UUID           actorId,
            String         actorEmail,
            AuditEventType eventType,
            String         eventDetail,
            String         ipAddress,
            String         userAgent,
            String         hmacHashPrefix,     // first 12 chars — enough to identify, not enough to reconstruct
            Instant        createdAt
    ) {
        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getDocument() != null ? log.getDocument().getId() : null,
                    log.getActor()    != null ? log.getActor().getId()    : null,
                    log.getActorEmail(),
                    log.getEventType(),
                    log.getEventDetail(),
                    log.getIpAddress(),
                    log.getUserAgent(),
                    log.getHmacHash() != null ? log.getHmacHash().substring(0, 12) + "…" : null,
                    log.getCreatedAt()
            );
        }
    }

    // ── Paginated list ─────────────────────────────────────────

    public record AuditPageResponse(
            List<AuditLogResponse> content,
            int    page,
            int    size,
            long   totalElements,
            int    totalPages,
            boolean last
    ) {}

    // ── Chain verification ─────────────────────────────────────

    public record ChainVerificationResponse(
            boolean                     intact,
            int                         totalRows,
            int                         tamperedRows,
            List<ChainFindingResponse>  findings,
            String                      summary,
            Instant                     verifiedAt
    ) {
        public static ChainVerificationResponse from(ChainVerificationResult result) {
            return new ChainVerificationResponse(
                    result.intact(),
                    result.totalRows(),
                    result.tamperedRows(),
                    result.findings().stream().map(f -> new ChainFindingResponse(
                            f.rowId(), f.eventType(), f.createdAt(), f.intact(), f.detail()
                    )).toList(),
                    result.summary(),
                    Instant.now()
            );
        }
    }

    public record ChainFindingResponse(
            UUID    rowId,
            String  eventType,
            Instant createdAt,
            boolean intact,
            String  detail
    ) {}

    // ── Stats per document ─────────────────────────────────────

    public record AuditStatsResponse(
            UUID   documentId,
            long   totalEvents,
            long   signEvents,
            long   downloadEvents,
            long   viewEvents,
            Instant firstEventAt,
            Instant lastEventAt
    ) {}

    // ── Admin search params ────────────────────────────────────

    public record AuditSearchParams(
            AuditEventType eventType,
            String         actorEmail,
            Instant        from,
            Instant        to,
            int            page,
            int            size
    ) {}
}