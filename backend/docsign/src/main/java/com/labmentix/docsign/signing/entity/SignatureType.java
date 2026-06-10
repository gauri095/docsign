package com.labmentix.docsign.signing.entity;

/**
 * How the signer produced their signature image.
 * Maps to the PostgreSQL ENUM signature_type.
 */
public enum SignatureType {
    /** Drawn freehand on the canvas. */
    DRAWN,

    /** Generated from typed text with a signature font. */
    TYPED,

    /** Uploaded as an image file. */
    UPLOADED
}