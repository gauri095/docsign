package com.labmentix.docsign.document.repository;

import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.document.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // ── Owner-scoped queries ───────────────────────────────────

    Page<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Page<Document> findByOwnerIdAndStatusOrderByCreatedAtDesc(
            UUID ownerId, DocumentStatus status, Pageable pageable
    );

    @Query("""
            SELECT d FROM Document d
            WHERE d.owner.id = :ownerId
              AND LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY d.createdAt DESC
            """)
    Page<Document> searchByOwnerAndTitle(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            Pageable pageable
    );

    // ── Single document fetch with owner guard ─────────────────

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    // ── Status transitions ─────────────────────────────────────

    @Modifying
    @Query("UPDATE Document d SET d.status = :status, d.updatedAt = :now WHERE d.id = :id")
    int updateStatus(
            @Param("id") UUID id,
            @Param("status") DocumentStatus status,
            @Param("now") Instant now
    );

    // ── Scheduler queries ──────────────────────────────────────

    @Query("""
            SELECT d FROM Document d
            WHERE d.status IN ('SENT', 'PARTIALLY_SIGNED')
              AND d.expiresAt IS NOT NULL
              AND d.expiresAt < :now
            """)
    List<Document> findExpiredDocuments(@Param("now") Instant now);

    // ── Dashboard stats ────────────────────────────────────────

    @Query("""
            SELECT d.status, COUNT(d)
            FROM Document d
            WHERE d.owner.id = :ownerId
            GROUP BY d.status
            """)
    List<Object[]> countByStatusForOwner(@Param("ownerId") UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);
}