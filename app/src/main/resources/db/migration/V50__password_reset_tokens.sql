-- ============================================================
-- V50: password_reset_tokens 表（plan-auth §5.4 + B-7 closure）
--
-- B-7 closure (v1.3.3): forgot-password / reset-password 端点落地需要
-- token 持久化。v1.0 单用户场景下用途有限（userId=1 永远知道密码），
-- 但需 spec-compliant 支持 + 为 v1.1+ 多用户场景铺路。
--
-- 设计要点：
--   - token_hash: SHA-256(raw_token), 唯一索引, 绝不存明文
--   - expires_at: TTL 1 小时（plan-auth §5.4 标准值）
--   - used_at: reset 成功后写入，触发 reuse 检测
--   - revoked_at: 主动失效入口
-- ============================================================

CREATE TABLE password_reset_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    token_hash      VARCHAR(128) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at         TIMESTAMP WITH TIME ZONE,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user_active
    ON password_reset_tokens (user_id, created_at DESC);

COMMENT ON TABLE password_reset_tokens IS
    '密码重置 token（plan-auth §5.4 + B-7 v1.3.3 closure）。token_hash = SHA-256(raw_token)';