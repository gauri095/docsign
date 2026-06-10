package com.labmentix.docsign.document.fsm;

import com.labmentix.docsign.common.exception.BadRequestException;
import com.labmentix.docsign.document.entity.DocumentStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Finite State Machine guard for document lifecycle transitions.
 *
 * <pre>
 * DRAFT ──► SENT
 * DRAFT ──► CANCELLED
 * SENT  ──► PARTIALLY_SIGNED
 * SENT  ──► COMPLETED          (skip partial when only one signer)
 * SENT  ──► CANCELLED
 * SENT  ──► EXPIRED
 * PARTIALLY_SIGNED ──► COMPLETED
 * PARTIALLY_SIGNED ──► CANCELLED
 * PARTIALLY_SIGNED ──► EXPIRED
 * </pre>
 */
public final class DocumentFsm {

    private DocumentFsm() {}

    private static final Map<DocumentStatus, Set<DocumentStatus>> TRANSITIONS =
            new EnumMap<>(DocumentStatus.class);

    static {
        TRANSITIONS.put(DocumentStatus.DRAFT, EnumSet.of(
                DocumentStatus.SENT,
                DocumentStatus.CANCELLED
        ));
        TRANSITIONS.put(DocumentStatus.SENT, EnumSet.of(
                DocumentStatus.PARTIALLY_SIGNED,
                DocumentStatus.COMPLETED,
                DocumentStatus.CANCELLED,
                DocumentStatus.EXPIRED
        ));
        TRANSITIONS.put(DocumentStatus.PARTIALLY_SIGNED, EnumSet.of(
                DocumentStatus.COMPLETED,
                DocumentStatus.CANCELLED,
                DocumentStatus.EXPIRED
        ));
        // Terminal states — no outgoing transitions
        TRANSITIONS.put(DocumentStatus.COMPLETED,  EnumSet.noneOf(DocumentStatus.class));
        TRANSITIONS.put(DocumentStatus.EXPIRED,    EnumSet.noneOf(DocumentStatus.class));
        TRANSITIONS.put(DocumentStatus.CANCELLED,  EnumSet.noneOf(DocumentStatus.class));
    }

    /**
     * Validates and performs the transition. Throws if illegal.
     *
     * @param current  current document status
     * @param target   desired next status
     * @throws BadRequestException if the transition is not allowed
     */
    public static void transition(DocumentStatus current, DocumentStatus target) {
        Set<DocumentStatus> allowed = TRANSITIONS.getOrDefault(current, EnumSet.noneOf(DocumentStatus.class));
        if (!allowed.contains(target)) {
            throw new BadRequestException(
                    "Invalid document status transition: %s → %s. Allowed from %s: %s"
                            .formatted(current, target, current, allowed)
            );
        }
    }

    /**
     * Returns true if the transition is valid without throwing.
     */
    public static boolean canTransition(DocumentStatus current, DocumentStatus target) {
        return TRANSITIONS.getOrDefault(current, EnumSet.noneOf(DocumentStatus.class))
                          .contains(target);
    }

    /**
     * Returns all valid next states from the given status.
     */
    public static Set<DocumentStatus> allowedTransitions(DocumentStatus from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(DocumentStatus.class));
    }
}