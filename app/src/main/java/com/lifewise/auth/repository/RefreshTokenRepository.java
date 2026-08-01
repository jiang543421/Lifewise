package com.lifewise.auth.repository;

import com.lifewise.auth.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * refresh_tokens 仓库（plan-auth §3.2 + V28 + V36）。
 *
 * <p>关键派生方法：
 * <ul>
 *   <li>{@link #findByTokenHash(String)} — rotation 时按哈希查找</li>
 *   <li>{@link #findAllByUserIdAndFamilyId(Long, UUID)} — revokeFamily 高频路径</li>
 * </ul>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 拉取 family 内所有未被软删除的 token；用于 revokeFamily 全家族失效。
     * 与 plan-auth §3.2 一致：reuse 检测 → 该 family 全部 revokedAt 写入。
     */
    @Query("""
            SELECT rt FROM RefreshToken rt
            WHERE rt.userId = :userId
              AND rt.familyId = :familyId
              AND rt.deletedAt IS NULL
            """)
    List<RefreshToken> findAllByUserIdAndFamilyId(
            @Param("userId") Long userId,
            @Param("familyId") UUID familyId);

    List<RefreshToken> findAllByUserId(Long userId);
}