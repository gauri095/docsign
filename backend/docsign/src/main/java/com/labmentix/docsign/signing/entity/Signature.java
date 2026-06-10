package com.labmentix.docsign.signing.entity;

import com.labmentix.docsign.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signatures")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id", nullable = false)
    private Signer signer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** The field this signature fills (nullable — signer may place outside any predefined field). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id")
    private SignatureField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_type", nullable = false, columnDefinition = "signature_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SignatureType signatureType;

    /**
     * AES-256/GCM encrypted base64-encoded PNG of the signature image.
     * Decrypted only during PDF sealing; never sent to clients.
     */
    @Column(name = "image_data_enc", nullable = false, columnDefinition = "TEXT")
    private String imageDataEnc;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "x_position", nullable = false, precision = 10, scale = 4)
    private BigDecimal xPosition;

    @Column(name = "y_position", nullable = false, precision = 10, scale = 4)
    private BigDecimal yPosition;

    @Column(name = "width", nullable = false, precision = 10, scale = 4)
    private BigDecimal width;

    @Column(name = "height", nullable = false, precision = 10, scale = 4)
    private BigDecimal height;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    @PrePersist
    protected void onCreate() {
        signedAt = Instant.now();
    }
}