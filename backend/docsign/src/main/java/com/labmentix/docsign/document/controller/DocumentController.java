package com.labmentix.docsign.document.controller;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.document.dto.DocumentDto.*;
import com.labmentix.docsign.document.entity.DocumentStatus;
import com.labmentix.docsign.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    // ── Upload initial document ────────────────────────────────

    /**
     * POST /api/documents/upload
     * Multipart: file (required), title (required), description, tags
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file")                         MultipartFile file,
            @RequestPart("title")                        String title,
            @RequestPart(value = "description",   required = false) String description,
            @RequestPart(value = "tags",          required = false) List<String> tags,
            @AuthenticationPrincipal User currentUser
    ) throws IOException {
        DocumentResponse response = documentService.uploadDocument(file, title, description, tags, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Upload new version ─────────────────────────────────────

    /**
     * POST /api/documents/{id}/versions
     */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<DocumentResponse> uploadVersion(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "changeNote", required = false) String changeNote,
            @AuthenticationPrincipal User currentUser
    ) throws IOException {
        DocumentResponse response = documentService.uploadNewVersion(id, file, changeNote, currentUser);
        return ResponseEntity.ok(response);
    }

    // ── List documents ─────────────────────────────────────────

    /**
     * GET /api/documents?status=DRAFT&search=contract&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<PagedResponse<DocumentListResponse>> list(
            @RequestParam(required = false)              DocumentStatus status,
            @RequestParam(required = false)              String search,
            @RequestParam(defaultValue = "0")            int page,
            @RequestParam(defaultValue = "20")           int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                documentService.listDocuments(currentUser, status, search, page, size)
        );
    }

    // ── Get single document ────────────────────────────────────

    /**
     * GET /api/documents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(documentService.getDocument(id, currentUser));
    }

    // ── Update metadata ────────────────────────────────────────

    /**
     * PATCH /api/documents/{id}
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(documentService.updateDocument(id, request, currentUser));
    }

    // ── Status transition ──────────────────────────────────────

    /**
     * POST /api/documents/{id}/status
     * Body: { "targetStatus": "CANCELLED" }
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<DocumentResponse> transition(
            @PathVariable UUID id,
            @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                documentService.transitionStatus(id, request.targetStatus(), currentUser)
        );
    }

    // ── Version history ────────────────────────────────────────

    /**
     * GET /api/documents/{id}/versions
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<VersionResponse>> versions(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(documentService.getVersionHistory(id, currentUser));
    }

    // ── Delete ─────────────────────────────────────────────────

    /**
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        documentService.deleteDocument(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // ── Download ───────────────────────────────────────────────

    /**
     * GET /api/documents/{id}/download
     * Streams the document bytes directly to the client.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        byte[] bytes = documentService.downloadDocument(id, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    /**
     * GET /api/documents/{id}/download-url
     * Returns a 15-minute presigned URL for direct browser download.
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        String url = documentService.generateDownloadUrl(id, currentUser);
        return ResponseEntity.ok(new DownloadUrlResponse(url, 15));
    }

    // ── Integrity verification ─────────────────────────────────

    /**
     * POST /api/documents/{id}/verify
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<VerifyIntegrityResponse> verify(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(documentService.verifyIntegrity(id, currentUser));
    }

    // ── Dashboard stats ────────────────────────────────────────

    /**
     * GET /api/dashboard/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<DashboardStats> stats(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(documentService.getDashboardStats(currentUser));
    }

    // ── Local response record ──────────────────────────────────

    record DownloadUrlResponse(String url, int expiryMinutes) {}
}