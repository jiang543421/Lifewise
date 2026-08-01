package com.lifewise.auth.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * users 表实体（plan-auth §3.1 + V1 schema）。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code email} — 登录标识，唯一索引（V1）</li>
 *   <li>{@code passwordHash} — BCrypt cost=12 哈希（CLAUDE.md §7.3）</li>
 *   <li>{@code emailVerified} — 邮箱验证状态（V2 引入）</li>
 *   <li>{@code timezone} — IANA 时区 ID（如 Asia/Shanghai），用于日报切分</li>
 *   <li>{@code locale} — BCP-47（如 zh-CN）</li>
 *   <li>{@code status} — 用户状态（ACTIVE / LOCKED），与 V1 status 列一致</li>
 * </ul>
 *
 * <p>继承 {@link BaseEntity} 获得 createdAt / updatedAt / soft delete；
 * 本实体不可被其他模块直接持有引用（CLAUDE.md §1.2 模块边界）。
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 80)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected User() {
        // JPA
    }

    private User(Builder b) {
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.emailVerifiedAt = b.emailVerifiedAt;
        this.displayName = b.displayName;
        this.timezone = b.timezone;
        this.locale = b.locale;
        this.status = b.status;
    }

    public static User create(String email, String passwordHash, String displayName,
                              String timezone, String locale) {
        return new Builder()
                .email(email)
                .passwordHash(passwordHash)
                .displayName(displayName)
                .timezone(timezone)
                .locale(locale)
                .emailVerifiedAt(null)
                .status("ACTIVE")
                .build();
    }

    public void markEmailVerified(OffsetDateTime when) {
        this.emailVerifiedAt = when;
    }

    public void recordLogin(OffsetDateTime when) {
        this.lastLoginAt = when;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void lock() {
        this.status = "LOCKED";
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    public OffsetDateTime emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String displayName() {
        return displayName;
    }

    public String timezone() {
        return timezone;
    }

    public String locale() {
        return locale;
    }

    public String status() {
        return status;
    }

    public OffsetDateTime lastLoginAt() {
        return lastLoginAt;
    }

    public boolean isLocked() {
        return "LOCKED".equals(status);
    }

    public static final class Builder {
        private String email;
        private String passwordHash;
        private OffsetDateTime emailVerifiedAt;
        private String displayName;
        private String timezone;
        private String locale;
        private String status;

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder emailVerifiedAt(OffsetDateTime emailVerifiedAt) {
            this.emailVerifiedAt = emailVerifiedAt;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder timezone(String timezone) {
            this.timezone = timezone;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}