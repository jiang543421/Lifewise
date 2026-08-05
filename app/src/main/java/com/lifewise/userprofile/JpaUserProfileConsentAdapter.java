package com.lifewise.userprofile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * user_profiles.ai_consent JPA 适配器（plan-06-ai §7.1）。
 *
 * <p>为什么用原生 SQL 而非 {@code UserProfile} 实体：diet 模块的 UserProfile 仅
 * 映射 diet 关心的字段（身高/体重/卡路里目标），ai_consent 字段需要在不引入
 * 跨模块耦合的情况下可读写。因此本适配器用 {@code EntityManager.createNativeQuery}
 * 直接读 / 写 user_profiles 的 3 个字段（user_id, ai_consent, ai_consent_at），
 * 避免与 {@code com.lifewise.diet.domain.UserProfile} 实体耦合。
 *
 * <p>事务隔离：{@code grantAiConsent} / {@code revokeAiConsent} 走默认 REQUIRED
 * 事务，与 AI 模块调用方同事务提交（plan §6 步骤 2.5）。
 */
@Component
public class JpaUserProfileConsentAdapter implements UserProfileConsentReader, UserProfileConsentWriter {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public boolean isAiConsentGranted(Long userId) {
        Object result = entityManager.createNativeQuery(
                        "SELECT ai_consent FROM user_profiles WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (result == null) {
            return false;
        }
        return (Boolean) result;
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetDateTime getAiConsentAt(Long userId) {
        Object result = entityManager.createNativeQuery(
                        "SELECT ai_consent_at FROM user_profiles WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (result == null) {
            return null;
        }
        return ((java.sql.Timestamp) result).toInstant().atOffset(ZoneOffset.UTC);
    }

    @Override
    @Transactional
    public void grantAiConsent(Long userId, OffsetDateTime when) {
        // INSERT ... ON CONFLICT DO UPDATE：用户首次开启时 user_profiles 暂无行时也保证幂等
        entityManager.createNativeQuery(
                        "INSERT INTO user_profiles (user_id, ai_consent, ai_consent_at, created_at, updated_at) "
                                + "VALUES (:userId, TRUE, :when, NOW(), NOW()) "
                                + "ON CONFLICT (user_id) DO UPDATE SET "
                                + "ai_consent = TRUE, ai_consent_at = :when, updated_at = NOW()")
                .setParameter("userId", userId)
                .setParameter("when", when)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void revokeAiConsent(Long userId) {
        entityManager.createNativeQuery(
                        "UPDATE user_profiles SET ai_consent = FALSE, ai_consent_at = NULL, updated_at = NOW() "
                                + "WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
