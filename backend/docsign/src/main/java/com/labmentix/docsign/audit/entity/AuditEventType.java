package com.labmentix.docsign.audit.entity;

/**
 * All auditable events in the DocSign platform.
 *
 * Must stay in sync with the PostgreSQL ENUM audit_event_type (V1__init_schema.sql).
 * To add a new event type: add here AND in a new Flyway migration that runs
 * ALTER TYPE audit_event_type ADD VALUE '...'.
 */
public enum AuditEventType {

    // ── Document lifecycle ─────────────────────────────────────
    DOCUMENT_UPLOADED,
    DOCUMENT_SENT,
    DOCUMENT_VIEWED,
    DOCUMENT_SIGNED,
    DOCUMENT_COMPLETED,
    DOCUMENT_CANCELLED,
    DOCUMENT_EXPIRED,
    DOCUMENT_DOWNLOADED,

    // ── User events ────────────────────────────────────────────
    USER_REGISTERED,
    USER_LOGIN,
    USER_LOGOUT,

    // ── Signing link events ────────────────────────────────────
    SIGNING_LINK_ACCESSED,
    SIGNING_LINK_EXPIRED;

    /** Returns a stable string suitable for HMAC input construction. */
    public String canonical() {
        return this.name();
    }
}