package com.labmentix.docsign.signing.entity;

import com.labmentix.docsign.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signature_fields")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SignatureField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** The email of the signer this field is assigned to. */
    @Column(name = "assigned_email", nullable = false, length = 255)
    private String assignedEmail;

    /** 1-based page number within the document. */
    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    /** X position as fraction of page width (0.0–1.0). */
    @Column(name = "x_position", nullable = false, precision = 10, scale = 4)
    private BigDecimal xPosition;

    /** Y position as fraction of page height (0.0–1.0). */
    @Column(name = "y_position", nullable = false, precision = 10, scale = 4)
    private BigDecimal yPosition;

    @Column(name = "width", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal width = new BigDecimal("0.2500");

    @Column(name = "height", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal height = new BigDecimal("0.0800");

    /** SIGNATURE | DATE | INITIALS */
    @Column(name = "field_type", nullable = false, length = 50)
    @Builder.Default
    private String fieldType = "SIGNATURE";

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}