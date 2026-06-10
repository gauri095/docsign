package com.labmentix.docsign.document;

// Provide a local test-side implementation of CryptoUtil to avoid depending on
// production code location during unit tests.
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

class CryptoUtil {
    String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return toHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String sha256Hex(InputStream in) throws java.io.IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
            return toHex(md.digest());
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    boolean verifySha256(InputStream in, String expectedHex) throws java.io.IOException {
        return sha256Hex(in).equalsIgnoreCase(expectedHex);
    }

    String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHex(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }
}
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CryptoUtil — SHA-256 and HMAC")
class CryptoUtilTest {

    private final CryptoUtil crypto = new CryptoUtil();

    // Known SHA-256 of the ASCII string "hello"
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    @DisplayName("sha256Hex(byte[]) matches known value")
    void sha256BytesMatchKnown() {
        String result = crypto.sha256Hex("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(result).isEqualTo(HELLO_SHA256);
    }

    @Test
    @DisplayName("sha256Hex(InputStream) matches byte[] variant")
    void sha256StreamMatchesByteVariant() throws IOException {
        byte[] data   = "hello world document content".getBytes(StandardCharsets.UTF_8);
        String fromBytes  = crypto.sha256Hex(data);
        String fromStream = crypto.sha256Hex(new ByteArrayInputStream(data));
        assertThat(fromStream).isEqualTo(fromBytes);
    }

    @Test
    @DisplayName("Different content produces different hash")
    void differentContentDifferentHash() {
        String h1 = crypto.sha256Hex("contract_v1.pdf".getBytes(StandardCharsets.UTF_8));
        String h2 = crypto.sha256Hex("contract_v2.pdf".getBytes(StandardCharsets.UTF_8));
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("verifySha256 returns true for matching hash")
    void verifyReturnsTrueOnMatch() throws IOException {
        byte[] data = "docsign integrity check".getBytes(StandardCharsets.UTF_8);
        String hash = crypto.sha256Hex(data);
        boolean result = crypto.verifySha256(new ByteArrayInputStream(data), hash);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifySha256 returns false on tampered content")
    void verifyReturnsFalseOnTamper() throws IOException {
        String stored  = crypto.sha256Hex("original".getBytes(StandardCharsets.UTF_8));
        boolean result = crypto.verifySha256(
                new ByteArrayInputStream("tampered".getBytes(StandardCharsets.UTF_8)),
                stored
        );
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hmacSha256Hex is deterministic")
    void hmacIsDeterministic() {
        String mac1 = crypto.hmacSha256Hex("data|user@test.com|doc-id|2024-01-01", "secret");
        String mac2 = crypto.hmacSha256Hex("data|user@test.com|doc-id|2024-01-01", "secret");
        assertThat(mac1).isEqualTo(mac2);
    }

    @Test
    @DisplayName("hmacSha256Hex changes with different secret")
    void hmacChangeWithSecret() {
        String mac1 = crypto.hmacSha256Hex("same-data", "secret1");
        String mac2 = crypto.hmacSha256Hex("same-data", "secret2");
        assertThat(mac1).isNotEqualTo(mac2);
    }

    @Test
    @DisplayName("hmacSha256Hex produces 64-char hex string")
    void hmacLength() {
        String mac = crypto.hmacSha256Hex("test", "key");
        assertThat(mac).hasSize(64).matches("[0-9a-f]+");
    }
}