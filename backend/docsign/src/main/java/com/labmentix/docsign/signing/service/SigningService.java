package com.labmentix.docsign.signing.service;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.common.crypto.AesEncryptionService;
import com.labmentix.docsign.common.exception.BadRequestException;
import com.labmentix.docsign.common.exception.ForbiddenException;
import com.labmentix.docsign.common.exception.ResourceNotFoundException;
import com.labmentix.docsign.common.storage.StorageService;
import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.document.entity.DocumentStatus;
import com.labmentix.docsign.document.fsm.DocumentFsm;
import com.labmentix.docsign.document.repository.DocumentRepository;
import com.labmentix.docsign.document.service.PdfService;
import com.labmentix.docsign.signing.dto.SigningDto.*;
import com.labmentix.docsign.signing.entity.*;
import com.labmentix.docsign.signing.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SigningService {

    private final DocumentRepository      documentRepository;
    private final SignatureFieldRepository fieldRepository;
    private final SignerRepository         signerRepository;
    private final SignatureRepository      signatureRepository;
    private final AesEncryptionService     aesService;
    private final StorageService           storageService;
    private final PdfService               pdfService;

    @Value("${app.signing.token-expiry-hours}")
    private int tokenExpiryHours;

    @Value("${app.signing.base-url}")
    private String baseUrl;

    @Value("${app.jwt.secret}")
    private String jwtSecret;   // reused as HMAC key for token integrity

    // ═══════════════════════════════════════════════════════════
    // FIELD PLACEMENT (owner-facing)
    // ═══════════════════════════════════════════════════════════

    /**
     * Replaces all signature fields on a document with a new set.
     * Only allowed while document is DRAFT.
     */
    @Transactional
    public List<FieldResponse> placeFields(
            UUID documentId,
            BulkFieldRequest request,
            User owner
    ) {
        Document doc = requireOwnedDraft(documentId, owner);

        // Wipe existing fields and replace atomically
        fieldRepository.deleteAllByDocumentId(documentId);

        List<SignatureField> saved = request.fields().stream().map(req -> {
            SignatureField f = SignatureField.builder()
                    .document(doc)
                    .assignedEmail(req.assignedEmail().toLowerCase().trim())
                    .pageNumber(req.pageNumber())
                    .xPosition(req.xPosition())
                    .yPosition(req.yPosition())
                    .width(req.width()   != null ? req.width()   : new BigDecimal("0.2500"))
                    .height(req.height() != null ? req.height()  : new BigDecimal("0.0800"))
                    .fieldType(req.fieldType() != null ? req.fieldType().toUpperCase() : "SIGNATURE")
                    .label(req.label())
                    .required(req.required())
                    .build();
            return fieldRepository.save(f);
        }).collect(Collectors.toList());

        log.info("Placed {} signature fields on document {}", saved.size(), documentId);
        return saved.stream().map(FieldResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FieldResponse> getFields(UUID documentId, User owner) {
        requireOwnerAccess(documentId, owner);
        return fieldRepository.findByDocumentIdOrderByPageNumberAscCreatedAtAsc(documentId)
                .stream().map(FieldResponse::from).toList();
    }

    // ═══════════════════════════════════════════════════════════
    // SEND FOR SIGNING — token generation
    // ═══════════════════════════════════════════════════════════

    /**
     * Transitions document DRAFT → SENT, creates Signer rows with
     * one-use cryptographic tokens, and returns signing link details.
     * The caller (notification service) uses these links to send emails.
     */
    @Transactional
    public SendForSigningResponse sendForSigning(
            UUID documentId,
            SendForSigningRequest request,
            User owner
    ) {
        Document doc = requireOwnedDraft(documentId, owner);

        if (fieldRepository.countByDocumentId(documentId) == 0) {
            throw new BadRequestException(
                "Place at least one signature field before sending for signing"
            );
        }

        if (request.signers().isEmpty()) {
            throw new BadRequestException("At least one signer is required");
        }

        // Detect duplicate emails in the request
        long uniqueEmails = request.signers().stream()
                .map(s -> s.email().toLowerCase())
                .distinct().count();
        if (uniqueEmails != request.signers().size()) {
            throw new BadRequestException("Duplicate signer emails are not allowed");
        }

        int expiryHrs = (request.expiryHours() != null && request.expiryHours() > 0)
                ? request.expiryHours() : tokenExpiryHours;

        Instant tokenExpiry = Instant.now().plus(expiryHrs, ChronoUnit.HOURS);

        List<Signer> signers = request.signers().stream().map(req -> {
            String email = req.email().toLowerCase().trim();
            String token = generateSecureToken(documentId, email);

            return signerRepository.save(Signer.builder()
                    .document(doc)
                    .name(req.name().trim())
                    .email(email)
                    .signingToken(token)
                    .tokenExpiresAt(tokenExpiry)
                    .status(SignerStatus.PENDING)
                    .signingOrder(req.signingOrder() != null ? req.signingOrder() : 1)
                    .build());
        }).toList();

        // Transition document to SENT via FSM
        DocumentFsm.transition(doc.getStatus(), DocumentStatus.SENT);
        doc.setStatus(DocumentStatus.SENT);
        doc.setExpiresAt(tokenExpiry);
        documentRepository.save(doc);

        log.info("Document {} sent for signing to {} signers", documentId, signers.size());

        return new SendForSigningResponse(
                doc.getId(),
                doc.getTitle(),
                signers.size(),
                signers.stream().map(Signer::getEmail).toList(),
                tokenExpiry,
                "Signing links generated. Send them via email."
        );
    }

    @Transactional(readOnly = true)
    public List<SignerResponse> getSigners(UUID documentId, User owner) {
        requireOwnerAccess(documentId, owner);
        return signerRepository.findByDocumentIdOrderBySigningOrderAsc(documentId)
                .stream().map(SignerResponse::from).toList();
    }

    // ═══════════════════════════════════════════════════════════
    // PUBLIC SIGNING PAGE — no authentication
    // ═══════════════════════════════════════════════════════════

    /**
     * Resolves a signing token into the context needed to render the signing page.
     * Marks the signer as VIEWED on first access.
     * Public — no JWT required.
     */
    @Transactional
    public PublicSigningContext resolveSigningToken(String token, HttpServletRequest httpRequest) {
        Signer signer = requireValidSigner(token);
        Document doc  = signer.getDocument();

        // Mark as VIEWED on first open
        if (signer.getStatus() == SignerStatus.PENDING) {
            signer.setStatus(SignerStatus.VIEWED);
            signer.setViewedAt(Instant.now());
            signer.setIpAddress(extractIp(httpRequest));
            signer.setUserAgent(httpRequest.getHeader("User-Agent"));
            signerRepository.save(signer);
            log.info("Signer {} viewed document {}", signer.getEmail(), doc.getId());
        }

        // Fields assigned to this signer
        List<FieldResponse> fields = fieldRepository
                .findByDocumentIdAndAssignedEmail(doc.getId(), signer.getEmail())
                .stream().map(FieldResponse::from).toList();

        // 15-minute presigned URL for PDF.js to load the document
        String pdfUrl = storageService.generatePresignedUrl(doc.getS3Key(), 15);

        return new PublicSigningContext(
                doc.getId(),
                doc.getTitle(),
                signer.getName(),
                signer.getEmail(),
                signer.getStatus(),
                fields,
                doc.getPageCount() != null ? doc.getPageCount() : 1,
                pdfUrl
        );
    }

    // ═══════════════════════════════════════════════════════════
    // SUBMIT SIGNATURE — AES encryption + FSM progression
    // ═══════════════════════════════════════════════════════════

    /**
     * Core signing action — public endpoint, secured by token only.
     *
     * Steps:
     * 1. Validate token
     * 2. Strip data-URI prefix from base64 image
     * 3. AES-256/GCM encrypt the image
     * 4. Persist Signature row
     * 5. Mark Signer as SIGNED
     * 6. Check if all signers are done → if yes, trigger PDF sealing
     * 7. Return completion status
     */
    @Transactional
    public SubmitSignatureResponse submitSignature(
            String token,
            SubmitSignatureRequest request,
            HttpServletRequest httpRequest
    ) throws IOException {

        Signer   signer = requireValidSigner(token);
        Document doc    = signer.getDocument();

        // Validate the field belongs to this signer (if a fieldId was provided)
        SignatureField field = null;
        if (request.fieldId() != null) {
            field = fieldRepository.findById(request.fieldId())
                    .orElseThrow(() -> new ResourceNotFoundException("SignatureField", request.fieldId()));
            if (!field.getDocument().getId().equals(doc.getId())) {
                throw new BadRequestException("Field does not belong to this document");
            }
            if (!field.getAssignedEmail().equalsIgnoreCase(signer.getEmail())) {
                throw new ForbiddenException("This signature field is not assigned to you");
            }
        }

        // Strip data-URI prefix if present (data:image/png;base64,...)
        String rawBase64 = stripDataUriPrefix(request.signatureImageBase64());
        validateBase64Image(rawBase64);

        // AES-256/GCM encrypt the image before persisting
        String encryptedImage = aesService.encrypt(rawBase64);

        // Persist Signature
        Signature signature = Signature.builder()
                .signer(signer)
                .document(doc)
                .field(field)
                .signatureType(request.signatureType())
                .imageDataEnc(encryptedImage)
                .pageNumber(request.pageNumber())
                .xPosition(request.xPosition())
                .yPosition(request.yPosition())
                .width(request.width())
                .height(request.height())
                .ipAddress(extractIp(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        signatureRepository.save(signature);

        // Mark signer SIGNED
        signer.setStatus(SignerStatus.SIGNED);
        signer.setSignedAt(Instant.now());
        signerRepository.save(signer);

        log.info("Signer {} signed document {} (type={})",
                signer.getEmail(), doc.getId(), request.signatureType());

        // ── Check if document is now fully signed ──────────────
        boolean allDone = signerRepository.areAllSignersDone(doc.getId());

        if (allDone) {
            sealDocument(doc);
            return new SubmitSignatureResponse(
                    "Document fully signed and sealed. A copy will be sent to all parties.",
                    true,
                    doc.getId()
            );
        } else {
            // Partial — advance FSM if still SENT
            if (doc.getStatus() == DocumentStatus.SENT) {
                DocumentFsm.transition(doc.getStatus(), DocumentStatus.PARTIALLY_SIGNED);
                doc.setStatus(DocumentStatus.PARTIALLY_SIGNED);
                documentRepository.save(doc);
            }
            long remaining = signerRepository.countByDocumentIdAndStatus(doc.getId(), SignerStatus.PENDING)
                           + signerRepository.countByDocumentIdAndStatus(doc.getId(), SignerStatus.VIEWED);
            return new SubmitSignatureResponse(
                    "Signature submitted. Waiting for " + remaining + " more signer(s).",
                    false,
                    doc.getId()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DECLINE
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void declineToSign(String token, DeclineRequest request, HttpServletRequest httpRequest) {
        Signer signer = requireValidSigner(token);

        signer.setStatus(SignerStatus.DECLINED);
        signer.setIpAddress(extractIp(httpRequest));
        signer.setUserAgent(httpRequest.getHeader("User-Agent"));
        signerRepository.save(signer);

        log.info("Signer {} declined to sign document {}", signer.getEmail(), signer.getDocument().getId());
    }

    // ═══════════════════════════════════════════════════════════
    // PDF SEALING — decrypt signatures, embed, store final copy
    // ═══════════════════════════════════════════════════════════

    /**
     * Decrypts all signature images, embeds them into the PDF via iText,
     * stores the sealed copy in MinIO, and marks the document COMPLETED.
     */
    private void sealDocument(Document doc) throws IOException {
        log.info("Sealing document {} — all signers have signed", doc.getId());

        // Fetch all signatures with signer info
        List<Signature> signatures = signatureRepository.findByDocumentIdWithSigner(doc.getId());

        // Decrypt images and build PdfService placement list
        List<PdfService.SignaturePlacement> placements = signatures.stream().map(sig -> {
            String decryptedBase64 = aesService.decrypt(sig.getImageDataEnc());
            return new PdfService.SignaturePlacement(
                    sig.getPageNumber(),
                    decryptedBase64,
                    sig.getXPosition().floatValue(),
                    sig.getYPosition().floatValue(),
                    sig.getWidth().floatValue(),
                    sig.getHeight().floatValue(),
                    sig.getSigner().getEmail()
            );
        }).toList();

        // Download original PDF from MinIO
        byte[] originalPdf = storageService.download(doc.getS3Key());

        // Embed signatures and stamp footer
        byte[] sealedPdf = pdfService.embedSignaturesAndSeal(
                originalPdf,
                placements,
                doc.getId().toString(),
                doc.getSha256Hash()
        );

        // Upload sealed PDF
        String signedKey = StorageService.buildSignedKey(doc.getId());
        storageService.upload(signedKey, sealedPdf, "application/pdf");

        // Transition FSM: PARTIALLY_SIGNED / SENT → COMPLETED
        DocumentStatus current = doc.getStatus();
        if (DocumentFsm.canTransition(current, DocumentStatus.COMPLETED)) {
            doc.setStatus(DocumentStatus.COMPLETED);
        }
        doc.setSignedS3Key(signedKey);
        doc.setCompletedAt(Instant.now());
        documentRepository.save(doc);

        log.info("Document {} sealed and stored at {}", doc.getId(), signedKey);
    }

    // ═══════════════════════════════════════════════════════════
    // SIGNING LINK HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Builds the full signing URL for a signer.
     * Example: http://localhost:5173/sign/abc123-token
     */
    public String buildSigningLink(String token) {
        return baseUrl + "/sign/" + token;
    }

    /**
     * Generates a cryptographically secure one-use token.
     * Format: UUID + UUID + HMAC(documentId|email|timestamp) truncated to 16 chars
     * Result is URL-safe and collision-resistant.
     */
    private String generateSecureToken(UUID documentId, String email) {
        String part1    = UUID.randomUUID().toString().replace("-", "");
        String part2    = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hmacInput = documentId + "|" + email + "|" + timestamp;

        // Use the JWT secret as HMAC key for token integrity
        // (a signing-specific secret would be cleaner — can be added in production)
        String hmac = computeSimpleHmac(hmacInput, jwtSecret);

        return part1 + part2 + hmac.substring(0, 16);
    }

    private String computeSimpleHmac(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec key =
                    new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(key);
            byte[] raw = mac.doFinal(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Token HMAC failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // GUARDS
    // ═══════════════════════════════════════════════════════════

    private Document requireOwnedDraft(UUID documentId, User owner) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        if (!doc.getOwner().getId().equals(owner.getId())
                && owner.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("You do not have access to this document");
        }
        if (!doc.getStatus().isMutable()) {
            throw new BadRequestException(
                "Document must be in DRAFT status. Current status: " + doc.getStatus()
            );
        }
        return doc;
    }

    private void requireOwnerAccess(UUID documentId, User owner) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        if (!doc.getOwner().getId().equals(owner.getId())
                && owner.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("You do not have access to this document");
        }
    }

    private Signer requireValidSigner(String token) {
        Signer signer = signerRepository.findBySigningToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or unknown signing token"));

        if (signer.isTokenExpired()) {
            throw new BadRequestException("This signing link has expired");
        }
        if (signer.getStatus() == SignerStatus.SIGNED) {
            throw new BadRequestException("You have already signed this document");
        }
        if (signer.getStatus() == SignerStatus.DECLINED) {
            throw new BadRequestException("You have already declined to sign this document");
        }
        return signer;
    }

    // ═══════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════

    private String stripDataUriPrefix(String input) {
        if (input == null) return null;
        // Strip "data:image/png;base64," prefix if present
        int commaIdx = input.indexOf(',');
        return (commaIdx >= 0) ? input.substring(commaIdx + 1).trim() : input.trim();
    }

    private void validateBase64Image(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new BadRequestException("Signature image is empty");
        }
        if (base64.length() < 100) {
            throw new BadRequestException("Signature image is too small — ensure it is a valid PNG");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            // Check PNG magic bytes: 0x89 0x50 0x4E 0x47
            if (decoded.length < 4 ||
                decoded[0] != (byte) 0x89 || decoded[1] != 0x50 ||
                decoded[2] != 0x4E      || decoded[3] != 0x47) {
                throw new BadRequestException("Signature image must be a PNG file");
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Signature image is not valid Base64");
        }
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}