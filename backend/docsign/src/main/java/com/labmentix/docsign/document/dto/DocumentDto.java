package com.labmentix.docsign.document.dto;

import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.document.entity.DocumentStatus;
import com.labmentix.docsign.document.entity.DocumentVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DocumentDto {

    // ── Requests ───────────────────────────────────────────────

    public record UpdateDocumentRequest(
            @NotBlank @Size(max = 255)
            String title,

            @Size(max = 2000)
            String description,

            List<String> tags
    ) {}

    public record UploadNewVersionRequest(
            @Size(max = 500)
            String changeNote
    ) {}

    public record UpdateStatusRequest(
            DocumentStatus targetStatus
    ) {}

    // ── Responses ──────────────────────────────────────────────

    public record DocumentResponse(
            UUID           id,
            String         title,
            String         description,
            String         originalName,
            String         mimeType,
            long           fileSizeBytes,
            String         sha256Hash,
            DocumentStatus status,
            int            currentVersion,
            Integer        pageCount,
            String[]       tags,
            Instant        expiresAt,
            Instant        completedAt,
            Instant        createdAt,
            Instant        updatedAt,
            OwnerInfo      owner
    ) {
        public static DocumentResponse from(Document d) {
            return new DocumentResponse(
                    d.getId(),
                    d.getTitle(),
                    d.getDescription(),
                    d.getOriginalName(),
                    d.getMimeType(),
                    d.getFileSizeBytes(),
                    d.getSha256Hash(),
                    d.getStatus(),
                    d.getCurrentVersion(),
                    d.getPageCount(),
                    d.getTags(),
                    d.getExpiresAt(),
                    d.getCompletedAt(),
                    d.getCreatedAt(),
                    d.getUpdatedAt(),
                    new OwnerInfo(d.getOwner().getId(), d.getOwner().getName(), d.getOwner().getEmail())
            );
        }
    }

    public record DocumentListResponse(
            UUID           id,
            String         title,
            String         originalName,
            String         mimeType,
            long           fileSizeBytes,
            DocumentStatus status,
            int            currentVersion,
            Integer        pageCount,
            String[]       tags,
            Instant        createdAt,
            Instant        updatedAt
    ) {
        public static DocumentListResponse from(Document d) {
            return new DocumentListResponse(
                    d.getId(),
                    d.getTitle(),
                    d.getOriginalName(),
                    d.getMimeType(),
                    d.getFileSizeBytes(),
                    d.getStatus(),
                    d.getCurrentVersion(),
                    d.getPageCount(),
                    d.getTags(),
                    d.getCreatedAt(),
                    d.getUpdatedAt()
            );
        }
    }

    public record VersionResponse(
            UUID    id,
            int     versionNumber,
            String  s3Key,
            String  originalName,
            long    fileSizeBytes,
            String  sha256Hash,
            String  changeNote,
            Instant createdAt,
            String  uploadedByName
    ) {
        public static VersionResponse from(DocumentVersion v) {
            return new VersionResponse(
                    v.getId(),
                    v.getVersionNumber(),
                    v.getS3Key(),
                    v.getOriginalName(),
                    v.getFileSizeBytes(),
                    v.getSha256Hash(),
                    v.getChangeNote(),
                    v.getCreatedAt(),
                    v.getUploadedBy().getName()
            );
        }
    }

    public record DashboardStats(
            long totalDocuments,
            long draft,
            long sent,
            long partiallySigned,
            long completed,
            long expired,
            long cancelled
    ) {}

    public record VerifyIntegrityResponse(
            boolean intact,
            String  storedHash,
            String  computedHash,
            String  message
    ) {}

    // ── Nested value types ─────────────────────────────────────

    public record OwnerInfo(UUID id, String name, String email) {}

    public record PagedResponse<T>(
            List<T>  content,
            int      page,
            int      size,
            long     totalElements,
            int      totalPages,
            boolean  last
    ) {}
}