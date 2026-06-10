package com.labmentix.docsign.signing.controller;

import com.labmentix.docsign.signing.dto.SigningDto.*;
import com.labmentix.docsign.signing.service.SigningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Public signing endpoints — no JWT required.
 * Security is provided entirely by the one-use, time-limited signing token.
 *
 * Base: /api/signing/public
 * Permitted in SecurityConfig via: .requestMatchers("/signing/public/**").permitAll()
 */
@RestController
@RequestMapping("/signing/public")
@RequiredArgsConstructor
@Slf4j
public class PublicSigningController {

    private final SigningService signingService;

    /**
     * GET /api/signing/public/{token}
     *
     * Resolves a signing token into the signing page context:
     * - Document title, signer name/email
     * - Assigned signature fields with coordinates
     * - Presigned PDF URL for PDF.js rendering
     * - Marks signer as VIEWED on first access
     */
    @GetMapping("/{token}")
    public ResponseEntity<PublicSigningContext> getSigningContext(
            @PathVariable String token,
            HttpServletRequest request
    ) {
        PublicSigningContext context = signingService.resolveSigningToken(token, request);
        return ResponseEntity.ok(context);
    }

    /**
     * POST /api/signing/public/{token}/sign
     *
     * Submits a signature:
     * - Validates token, encrypts image (AES-256/GCM), persists
     * - Marks signer SIGNED
     * - If last signer: seals PDF and marks document COMPLETED
     * - Returns whether document is now fully completed
     */
    @PostMapping("/{token}/sign")
    public ResponseEntity<SubmitSignatureResponse> submitSignature(
            @PathVariable String token,
            @Valid @RequestBody SubmitSignatureRequest request,
            HttpServletRequest httpRequest
    ) throws IOException {
        SubmitSignatureResponse response = signingService.submitSignature(token, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/signing/public/{token}/decline
     *
     * Allows a signer to actively decline to sign.
     */
    @PostMapping("/{token}/decline")
    public ResponseEntity<DeclineResponse> declineToSign(
            @PathVariable String token,
            @RequestBody(required = false) DeclineRequest request,
            HttpServletRequest httpRequest
    ) {
        signingService.declineToSign(
                token,
                request != null ? request : new DeclineRequest(null),
                httpRequest
        );
        return ResponseEntity.ok(new DeclineResponse("Your decision to decline has been recorded"));
    }

    record DeclineResponse(String message) {}
}