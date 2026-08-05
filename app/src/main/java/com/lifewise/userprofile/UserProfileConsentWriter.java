package com.lifewise.userprofile;

import java.time.OffsetDateTime;

/**
 * user_profiles.ai_consent 写端口（plan-06-ai §7.1）。
 *
 * <p>由 AI 模块独占调用，其他模块若需调整 ai_consent 必须经 ai 模块走应用层
 * 审计（plan §6 步骤 2.5）。
 */
public interface UserProfileConsentWriter {

    /** 同意：设置 ai_consent=true + ai_consent_at=now（UTC）。 */
    void grantAiConsent(Long userId, OffsetDateTime when);

    /** 撤销：设置 ai_consent=false + ai_cons_at=NULL。 */
    void revokeAiConsent(Long userId);
}
