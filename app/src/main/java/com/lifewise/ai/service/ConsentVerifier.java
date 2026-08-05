package com.lifewise.ai.service;

import com.lifewise.ai.dto.ConsentView;
import com.lifewise.ai.service.audit.AiAuditDecision;
import com.lifewise.ai.service.audit.AiAuditLogger;
import com.lifewise.ai.service.exception.ConsentRequiredException;
import com.lifewise.userprofile.UserProfileConsentReader;
import com.lifewise.userprofile.UserProfileConsentWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 用户同意校验器（plan-06-ai §7.1；BR-26）。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #verifyOrThrow(Long)} — 报告生成 / Chat LLM 路径必先调用，
 *       {@code ai_consent=false} 时抛 {@link ConsentRequiredException}</li>
 *   <li>{@link #updateConsent(Long, boolean)} — POST /api/ai/consent 落地，
 *       同步写 CONSENT_UPDATE 审计</li>
 * </ul>
 *
 * <p>审计约束：每次决策都通过 {@link AiAuditLogger} 写 chat_messages role=SYSTEM，
 * 由 DB 角色 GRANT 限制 UPDATE/DELETE 权限（plan §6 步骤 2.5）。
 */
@Service
public class ConsentVerifier {

    private final UserProfileConsentReader consentReader;
    private final UserProfileConsentWriter consentWriter;
    private final AiAuditLogger auditLogger;
    private final Clock clock;

    public ConsentVerifier(UserProfileConsentReader consentReader,
                           UserProfileConsentWriter consentWriter,
                           AiAuditLogger auditLogger,
                           Clock clock) {
        this.consentReader = consentReader;
        this.consentWriter = consentWriter;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    public void verifyOrThrow(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        boolean granted = consentReader.isAiConsentGranted(userId);
        auditLogger.log(userId, AiAuditDecision.builder()
                .decisionType("CONSENT_CHECK")
                .decision(granted ? "APPROVED" : "DENIED")
                .build());
        if (!granted) {
            throw new ConsentRequiredException();
        }
    }

    @Transactional
    public Optional<ConsentView> updateConsent(Long userId, boolean granted) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (granted) {
            consentWriter.grantAiConsent(userId, now);
            auditLogger.log(userId, AiAuditDecision.builder()
                    .decisionType("CONSENT_UPDATE")
                    .decision("GRANTED")
                    .build());
            return Optional.of(new ConsentView(true, now));
        } else {
            consentWriter.revokeAiConsent(userId);
            auditLogger.log(userId, AiAuditDecision.builder()
                    .decisionType("CONSENT_UPDATE")
                    .decision("REVOKED")
                    .build());
            return Optional.of(new ConsentView(false, null));
        }
    }
}
