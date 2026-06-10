package com.labmentix.docsign.signing.entity;

/**
 * Lifecycle states for an individual signer on a document.
 * Maps to the PostgreSQL ENUM signer_status.
 *
 * Transitions:
 *   PENDING → VIEWED → SIGNED
 *   PENDING → DECLINED
 *   VIEWED  → DECLINED
 */
public enum SignerStatus {

    /** Invited but has not opened the signing link yet. */
    PENDING,

    /** Opened the signing page — token accessed. */
    VIEWED,

    /** Submitted their signature. */
    SIGNED,

    /** Actively declined to sign. */
    DECLINED;

    public boolean isTerminal() {
        return this == SIGNED || this == DECLINED;
    }
}