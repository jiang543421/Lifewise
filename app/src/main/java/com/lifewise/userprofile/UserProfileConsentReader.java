package com.lifewise.userprofile;

import java.time.OffsetDateTime;

/**
 * user_profiles.ai_consent 读端口（plan-06-ai §7.1）。
 *
 * <p>user_profiles 是 5 个模块共享的延展表（V2 §4.1），任何模块需要读取
 * AI 同意状态时通过本接口而非直接访问 JPA 实体。AI 模块只读 + 写 2 个
 * 字段，禁止破坏 diet 模块对 UserProfile 的所有权。
 */
public interface UserProfileConsentReader {

    /** 用户是否已开启 AI 同意。返回 {@code false} 表示未同意或用户不存在。 */
    boolean isAiConsentGranted(Long userId);

    /** 同意时间戳（用于展示 ConsentView.consentedAt）。 */
    OffsetDateTime getAiConsentAt(Long userId);
}
