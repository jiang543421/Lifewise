package com.lifewise.auth.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 密码重置 token 持久化（plan-auth §5.4 + V50）。
 *
 * <p>B-7 closure (v1.3.3): {@code AuthController.forgotPassword} 端点生成 token,
 * {@code resetPassword} 端点消费 token。Token 明文只通过 EmailService 投递给用户,
 * 数据库只存 SHA-256 哈希。
 *
 * <p>字段映射（V50）：
 * <ul>
 *   <li>{@code tokenHash} — SHA-256(raw_token) 哈希；唯一索引</li>
 *   <li>{@code expiresAt} — TTL 1 小时</li>
 *   <li>{@code usedAt} — reset 成功后写入，触发 reuse 检测</li>
 *   <li>{@code revokedAt} — 主动失效或检测到 reuse</li>
 * </ul>
 */
@Entity
@Table(
        name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_password_reset_tokens_user_active",
                        columnList = "user_id, created_at DESC")
        })
public class PasswordResetToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected PasswordResetToken() {
        // JPA
    }

    private PasswordResetToken(Builder b) {
        this.userId = b.userId;
        this.tokenHash = b.tokenHash;
        this.expiresAt = b.expiresAt;
    }

    public static PasswordResetToken issue(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        return new Builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
    }

    /** reset 成功后写入 usedAt */
    public void markUsed(OffsetDateTime when) {
        this.usedAt = when;
    }

    /** 主动失效或检测到 reuse */
    public void revoke(OffsetDateTime when) {
        this.revokedAt = when;
    }

    public boolean isUsable(OffsetDateTime now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public Long userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
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

    public static final class Builder {
        private Long userId;
        private String tokenHash;
        private OffsetDateTime expiresAt;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder tokenHash(String tokenHash) {
            this.tokenHash = tokenHash;
            return this;
        }

        public Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public PasswordResetToken build() {
            return new PasswordResetToken(this);
        }
    }
}