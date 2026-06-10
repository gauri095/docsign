package com.labmentix.docsign.signing.entity;

import com.labmentix.docsign.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signers")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Signer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    /** One-use, cryptographically random token embedded in the signing link. */
    @Column(name = "signing_token", nullable = false, unique = true, length = 512)
    private String signingToken;

    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "signer_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private SignerStatus status = SignerStatus.PENDING;

    /**
     * For sequential signing workflows (1 = first, 2 = second, …).
     * Parallel signing uses signing_order = 1 for all signers.
     */
    @Column(name = "signing_order", nullable = false)
    @Builder.Default
    private Integer signingOrder = 1;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    public boolean isTokenExpired() {
        return Instant.now().isAfter(tokenExpiresAt);
    }

    public boolean isTokenValid() {
        return !isTokenExpired() && status != SignerStatus.SIGNED && status != SignerStatus.DECLINED;
    }
}