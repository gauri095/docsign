package com.labmentix.docsign.audit.aop;

import com.labmentix.docsign.audit.annotation.Auditable;
import com.labmentix.docsign.audit.entity.AuditEventType;
import com.labmentix.docsign.audit.service.AuditService;
import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.document.dto.DocumentDto.DocumentResponse;
import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.document.repository.DocumentRepository;
import com.labmentix.docsign.signing.dto.SigningDto.SendForSigningResponse;
import com.labmentix.docsign.signing.dto.SigningDto.SubmitSignatureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * AOP aspect that intercepts methods annotated with {@link Auditable}
 * and dispatches asynchronous audit log entries via {@link AuditService}.
 *
 * Extraction strategy:
 * - Scans all method arguments to find {@link User} (the actor)
 * - Looks for a {@link UUID} argument as a potential document ID
 * - Inspects the return value for document IDs in known response types
 * - Builds a context map from whatever it can find
 *
 * The aspect never throws — if extraction or dispatch fails, it logs
 * the error and lets the original method result pass through unchanged.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService       auditService;
    private final DocumentRepository documentRepository;

    @Around("@annotation(com.labmentix.docsign.audit.annotation.Auditable)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {

        Method         method    = ((MethodSignature) pjp.getSignature()).getMethod();
        Auditable      auditable = method.getAnnotation(Auditable.class);
        AuditEventType eventType = auditable.value();
        Object[]       args      = pjp.getArgs();

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            if (!auditable.onlyOnSuccess()) {
                // best-effort — never let audit failure mask the real exception
                try { dispatchAudit(eventType, auditable, args, null); }
                catch (Exception ignored) { log.debug("Audit dispatch suppressed on error path", ignored); }
            }
            throw t;   // always re-throw the original
        }

        // Successful execution — dispatch audit asynchronously, fully isolated
        try {
            dispatchAudit(eventType, auditable, args, result);
        } catch (Exception e) {
            // Audit MUST NEVER break a successful business operation
            log.error("AuditAspect dispatch failed for {} — business call succeeded: {}", eventType, e.getMessage(), e);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // DISPATCH
    // ═══════════════════════════════════════════════════════════

    private void dispatchAudit(
            AuditEventType eventType,
            Auditable      auditable,
            Object[]       args,
            Object         returnValue
    ) {
        try {
            User     actor    = extractUser(args);
            Document document = extractDocument(args, returnValue);
            Map<String, Object> detail = buildDetail(eventType, auditable, args, returnValue);

            auditService.recordAsync(eventType, actor, document, detail);

        } catch (Exception e) {
            // Audit aspect must NEVER break the application
            log.error("AuditAspect dispatch failed for {}: {}", eventType, e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ACTOR EXTRACTION
    // ═══════════════════════════════════════════════════════════

    /** Finds the first {@link User} argument. Null if not present (public endpoints). */
    private User extractUser(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof User u) return u;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT EXTRACTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Tries to resolve a {@link Document} from:
     * 1. A UUID argument (direct document ID parameter)
     * 2. A DocumentResponse return value (contains the document ID)
     * 3. A SendForSigningResponse (contains documentId field)
     */
    private Document extractDocument(Object[] args, Object returnValue) {
        // Try to find a UUID document ID from the return value first
        UUID docId = extractDocumentIdFromReturn(returnValue);

        // Fall back to first UUID argument
        if (docId == null) {
            docId = extractFirstUuid(args);
        }

        if (docId == null) return null;

        try {
            return documentRepository.findById(docId).orElse(null);
        } catch (Exception e) {
            log.debug("Could not load document {} for audit: {}", docId, e.getMessage());
            return null;
        }
    }

    private UUID extractDocumentIdFromReturn(Object ret) {
        if (ret instanceof DocumentResponse r)       return r.id();
        if (ret instanceof SendForSigningResponse r)  return r.documentId();
        if (ret instanceof SubmitSignatureResponse r) return r.documentId();
        return null;
    }

    private UUID extractFirstUuid(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID id) return id;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // DETAIL MAP CONSTRUCTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Builds a context map for the event_detail JSONB column.
     * Each event type populates slightly different fields.
     */
    private Map<String, Object> buildDetail(
            AuditEventType eventType,
            Auditable      auditable,
            Object[]       args,
            Object         returnValue
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();

        if (!auditable.description().isEmpty()) {
            detail.put("description", auditable.description());
        }

        switch (eventType) {

            case DOCUMENT_UPLOADED -> {
                if (returnValue instanceof DocumentResponse r) {
                    detail.put("documentId",   r.id().toString());
                    detail.put("fileName",      r.originalName());
                    detail.put("sizeBytes",     r.fileSizeBytes());
                    detail.put("mimeType",      r.mimeType());
                    detail.put("sha256",        r.sha256Hash());
                    detail.put("pageCount",     r.pageCount());
                }
            }

            case DOCUMENT_SENT -> {
                if (returnValue instanceof SendForSigningResponse r) {
                    detail.put("documentId",    r.documentId().toString());
                    detail.put("signerCount",   r.signerCount());
                    detail.put("signerEmails",  r.signerEmails());
                    detail.put("expiresAt",     r.expiresAt() != null ? r.expiresAt().toString() : null);
                }
            }

            case DOCUMENT_SIGNED -> {
                if (returnValue instanceof SubmitSignatureResponse r) {
                    detail.put("documentId",        r.documentId().toString());
                    detail.put("documentCompleted", r.documentCompleted());
                }
                // Extract signature type from args if present
                for (Object arg : args) {
                    if (arg instanceof com.labmentix.docsign.signing.dto.SigningDto.SubmitSignatureRequest req) {
                        detail.put("signatureType",  req.signatureType().name());
                        detail.put("pageNumber",     req.pageNumber());
                    }
                    if (arg instanceof String s && s.length() == 80) {
                        detail.put("signingToken", s.substring(0, 8) + "...");  // partial for privacy
                    }
                }
            }

            case DOCUMENT_DOWNLOADED -> {
                UUID docId = extractFirstUuid(args);
                if (docId != null) detail.put("documentId", docId.toString());
            }

            case DOCUMENT_CANCELLED, DOCUMENT_COMPLETED, DOCUMENT_EXPIRED -> {
                if (returnValue instanceof DocumentResponse r) {
                    detail.put("documentId", r.id().toString());
                    detail.put("status",     r.status().name());
                }
            }

            case USER_REGISTERED -> {
                if (returnValue instanceof com.labmentix.docsign.auth.dto.AuthDto.AuthResponse r) {
                    detail.put("userId", r.user().id().toString());
                    detail.put("email",  r.user().email());
                    detail.put("role",   r.user().role());
                }
            }

            case USER_LOGIN -> {
                if (returnValue instanceof com.labmentix.docsign.auth.dto.AuthDto.AuthResponse r) {
                    detail.put("userId", r.user().id().toString());
                    detail.put("email",  r.user().email());
                }
            }

            case USER_LOGOUT -> {
                for (Object arg : args) {
                    if (arg instanceof User u) {
                        detail.put("userId", u.getId().toString());
                        detail.put("email",  u.getEmail());
                        break;
                    }
                }
            }

            case SIGNING_LINK_ACCESSED -> {
                for (Object arg : args) {
                    if (arg instanceof String s && s.length() >= 32) {
                        detail.put("tokenPrefix", s.substring(0, 8) + "...");
                        break;
                    }
                }
            }

            default -> {
                // Generic fallback — just record the event type
                UUID docId = extractFirstUuid(args);
                if (docId != null) detail.put("documentId", docId.toString());
            }
        }

        return detail;
    }
}