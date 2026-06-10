package com.labmentix.docsign.audit.repository;

import com.labmentix.docsign.audit.entity.AuditEventType;
import com.labmentix.docsign.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // ── Document-scoped queries ────────────────────────────────

    /** All events for a document, oldest-first. Used for audit trail export. */
    List<AuditLog> findByDocumentIdOrderByCreatedAtAsc(UUID documentId);

    /** Paginated events for a document, newest-first. Used in the UI. */
    Page<AuditLog> findByDocumentIdOrderByCreatedAtDesc(UUID documentId, Pageable pageable);

    long countByDocumentId(UUID documentId);

    // ── Actor-scoped queries ───────────────────────────────────

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    // ── Chain verification ─────────────────────────────────────

    /**
     * Returns all rows in insertion order for HMAC chain verification.
     * Ordered by created_at ASC, then id ASC (tie-breaker for same-millisecond events).
     */
    @Query("""
           SELECT a FROM AuditLog a
           ORDER BY a.createdAt ASC, a.id ASC
           """)
    List<AuditLog> findAllForChainVerification();

    /**
     * Returns all rows for a specific document in chain order.
     */
    @Query("""
           SELECT a FROM AuditLog a
           WHERE a.document.id = :docId
           ORDER BY a.createdAt ASC, a.id ASC
           """)
    List<AuditLog> findByDocumentForChainVerification(@Param("docId") UUID documentId);

    /**
     * Fetches the single most-recently written row.
     * Used by AuditService to get the "previous HMAC" for chaining.
     */
    @Query("""
           SELECT a FROM AuditLog a
           ORDER BY a.createdAt DESC, a.id DESC
           LIMIT 1
           """)
    Optional<AuditLog> findLatestEntry();

    // ── Admin / search queries ─────────────────────────────────

    @Query("""
           SELECT a FROM AuditLog a
           WHERE (:eventType IS NULL OR a.eventType = :eventType)
             AND (:actorEmail IS NULL OR LOWER(a.actorEmail) LIKE LOWER(CONCAT('%', :actorEmail, '%')))
             AND (:from IS NULL OR a.createdAt >= :from)
             AND (:to   IS NULL OR a.createdAt <= :to)
           ORDER BY a.createdAt DESC
           """)
    Page<AuditLog> search(
            @Param("eventType")  AuditEventType eventType,
            @Param("actorEmail") String actorEmail,
            @Param("from")       Instant from,
            @Param("to")         Instant to,
            Pageable pageable
    );

    // ── Stats ──────────────────────────────────────────────────

    @Query("""
           SELECT a.eventType, COUNT(a)
           FROM AuditLog a
           WHERE a.document.id = :docId
           GROUP BY a.eventType
           """)
    List<Object[]> countByEventTypeForDocument(@Param("docId") UUID documentId);
}