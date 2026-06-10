package com.labmentix.docsign.signing;

import com.labmentix.docsign.common.crypto.AesEncryptionService;
import com.labmentix.docsign.common.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AesEncryptionService — AES-256/GCM encrypt/decrypt")
class AesEncryptionServiceTest {

    private AesEncryptionService aes;

    // A valid 32-byte Base64-encoded key for testing
    private static final String TEST_KEY_B64 =
            Base64.getEncoder().encodeToString(new byte[32]);  // 32 zero-bytes

    @BeforeEach
    void setUp() {
        aes = new AesEncryptionService(TEST_KEY_B64);
    }

    @Nested
    @DisplayName("Encrypt → Decrypt roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("Plaintext survives encrypt/decrypt cycle")
        void roundtrip_plaintext() {
            String original  = "iVBORw0KGgoAAAANSUhEUgAAAAUA==";  // fake base64 PNG
            String encrypted = aes.encrypt(original);
            String decrypted = aes.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("Long signature data survives roundtrip")
        void roundtrip_longData() {
            String big = "A".repeat(50_000);   // ~50 KB of base64
            assertThat(aes.decrypt(aes.encrypt(big))).isEqualTo(big);
        }

        @Test
        @DisplayName("Each encrypt call produces a unique ciphertext (random IV)")
        void uniqueCiphertextPerCall() {
            String plaintext = "same-signature-data";
            String enc1 = aes.encrypt(plaintext);
            String enc2 = aes.encrypt(plaintext);
            assertThat(enc1).isNotEqualTo(enc2);  // different IVs → different ciphertexts
        }

        @Test
        @DisplayName("Both ciphertexts decrypt to the same plaintext")
        void bothDecryptCorrectly() {
            String pt = "docsign-signature";
            assertThat(aes.decrypt(aes.encrypt(pt))).isEqualTo(pt);
            assertThat(aes.decrypt(aes.encrypt(pt))).isEqualTo(pt);
        }
    }

    @Nested
    @DisplayName("Tamper detection — GCM auth tag")
    class TamperDetection {

        @Test
        @DisplayName("Flipping a byte in ciphertext triggers GCM auth failure")
        void tamperedCiphertext_throwsCryptoException() {
            String encrypted = aes.encrypt("important-signature-data");
            byte[] raw = Base64.getDecoder().decode(encrypted);

            // Flip a byte in the ciphertext portion (after the 12-byte IV)
            raw[20] ^= 0xFF;

            String tampered = Base64.getEncoder().encodeToString(raw);
            assertThatThrownBy(() -> aes.decrypt(tampered))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("Decryption failed");
        }

        @Test
        @DisplayName("Decrypting with a different key throws CryptoException")
        void wrongKey_throwsCryptoException() {
            String encrypted = aes.encrypt("secret-data");

            // Different key
            byte[] otherKey = new byte[32];
            otherKey[0] = (byte) 0xFF;
            AesEncryptionService aes2 = new AesEncryptionService(
                    Base64.getEncoder().encodeToString(otherKey)
            );

            assertThatThrownBy(() -> aes2.decrypt(encrypted))
                    .isInstanceOf(CryptoException.class);
        }

        @Test
        @DisplayName("Empty or garbage input throws CryptoException")
        void garbage_throwsCryptoException() {
            assertThatThrownBy(() -> aes.decrypt("not-valid-base64!!!"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Key validation")
    class KeyValidation {

        @Test
        @DisplayName("31-byte key throws IllegalArgumentException")
        void shortKey_throws() {
            String badKey = Base64.getEncoder().encodeToString(new byte[31]);
            assertThatThrownBy(() -> new AesEncryptionService(badKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("33-byte key throws IllegalArgumentException")
        void longKey_throws() {
            String badKey = Base64.getEncoder().encodeToString(new byte[33]);
            assertThatThrownBy(() -> new AesEncryptionService(badKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("generateKeyBase64 produces a decodable 32-byte key")
        void generatedKeyIsValid() {
            String key = AesEncryptionService.generateKeyBase64();
            byte[] decoded = Base64.getDecoder().decode(key);
            assertThat(decoded).hasSize(32);
            // Instantiation should not throw
            assertThatNoException().isThrownBy(() -> new AesEncryptionService(key));
        }
    }
}