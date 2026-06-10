package com.labmentix.docsign.audit.entity;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry.
 *
 * Design constraints:
 * - No @Setter — entity fields must never be modified after construction
 * - No @LastModifiedDate — there is no updated_at column in audit_logs
 * - DB triggers (V4 migration) also prevent UPDATE and DELETE at the DB level
 * - actor_email is denormalized: even if the user row is deleted, the email is preserved
 * - hmac_hash chains this row to the previous one (HMAC over key event fields)
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA needs it; nothing else should use it
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Nullable — preserved via ON DELETE SET NULL so audit rows survive document deletion.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    /**
     * Nullable — preserved via ON DELETE SET NULL so audit rows survive user deletion.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    /** Denormalized email — stays readable even after actor row deletion. */
    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, columnDefinition = "audit_event_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AuditEventType eventType;

    /**
     * Free-form JSON context for this event.
     * Examples:
     *   DOCUMENT_UPLOADED  → {"fileName":"nda.pdf","sizeBytes":204800}
     *   DOCUMENT_SIGNED    → {"signerEmail":"bob@co.com","signatureType":"DRAWN"}
     *   USER_LOGIN         → {"method":"password"}
     */
    @Column(name = "event_detail", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String eventDetail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * HMAC-SHA256 of: "eventType|actorEmail|documentId|isoTimestamp"
     * Keyed with the platform JWT secret (app.jwt.secret).
     * Chaining: each row's HMAC input also includes the previous row's hmac_hash,
     * making the entire log a tamper-evident chain.
     */
    @Column(name = "hmac_hash", nullable = false, length = 64)
    private String hmacHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}