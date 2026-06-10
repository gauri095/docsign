package com.labmentix.docsign.document.service;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.common.crypto.CryptoUtil;
import com.labmentix.docsign.common.exception.BadRequestException;
import com.labmentix.docsign.common.exception.ForbiddenException;
import com.labmentix.docsign.common.exception.ResourceNotFoundException;
import com.labmentix.docsign.common.storage.StorageService;
import com.labmentix.docsign.document.dto.DocumentDto.*;
import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.document.entity.DocumentStatus;
import com.labmentix.docsign.document.entity.DocumentVersion;
import com.labmentix.docsign.document.fsm.DocumentFsm;
import com.labmentix.docsign.document.repository.DocumentRepository;
import com.labmentix.docsign.document.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"  // .docx
    );
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;   // 50 MB

    private final DocumentRepository        documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageService            storageService;
    private final CryptoUtil                cryptoUtil;
    private final PdfService                pdfService;

    // ── Upload ─────────────────────────────────────────────────

    /**
     * Handles initial document upload:
     * 1. Validates file type and size
     * 2. Computes SHA-256 fingerprint
     * 3. Stores to MinIO
     * 4. Persists Document + DocumentVersion entities
     * 5. Extracts page count (PDFs only)
     */
    @Transactional
    public DocumentResponse uploadDocument(
            MultipartFile file,
            String title,
            String description,
            List<String> tags,
            User owner
    ) throws IOException {

        validateFile(file);

        byte[] bytes    = file.getBytes();
        String hash     = cryptoUtil.sha256Hex(bytes);
        String mimeType = sanitizeMimeType(file.getContentType());

        // Extract page count for PDFs
        Integer pageCount = null;
        if ("application/pdf".equals(mimeType)) {
            if (!pdfService.isPdf(bytes)) {
                throw new BadRequestException("File claims to be PDF but fails magic-byte validation");
            }
            pageCount = pdfService.extractPageCount(bytes);
        }

        // Build storage key and persist to MinIO
        UUID docId = UUID.randomUUID();
        String s3Key = StorageService.buildDocumentKey(docId, 1, file.getOriginalFilename());
        storageService.upload(s3Key, bytes, mimeType);

        // Persist Document entity
        Document doc = Document.builder()
                .id(docId)
                .owner(owner)
                .title(title.trim())
                .description(description)
                .s3Key(s3Key)
                .originalName(file.getOriginalFilename())
                .mimeType(mimeType)
                .fileSizeBytes((long) bytes.length)
                .sha256Hash(hash)
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .pageCount(pageCount)
                .tags(tags != null ? tags.toArray(String[]::new) : null)
                .build();

        documentRepository.save(doc);

        // Persist version snapshot
        DocumentVersion version = DocumentVersion.builder()
                .document(doc)
                .versionNumber(1)
                .s3Key(s3Key)
                .originalName(file.getOriginalFilename())
                .fileSizeBytes((long) bytes.length)
                .sha256Hash(hash)
                .uploadedBy(owner)
                .changeNote("Initial upload")
                .build();

        versionRepository.save(version);

        log.info("Document uploaded: {} ({}) by {}", doc.getId(), doc.getTitle(), owner.getEmail());
        return DocumentResponse.from(doc);
    }

    // ── Upload new version ─────────────────────────────────────

    /**
     * Replaces the document content with a new file version.
     * Only allowed when document is in DRAFT status.
     */
    @Transactional
    public DocumentResponse uploadNewVersion(
            UUID documentId,
            MultipartFile file,
            String changeNote,
            User requester
    ) throws IOException {

        Document doc = getOwnedDocument(documentId, requester);

        if (!doc.getStatus().isMutable()) {
            throw new BadRequestException(
                "Cannot upload a new version when document status is: " + doc.getStatus()
            );
        }

        validateFile(file);
        byte[] bytes    = file.getBytes();
        String hash     = cryptoUtil.sha256Hex(bytes);
        String mimeType = sanitizeMimeType(file.getContentType());

        int nextVersion = doc.getCurrentVersion() + 1;
        String s3Key    = StorageService.buildDocumentKey(documentId, nextVersion, file.getOriginalFilename());
        storageService.upload(s3Key, bytes, mimeType);

        // Update page count if PDF
        if ("application/pdf".equals(mimeType)) {
            doc.setPageCount(pdfService.extractPageCount(bytes));
        }

        doc.setS3Key(s3Key);
        doc.setOriginalName(file.getOriginalFilename());
        doc.setMimeType(mimeType);
        doc.setFileSizeBytes((long) bytes.length);
        doc.setSha256Hash(hash);
        doc.setCurrentVersion(nextVersion);

        documentRepository.save(doc);

        DocumentVersion version = DocumentVersion.builder()
                .document(doc)
                .versionNumber(nextVersion)
                .s3Key(s3Key)
                .originalName(file.getOriginalFilename())
                .fileSizeBytes((long) bytes.length)
                .sha256Hash(hash)
                .uploadedBy(requester)
                .changeNote(changeNote)
                .build();

        versionRepository.save(version);

        log.info("New version v{} uploaded for document {} by {}", nextVersion, documentId, requester.getEmail());
        return DocumentResponse.from(doc);
    }

    // ── Read ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId, User requester) {
        Document doc = getOwnedDocument(documentId, requester);
        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DocumentListResponse> listDocuments(
            User owner, DocumentStatus status, String search, int page, int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Document> result;

        if (search != null && !search.isBlank()) {
            result = documentRepository.searchByOwnerAndTitle(owner.getId(), search.trim(), pageable);
        } else if (status != null) {
            result = documentRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(owner.getId(), status, pageable);
        } else {
            result = documentRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId(), pageable);
        }

        return new PagedResponse<>(
                result.getContent().stream().map(DocumentListResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> getVersionHistory(UUID documentId, User requester) {
        getOwnedDocument(documentId, requester); // ownership check
        return versionRepository
                .findByDocumentIdOrderByVersionNumberDesc(documentId)
                .stream()
                .map(VersionResponse::from)
                .toList();
    }

    // ── Update ─────────────────────────────────────────────────

    @Transactional
    public DocumentResponse updateDocument(UUID documentId, UpdateDocumentRequest request, User requester) {
        Document doc = getOwnedDocument(documentId, requester);

        if (!doc.getStatus().isMutable()) {
            throw new BadRequestException("Cannot edit metadata after document has been sent for signing");
        }

        doc.setTitle(request.title().trim());
        if (request.description() != null) doc.setDescription(request.description());
        if (request.tags() != null)        doc.setTags(request.tags().toArray(String[]::new));

        return DocumentResponse.from(documentRepository.save(doc));
    }

    // ── FSM transition ─────────────────────────────────────────

    /**
     * Validates and applies a status transition via the FSM guard.
     * Downstream services (SigningService, Scheduler) call this.
     */
    @Transactional
    public DocumentResponse transitionStatus(UUID documentId, DocumentStatus target, User requester) {
        Document doc = getOwnedDocument(documentId, requester);
        DocumentFsm.transition(doc.getStatus(), target);

        doc.setStatus(target);
        if (target == DocumentStatus.COMPLETED) {
            doc.setCompletedAt(Instant.now());
        }

        documentRepository.save(doc);
        log.info("Document {} transitioned: {} → {}", documentId, doc.getStatus(), target);
        return DocumentResponse.from(doc);
    }

    // ── Delete ─────────────────────────────────────────────────

    @Transactional
    public void deleteDocument(UUID documentId, User requester) {
        Document doc = getOwnedDocument(documentId, requester);

        if (!doc.getStatus().isMutable()) {
            throw new BadRequestException(
                "Cannot delete a document in status: " + doc.getStatus() +
                ". Cancel it first."
            );
        }

        // Clean up all version files from storage
        versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId)
                .forEach(v -> storageService.delete(v.getS3Key()));

        documentRepository.delete(doc);
        log.info("Document {} deleted by {}", documentId, requester.getEmail());
    }

    // ── Download ───────────────────────────────────────────────

    /**
     * Returns raw bytes of the current document version.
     * For completed documents, returns the sealed signed PDF.
     */
    @Transactional(readOnly = true)
    public byte[] downloadDocument(UUID documentId, User requester) {
        Document doc = getOwnedDocument(documentId, requester);

        String key = (doc.getStatus() == DocumentStatus.COMPLETED && doc.getSignedS3Key() != null)
                ? doc.getSignedS3Key()
                : doc.getS3Key();

        return storageService.download(key);
    }

    /**
     * Generates a short-lived presigned URL (15 minutes) for direct browser download.
     */
    @Transactional(readOnly = true)
    public String generateDownloadUrl(UUID documentId, User requester) {
        Document doc = getOwnedDocument(documentId, requester);
        String key = (doc.getStatus() == DocumentStatus.COMPLETED && doc.getSignedS3Key() != null)
                ? doc.getSignedS3Key() : doc.getS3Key();
        return storageService.generatePresignedUrl(key, 15);
    }

    // ── Integrity verification ─────────────────────────────────

    /**
     * Re-downloads the document from storage and computes its SHA-256.
     * Compares against the stored fingerprint to detect tampering.
     */
    @Transactional(readOnly = true)
    public VerifyIntegrityResponse verifyIntegrity(UUID documentId, User requester) {
        Document doc   = getOwnedDocument(documentId, requester);
        byte[]   bytes = storageService.download(doc.getS3Key());
        String   computed = cryptoUtil.sha256Hex(bytes);
        boolean  intact   = computed.equalsIgnoreCase(doc.getSha256Hash());

        return new VerifyIntegrityResponse(
                intact,
                doc.getSha256Hash(),
                computed,
                intact ? "Document integrity verified — file has not been tampered with"
                       : "⚠ INTEGRITY FAILURE — stored hash does not match current file"
        );
    }

    // ── Dashboard stats ────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats(User owner) {
        List<Object[]> rows = documentRepository.countByStatusForOwner(owner.getId());
        Map<DocumentStatus, Long> counts = new EnumMap<>(DocumentStatus.class);
        rows.forEach(r -> counts.put((DocumentStatus) r[0], (Long) r[1]));

        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        return new DashboardStats(
                total,
                counts.getOrDefault(DocumentStatus.DRAFT,            0L),
                counts.getOrDefault(DocumentStatus.SENT,             0L),
                counts.getOrDefault(DocumentStatus.PARTIALLY_SIGNED, 0L),
                counts.getOrDefault(DocumentStatus.COMPLETED,        0L),
                counts.getOrDefault(DocumentStatus.EXPIRED,          0L),
                counts.getOrDefault(DocumentStatus.CANCELLED,        0L)
        );
    }

    // ── Scheduled expiry ───────────────────────────────────────

    /**
     * Runs every 15 minutes. Finds documents past their expiry date
     * and transitions them to EXPIRED via the FSM.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    public void expireDocuments() {
        List<Document> expired = documentRepository.findExpiredDocuments(Instant.now());
        if (expired.isEmpty()) return;

        log.info("Expiry sweep: found {} document(s) to expire", expired.size());
        for (Document doc : expired) {
            if (DocumentFsm.canTransition(doc.getStatus(), DocumentStatus.EXPIRED)) {
                doc.setStatus(DocumentStatus.EXPIRED);
                documentRepository.save(doc);
                log.info("Document {} expired (was {})", doc.getId(), doc.getStatus());
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────

    private Document getOwnedDocument(UUID documentId, User requester) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        // Admins can access any document; owners only their own
        if (requester.getRole() != com.labmentix.docsign.auth.entity.User.Role.ADMIN
                && !doc.getOwner().getId().equals(requester.getId())) {
            throw new ForbiddenException("You do not have access to this document");
        }
        return doc;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File exceeds the 50 MB size limit");
        }
        String mime = sanitizeMimeType(file.getContentType());
        if (!ALLOWED_MIME_TYPES.contains(mime)) {
            throw new BadRequestException(
                "Unsupported file type: " + mime + ". Allowed: PDF, DOCX"
            );
        }
    }

    private String sanitizeMimeType(String contentType) {
        if (contentType == null) return "application/octet-stream";
        // Strip charset and boundary params
        return contentType.split(";")[0].trim().toLowerCase();
    }
}