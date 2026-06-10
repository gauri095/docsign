package com.labmentix.docsign.signing.controller;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.signing.dto.SigningDto.*;
import com.labmentix.docsign.signing.service.SigningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Owner-facing signing management API.
 * All endpoints require JWT authentication.
 *
 * Base: /api/documents/{documentId}/signing
 */
@RestController
@RequestMapping("/documents/{documentId}/signing")
@RequiredArgsConstructor
public class SigningController {

    private final SigningService signingService;

    // ── Signature field placement ──────────────────────────────

    /**
     * PUT /api/documents/{documentId}/signing/fields
     * Replaces all signature fields with a new set (idempotent).
     */
    @PutMapping("/fields")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<List<FieldResponse>> placeFields(
            @PathVariable UUID documentId,
            @Valid @RequestBody BulkFieldRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(signingService.placeFields(documentId, request, currentUser));
    }

    /**
     * GET /api/documents/{documentId}/signing/fields
     * Retrieves all placed signature fields.
     */
    @GetMapping("/fields")
    public ResponseEntity<List<FieldResponse>> getFields(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(signingService.getFields(documentId, currentUser));
    }

    // ── Send for signing ───────────────────────────────────────

    /**
     * POST /api/documents/{documentId}/signing/send
     * Transitions document to SENT and generates signing tokens.
     */
    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<SendForSigningResponse> sendForSigning(
            @PathVariable UUID documentId,
            @Valid @RequestBody SendForSigningRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        SendForSigningResponse response = signingService.sendForSigning(documentId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Signers overview ───────────────────────────────────────

    /**
     * GET /api/documents/{documentId}/signing/signers
     * Lists all signers and their current status.
     */
    @GetMapping("/signers")
    public ResponseEntity<List<SignerResponse>> getSigners(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(signingService.getSigners(documentId, currentUser));
    }

    /**
     * GET /api/documents/{documentId}/signing/signers/{signerId}/link
     * Returns the signing link for a specific signer (for resending).
     */
    @GetMapping("/signers/{signerId}/link")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<SigningLinkResponse> getSigningLink(
            @PathVariable UUID documentId,
            @PathVariable UUID signerId,
            @AuthenticationPrincipal User currentUser
    ) {
        // Validate ownership first
        signingService.getSigners(documentId, currentUser);  // throws if not owner

        com.labmentix.docsign.signing.repository.SignerRepository signerRepo =
                getSignerRepository();
        com.labmentix.docsign.signing.entity.Signer signer = signerRepo.findById(signerId)
                .orElseThrow(() -> new com.labmentix.docsign.common.exception.ResourceNotFoundException("Signer", signerId));

        String link = signingService.buildSigningLink(signer.getSigningToken());
        return ResponseEntity.ok(new SigningLinkResponse(
                signer.getId(), signer.getEmail(), link, signer.getTokenExpiresAt()
        ));
    }

    // ── Local helpers ──────────────────────────────────────────

    // Injected via constructor for the link endpoint
    private final com.labmentix.docsign.signing.repository.SignerRepository signerRepository;

    private com.labmentix.docsign.signing.repository.SignerRepository getSignerRepository() {
        return signerRepository;
    }

    record SigningLinkResponse(
            UUID   signerId,
            String email,
            String link,
            java.time.Instant expiresAt
    ) {}
}