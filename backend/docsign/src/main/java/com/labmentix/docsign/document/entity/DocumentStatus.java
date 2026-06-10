package com.labmentix.docsign.document.entity;

/**
 * Document lifecycle states.
 *
 * FSM transitions:
 *
 *   DRAFT ──► SENT ──► PARTIALLY_SIGNED ──► COMPLETED
 *     │         │               │
 *     └─────────┴───────────────┴──► CANCELLED
 *                                         │
 *               SENT / PARTIALLY_SIGNED ──► EXPIRED  (by scheduler)
 */
public enum DocumentStatus {

    /** Document uploaded, not yet sent to any signers. */
    DRAFT,

    /** Signing links sent; awaiting first signature. */
    SENT,

    /** At least one signer has signed; others still pending. */
    PARTIALLY_SIGNED,

    /** All required signers have signed; PDF sealed. */
    COMPLETED,

    /** Auto-transitioned by scheduler when expires_at is passed. */
    EXPIRED,

    /** Manually cancelled by the document owner. */
    CANCELLED;

    /** Returns true if this status allows the document to be modified. */
    public boolean isMutable() {
        return this == DRAFT;
    }

    /** Returns true if the document is still in an active signing workflow. */
    public boolean isActive() {
        return this == SENT || this == PARTIALLY_SIGNED;
    }

    /** Returns true if this is a terminal state (no further transitions). */
    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == CANCELLED;
    }
}