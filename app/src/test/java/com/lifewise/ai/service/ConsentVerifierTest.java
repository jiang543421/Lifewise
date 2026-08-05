package com.lifewise.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.ai.dto.ConsentView;
import com.lifewise.ai.service.audit.AiAuditDecision;
import com.lifewise.ai.service.audit.AiAuditLogger;
import com.lifewise.ai.service.exception.ConsentRequiredException;
import com.lifewise.userprofile.UserProfileConsentReader;
import com.lifewise.userprofile.UserProfileConsentWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ConsentVerifier 单元测试（plan-06-ai §7.1）。
 *
 * <p>BR-26：用户开启 ai_consent=true 才允许生成 AI 报告或调用 chat LLM 路径。
 * 验证 5 个用例：拒绝 / 通过 / 授权 / 撤销 / 撤销不触发 verify 副效应。
 */
@ExtendWith(MockitoExtension.class)
class ConsentVerifierTest {

    private static final long USER_ID = 7L;
    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 8, 5, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock UserProfileConsentReader consentReader;
    @Mock UserProfileConsentWriter consentWriter;
    @Mock AiAuditLogger auditLogger;
    ConsentVerifier verifier;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        verifier = new ConsentVerifier(consentReader, consentWriter, auditLogger, clock);
    }

    @Test
    @DisplayName("verifyOrThrow rejects when ai_consent is false and logs DENIED")
    void verifyOrThrow_consentFalse_throwsConsentRequired() {
        when(consentReader.isAiConsentGranted(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> verifier.verifyOrThrow(USER_ID))
                .isInstanceOf(ConsentRequiredException.class);

        ArgumentCaptor<AiAuditDecision> dec = ArgumentCaptor.forClass(AiAuditDecision.class);
        verify(auditLogger, times(1)).log(eq(USER_ID), dec.capture());
        assertThat(dec.getValue().decisionType()).isEqualTo("CONSENT_CHECK");
        assertThat(dec.getValue().decision()).isEqualTo("DENIED");
    }

    @Test
    @DisplayName("verifyOrThrow passes silently when ai_consent is true and logs APPROVED")
    void verifyOrThrow_consentTrue_passes() {
        when(consentReader.isAiConsentGranted(USER_ID)).thenReturn(true);

        verifier.verifyOrThrow(USER_ID);

        ArgumentCaptor<AiAuditDecision> dec = ArgumentCaptor.forClass(AiAuditDecision.class);
        verify(auditLogger, times(1)).log(eq(USER_ID), dec.capture());
        assertThat(dec.getValue().decisionType()).isEqualTo("CONSENT_CHECK");
        assertThat(dec.getValue().decision()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("updateConsent with true grants and writes GRANTED audit")
    void updateConsent_granted_writesProfileAndAudit() {
        Optional<ConsentView> result = verifier.updateConsent(USER_ID, true);

        assertThat(result).isPresent();
        ConsentView view = result.get();
        assertThat(view.aiConsent()).isTrue();
        assertThat(view.consentedAt()).isEqualTo(FIXED_NOW);

        verify(consentWriter, times(1)).grantAiConsent(USER_ID, FIXED_NOW);
        ArgumentCaptor<AiAuditDecision> dec = ArgumentCaptor.forClass(AiAuditDecision.class);
        verify(auditLogger, times(1)).log(eq(USER_ID), dec.capture());
        assertThat(dec.getValue().decisionType()).isEqualTo("CONSENT_UPDATE");
        assertThat(dec.getValue().decision()).isEqualTo("GRANTED");
    }

    @Test
    @DisplayName("updateConsent with false revokes and writes REVOKED audit")
    void updateConsent_revoked_writesProfileAndAudit() {
        Optional<ConsentView> result = verifier.updateConsent(USER_ID, false);

        assertThat(result).isPresent();
        assertThat(result.get().aiConsent()).isFalse();
        assertThat(result.get().consentedAt()).isNull();

        verify(consentWriter, times(1)).revokeAiConsent(USER_ID);
        ArgumentCaptor<AiAuditDecision> dec = ArgumentCaptor.forClass(AiAuditDecision.class);
        verify(auditLogger, times(1)).log(eq(USER_ID), dec.capture());
        assertThat(dec.getValue().decisionType()).isEqualTo("CONSENT_UPDATE");
        assertThat(dec.getValue().decision()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("revoking does not invoke verifyOrThrow side-effect path")
    void updateConsent_revoked_doesNotAuditConsentCheck() {
        verifier.updateConsent(USER_ID, false);

        verify(auditLogger, times(1)).log(any(), any());
        verify(consentReader, never()).isAiConsentGranted(anyLong());
    }
}
