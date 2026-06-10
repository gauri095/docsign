package com.labmentix.docsign.document;

import com.labmentix.docsign.common.exception.BadRequestException;
import com.labmentix.docsign.document.entity.DocumentStatus;
import com.labmentix.docsign.document.fsm.DocumentFsm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DocumentFsm — state transition guard")
class DocumentFsmTest {

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "DRAFT,           SENT",
            "DRAFT,           CANCELLED",
            "SENT,            PARTIALLY_SIGNED",
            "SENT,            COMPLETED",
            "SENT,            CANCELLED",
            "SENT,            EXPIRED",
            "PARTIALLY_SIGNED,COMPLETED",
            "PARTIALLY_SIGNED,CANCELLED",
            "PARTIALLY_SIGNED,EXPIRED",
        })
        void shouldAllowValidTransition(DocumentStatus from, DocumentStatus to) {
            assertThatNoException().isThrownBy(() -> DocumentFsm.transition(from, to));
        }

        @ParameterizedTest(name = "{0} → {1} canTransition = true")
        @CsvSource({
            "DRAFT,SENT",
            "SENT,COMPLETED",
            "PARTIALLY_SIGNED,COMPLETED",
        })
        void canTransitionShouldReturnTrue(DocumentStatus from, DocumentStatus to) {
            assertThat(DocumentFsm.canTransition(from, to)).isTrue();
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @ParameterizedTest(name = "{0} → {1} should throw")
        @CsvSource({
            "DRAFT,           COMPLETED",
            "DRAFT,           PARTIALLY_SIGNED",
            "DRAFT,           EXPIRED",
            "COMPLETED,       SENT",
            "COMPLETED,       DRAFT",
            "COMPLETED,       CANCELLED",
            "EXPIRED,         SENT",
            "CANCELLED,       SENT",
            "CANCELLED,       DRAFT",
        })
        void shouldRejectInvalidTransition(DocumentStatus from, DocumentStatus to) {
            assertThatThrownBy(() -> DocumentFsm.transition(from, to))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid document status transition");
        }

        @Test
        @DisplayName("Terminal states have no allowed transitions")
        void terminalStatesHaveNoOutgoingTransitions() {
            for (DocumentStatus terminal : new DocumentStatus[]{
                    DocumentStatus.COMPLETED, DocumentStatus.EXPIRED, DocumentStatus.CANCELLED
            }) {
                assertThat(DocumentFsm.allowedTransitions(terminal)).isEmpty();
                assertThat(terminal.isTerminal()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Status predicates")
    class StatusPredicates {

        @Test
        void draftIsMutable() {
            assertThat(DocumentStatus.DRAFT.isMutable()).isTrue();
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
            value = DocumentStatus.class,
            names = {"SENT", "PARTIALLY_SIGNED", "COMPLETED", "EXPIRED", "CANCELLED"}
        )
        void nonDraftIsNotMutable(DocumentStatus status) {
            assertThat(status.isMutable()).isFalse();
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
            value = DocumentStatus.class,
            names = {"SENT", "PARTIALLY_SIGNED"}
        )
        void activeStatuses(DocumentStatus status) {
            assertThat(status.isActive()).isTrue();
        }
    }
}