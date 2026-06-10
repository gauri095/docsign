package com.labmentix.docsign.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256/GCM authenticated encryption for signature image data.
 *
 * Format of ciphertext stored in DB (all Base64-encoded):
 *   [ 12-byte IV ][ 16-byte GCM tag ][ ciphertext ]
 * The entire payload is Base64-encoded as a single string.
 *
 * GCM provides both confidentiality AND integrity — no separate HMAC needed
 * for the encrypted payload itself.
 */
@Service
@Slf4j
public class AesEncryptionService {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH    = 12;    // 96-bit IV — GCM standard
    private static final int    TAG_LENGTH   = 128;   // 128-bit authentication tag
    private static final int    KEY_BITS     = 256;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptionService(@Value("${app.encryption.secret-key}") String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                "AES encryption key must be exactly 32 bytes (256 bits) when Base64-decoded. " +
                "Got: " + keyBytes.length + " bytes."
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("AES-256/GCM encryption service initialised");
    }

    // ── Encrypt ────────────────────────────────────────────────

    /**
     * Encrypts a plaintext string (e.g. base64 PNG image data).
     *
     * @param plaintext raw value to encrypt
     * @return Base64-encoded string containing IV + ciphertext + GCM tag
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Pack: IV (12) + ciphertext+tag
            ByteBuffer packed = ByteBuffer.allocate(iv.length + ciphertext.length);
            packed.put(iv);
            packed.put(ciphertext);

            return Base64.getEncoder().encodeToString(packed.array());
        } catch (Exception e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    // ── Decrypt ────────────────────────────────────────────────

    /**
     * Decrypts a previously encrypted payload.
     *
     * @param encryptedBase64 the Base64 string produced by {@link #encrypt(String)}
     * @return original plaintext
     * @throws CryptoException if decryption or authentication fails
     */
    public String decrypt(String encryptedBase64) {
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedBase64);

            ByteBuffer buf = ByteBuffer.wrap(packed);

            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);

            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("Decryption failed — data may be tampered or key mismatch", e);
        }
    }

    // ── Key generation utility (run once, store result in config) ──

    /**
     * Generates a fresh AES-256 key as a Base64 string.
     * Use this once to produce the value for app.encryption.secret-key.
     */
    public static String generateKeyBase64() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(KEY_BITS, new SecureRandom());
            return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("AES key generation failed", e);
        }
    }
}