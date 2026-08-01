package com.lifewise.auth.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * refresh_tokens 表实体（plan-auth §3.2 + V28 + V36）。
 *
 * <p>字段映射（V28 创建，V36 增加 family_id）：
 * <ul>
 *   <li>{@code tokenHash} — SHA-256(refresh_token) 哈希；唯一索引；绝不存明文</li>
 *   <li>{@code familyId} — UUID 标识 rotation family（plan-auth §2.3；reuse → 全 family 失效）</li>
 *   <li>{@code expiresAt} — TTL 30 天</li>
 *   <li>{@code usedAt} — rotation 时写入，触发 reuse 检测</li>
 *   <li>{@code revokedAt} — 主动吊销或 reuse 检测后写入</li>
 *   <li>{@code userAgent} / {@code ipAddress} — 设备指纹</li>
 * </ul>
 *
 * <p>V28 已建索引 {@code idx_refresh_tokens_user_active(user_id, issued_at DESC)}；
 * V36 增加 {@code idx_refresh_tokens_user_family(user_id, family_id)} 用于
 * {@code revokeFamily(userId, familyId)} 高频路径。
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_user_active",
                        columnList = "user_id, issued_at DESC"),
                @Index(name = "idx_refresh_tokens_user_family",
                        columnList = "user_id, family_id")
        })
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    protected RefreshToken() {
        // JPA
    }

    private RefreshToken(Builder b) {
        this.userId = b.userId;
        this.tokenHash = b.tokenHash;
        this.familyId = b.familyId;
        this.expiresAt = b.expiresAt;
        this.userAgent = b.userAgent;
        this.ipAddress = b.ipAddress;
    }

    public static RefreshToken issue(
            Long userId,
            UUID familyId,
            String tokenHash,
            OffsetDateTime expiresAt,
            String userAgent,
            String ipAddress) {
        return new Builder()
                .userId(userId)
                .familyId(familyId)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();
    }

    /** 标记本 token 已被使用（rotation 时由 child 触发）；若已 usedAt 非空 → reuse detection */
    public void markUsed(OffsetDateTime when) {
        this.usedAt = when;
    }

    /** 主动吊销或 reuse 检测后调用 */
    public void revoke(OffsetDateTime when) {
        this.revokedAt = when;
    }

    public boolean isUsable(OffsetDateTime now) {
        return revokedAt == null && usedAt == null && expiresAt.isAfter(now);
    }

    public Long userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public UUID familyId() {
        return familyId;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public OffsetDateTime usedAt() {
        return usedAt;
    }

    public OffsetDateTime revokedAt() {
        return revokedAt;
    }

    public String userAgent() {
        return userAgent;
    }

    public String ipAddress() {
        return ipAddress;
    }

    public static final class Builder {
        private Long userId;
        private String tokenHash;
        private UUID familyId;
        private OffsetDateTime expiresAt;
        private String userAgent;
        private String ipAddress;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder tokenHash(String tokenHash) {
            this.tokenHash = tokenHash;
            return this;
        }

        public Builder familyId(UUID familyId) {
            this.familyId = familyId;
            return this;
        }

        public Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public RefreshToken build() {
            return new RefreshToken(this);
        }
    }
}