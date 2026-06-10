package com.labmentix.docsign.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmentix.docsign.audit.entity.AuditEventType;
import com.labmentix.docsign.audit.entity.AuditLog;
import com.labmentix.docsign.audit.repository.AuditLogRepository;
import com.labmentix.docsign.audit.service.AuditContext;
import com.labmentix.docsign.audit.service.AuditService;
import com.labmentix.docsign.common.crypto.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService — chain write and verification")
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock AuditContext       auditContext;
    @Spy  CryptoUtil         cryptoUtil = new CryptoUtil();
    @Spy  ObjectMapper       objectMapper = new ObjectMapper();
    @InjectMocks AuditService auditService;

    private static final String TEST_SECRET = "test-hmac-secret-key-for-unit-tests";

    @BeforeEach
    void injectSecret() {
        ReflectionTestUtils.setField(auditService, "hmacSecret", TEST_SECRET);
    }

    // ── Helper: build a fake AuditLog as if stored in DB ──────

    private AuditLog fakeLog(AuditEventType type, String actorEmail,
                              String docId, Instant ts, String prevHash) {
        String input = String.join("|",
                type.canonical(),
                actorEmail != null ? actorEmail : "anonymous",
                docId != null ? docId : "none",
                ts.toString(),
                prevHash
        );
        String hmac = cryptoUtil.hmacSha256Hex(input, TEST_SECRET);

        return AuditLog.builder()
                .id(UUID.randomUUID())
                .actorEmail(actorEmail)
                .eventType(type)
                .hmacHash(hmac)
                .createdAt(ts)
                .build();
    }

    @Nested
    @DisplayName("Chain verification — intact logs")
    class IntactChain {

        @Test
        @DisplayName("Empty log reports intact")
        void emptyLog_intact() {
            when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of());

            var result = auditService.verifyFullChain();

            assertThat(result.intact()).isTrue();
            assertThat(result.totalRows()).isEqualTo(0);
            assertThat(result.findings()).isEmpty();
        }

        @Test
        @DisplayName("Single row with GENESIS prev-hash verifies correctly")
        void singleRow_genesis_intact() {
            Instant ts   = Instant.parse("2024-06-01T10:00:00Z");
            AuditLog row = fakeLog(AuditEventType.USER_REGISTERED,
                                   "gauri@labmentix.com", "none", ts, "GENESIS");

            when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of(row));

            var result = auditService.verifyFullChain();

            assertThat(result.intact()).isTrue();
            assertThat(result.totalRows()).isEqualTo(1);
            assertThat(result.tamperedRows()).isEqualTo(0);
        }

        @Test
        @DisplayName("Three-row chain verifies end-to-end")
        void threeRowChain_allIntact() {
            Instant t1 = Instant.parse("2024-06-01T10:00:00Z");
            Instant t2 = Instant.parse("2024-06-01T10:01:00Z");
            Instant t3 = Instant.parse("2024-06-01T10:02:00Z");

            AuditLog r1 = fakeLog(AuditEventType.USER_LOGIN,        "g@x.com", "none", t1, "GENESIS");
            AuditLog r2 = fakeLog(AuditEventType.DOCUMENT_UPLOADED, "g@x.com", "doc1", t2, r1.getHmacHash());
            AuditLog r3 = fakeLog(AuditEventType.DOCUMENT_SENT,     "g@x.com", "doc1", t3, r2.getHmacHash());

            when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of(r1, r2, r3));

            var result = auditService.verifyFullChain();

            assertThat(result.intact()).isTrue();
            assertThat(result.totalRows()).isEqualTo(3);
            assertThat(result.findings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Chain verification — tampered logs")
    class TamperedChain {

        @Test
        @DisplayName("Modified HMAC hash triggers chain failure")
        void modifiedHmac_detected() {
            Instant t1 = Instant.parse("2024-06-01T10:00:00Z");
            Instant t2 = Instant.parse("2024-06-01T10:01:00Z");

            AuditLog r1 = fakeLog(AuditEventType.USER_LOGIN,        "g@x.com", "none", t1, "GENESIS");
            AuditLog r2 = fakeLog(AuditEventType.DOCUMENT_UPLOADED, "g@x.com", "doc1", t2, r1.getHmacHash());

            // Tamper: replace r1's stored hash with garbage
            AuditLog tampered = AuditLog.builder()
                    .id(r1.getId())
                    .actorEmail(r1.getActorEmail())
                    .eventType(r1.getEventType())
                    .hmacHash("0000000000000000000000000000000000000000000000000000000000000000")
                    .createdAt(r1.getCreatedAt())
                    .build();

            when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of(tampered, r2));

            var result = auditService.verifyFullChain();

            // Both rows fail: r1 because its hash is wrong, r2 because its prevHash input is now wrong
            assertThat(result.intact()).isFalse();
            assertThat(result.tamperedRows()).isGreaterThanOrEqualTo(1);
            assertThat(result.findings()).isNotEmpty();
        }

        @Test
        @DisplayName("Tampered actor email breaks verification")
        void modifiedActorEmail_detected() {
            Instant ts   = Instant.parse("2024-06-01T10:00:00Z");
            AuditLog row = fakeLog(AuditEventType.DOCUMENT_SIGNED, "original@x.com", "doc1", ts, "GENESIS");

            // Simulate what an attacker might do — swap the email in the object
            AuditLog tampered = AuditLog.builder()
                    .id(row.getId())
                    .actorEmail("attacker@x.com")   // changed
                    .eventType(row.getEventType())
                    .hmacHash(row.getHmacHash())     // but kept original hash
                    .createdAt(row.getCreatedAt())
                    .build();

            when(auditLogRepository.findAllForChainVerification()).thenReturn(List.of(tampered));

            var result = auditService.verifyFullChain();

            assertThat(result.intact()).isFalse();
            assertThat(result.findings()).hasSize(1);
            assertThat(result.findings().get(0).rowId()).isEqualTo(row.getId());
        }
    }

    @Nested
    @DisplayName("Audit write — REQUIRES_NEW propagation")
    class AuditWrite {

        @Test
        @DisplayName("record() uses latestEntry as prevHash or GENESIS")
        void record_usesGenesisWhenEmpty() {
            when(auditLogRepository.findLatestEntry()).thenReturn(Optional.empty());
            when(auditContext.getIpAddress()).thenReturn("127.0.0.1");
            when(auditContext.getUserAgent()).thenReturn("JUnit");
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not throw
            assertThatNoException().isThrownBy(() ->
                    auditService.record(
                            AuditEventType.USER_REGISTERED,
                            null, null,
                            Map.of("email", "test@x.com"),
                            "127.0.0.1", "TestAgent"
                    )
            );

            verify(auditLogRepository).save(argThat(log ->
                    log.getEventType() == AuditEventType.USER_REGISTERED &&
                    log.getHmacHash() != null &&
                    log.getHmacHash().length() == 64
            ));
        }
    }
}