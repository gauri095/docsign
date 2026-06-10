package com.labmentix.docsign.signing.dto;

import com.labmentix.docsign.signing.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SigningDto {

    // ── Signature field placement ──────────────────────────────

    public record FieldPlacementRequest(
            @NotBlank @Email
            String assignedEmail,

            @NotNull @Min(1)
            Integer pageNumber,

            @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
            BigDecimal xPosition,

            @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
            BigDecimal yPosition,

            @DecimalMin("0.01") @DecimalMax("1.0")
            BigDecimal width,

            @DecimalMin("0.01") @DecimalMax("1.0")
            BigDecimal height,

            /** SIGNATURE | DATE | INITIALS */
            String fieldType,

            String label,

            boolean required
    ) {}

    public record BulkFieldRequest(
            @NotEmpty @Valid
            List<FieldPlacementRequest> fields
    ) {}

    public record FieldResponse(
            UUID       id,
            UUID       documentId,
            String     assignedEmail,
            int        pageNumber,
            BigDecimal xPosition,
            BigDecimal yPosition,
            BigDecimal width,
            BigDecimal height,
            String     fieldType,
            String     label,
            boolean    required,
            Instant    createdAt
    ) {
        public static FieldResponse from(SignatureField f) {
            return new FieldResponse(
                    f.getId(), f.getDocument().getId(),
                    f.getAssignedEmail(), f.getPageNumber(),
                    f.getXPosition(), f.getYPosition(),
                    f.getWidth(), f.getHeight(),
                    f.getFieldType(), f.getLabel(),
                    f.isRequired(), f.getCreatedAt()
            );
        }
    }

    // ── Send for signing ───────────────────────────────────────

    public record SendForSigningRequest(
            @NotEmpty @Valid
            List<SignerRequest> signers,

            /** Hours until signing links expire (default from config). */
            Integer expiryHours
    ) {}

    public record SignerRequest(
            @NotBlank @Size(max = 100)
            String name,

            @NotBlank @Email
            String email,

            @Min(1)
            Integer signingOrder
    ) {}

    public record SendForSigningResponse(
            UUID         documentId,
            String       documentTitle,
            int          signerCount,
            List<String> signerEmails,
            Instant      expiresAt,
            String       message
    ) {}

    // ── Public signing page (no auth) ─────────────────────────

    public record PublicSigningContext(
            UUID              documentId,
            String            documentTitle,
            String            signerName,
            String            signerEmail,
            SignerStatus      signerStatus,
            List<FieldResponse> fields,
            int               totalPages,
            String            documentDownloadUrl   // presigned URL for PDF.js
    ) {}

    // ── Submit signature ───────────────────────────────────────

    public record SubmitSignatureRequest(
            /**
             * Base64-encoded PNG of the drawn/typed/uploaded signature.
             * Must be a valid data URI or raw base64 PNG.
             */
            @NotBlank
            String signatureImageBase64,

            @NotNull
            SignatureType signatureType,

            /** Which predefined field this fills. May be null for freehand placement. */
            UUID fieldId,

            @NotNull @Min(1)
            Integer pageNumber,

            @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
            BigDecimal xPosition,

            @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
            BigDecimal yPosition,

            @NotNull @DecimalMin("0.01") @DecimalMax("1.0")
            BigDecimal width,

            @NotNull @DecimalMin("0.01") @DecimalMax("1.0")
            BigDecimal height
    ) {}

    public record SubmitSignatureResponse(
            String  message,
            boolean documentCompleted,
            UUID    documentId
    ) {}

    // ── Signer info ────────────────────────────────────────────

    public record SignerResponse(
            UUID         id,
            String       name,
            String       email,
            SignerStatus status,
            int          signingOrder,
            Instant      viewedAt,
            Instant      signedAt,
            Instant      tokenExpiresAt,
            Instant      createdAt
    ) {
        public static SignerResponse from(com.labmentix.docsign.signing.entity.Signer s) {
            return new SignerResponse(
                    s.getId(), s.getName(), s.getEmail(),
                    s.getStatus(), s.getSigningOrder(),
                    s.getViewedAt(), s.getSignedAt(),
                    s.getTokenExpiresAt(), s.getCreatedAt()
            );
        }
    }

    // ── Decline ────────────────────────────────────────────────

    public record DeclineRequest(
            @Size(max = 500)
            String reason
    ) {}
}