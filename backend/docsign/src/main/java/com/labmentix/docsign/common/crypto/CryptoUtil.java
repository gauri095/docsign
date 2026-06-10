package com.labmentix.docsign.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Cryptographic utilities for document integrity and audit chain.
 */
@Component
@Slf4j
public class CryptoUtil {

    private static final String SHA_256 = "SHA-256";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int BUFFER_SIZE = 8192;

    // Fast hex conversion table
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    // ─────────────────────────────────────────────────────────────
    // SHA-256 (byte array)
    // ─────────────────────────────────────────────────────────────

    public String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SHA-256 (streaming)
    // ─────────────────────────────────────────────────────────────

    public String sha256Hex(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return bytesToHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SHA verification (SAFE VERSION)
    // ─────────────────────────────────────────────────────────────

    public boolean verifySha256(byte[] data, String expectedHex) {
        String computed = sha256Hex(data);

        if (expectedHex == null) {
            return false;
        }

        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                expectedHex.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ─────────────────────────────────────────────────────────────
    // HMAC-SHA256 (audit chain integrity)
    // ─────────────────────────────────────────────────────────────

    public String hmacSha256Hex(String data, String secret) {
        try {
            Objects.requireNonNull(secret, "HMAC secret cannot be null");

            Mac mac = Mac.getInstance(HMAC_SHA256);

            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );

            mac.init(keySpec);

            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(raw);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Audit HMAC input builder
    // ─────────────────────────────────────────────────────────────

    public String buildAuditHmacInput(
            String eventType,
            String actorEmail,
            String documentId,
            String isoTimestamp
    ) {
        return String.join("|", eventType, actorEmail, documentId, isoTimestamp);
    }

    // ─────────────────────────────────────────────────────────────
    // Fast hex encoder (optimized)
    // ─────────────────────────────────────────────────────────────

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hexChars);
    }
}