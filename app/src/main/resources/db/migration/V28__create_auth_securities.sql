-- ============================================================
-- V28__create_auth_securities.sql
-- v1.2 P0：认证安全（refresh_tokens / email_verifications / password_resets）
-- 关联：business-architecture §5.5 认证授权；technical-architecture §5.1 Auth
-- 业务架构引用：CLAUDE.md §7.3 认证/授权
-- ============================================================

-- 28.1 refresh_tokens —— Refresh Token（rotate + reuse detection）
CREATE TABLE refresh_tokens (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 哈希（绝不存明文）
    token_hash          TEXT NOT NULL UNIQUE
                        CHECK (length(token_hash) BETWEEN 1 AND 128),

    -- 关系链（rotation 后原 token 标记 replaced_by）
    parent_id           BIGINT NULL REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    replaced_by         BIGINT NULL REFERENCES refresh_tokens(id) ON DELETE SET NULL,

    -- 设备指纹
    user_agent          TEXT NULL CHECK (user_agent IS NULL OR length(user_agent) <= 512),
    ip_address          TEXT NULL CHECK (ip_address IS NULL OR length(ip_address) <= 64),

    -- 生命周期
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ NULL,
    used_at             TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, issued_at DESC)
    WHERE revoked_at IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_refresh_tokens_expires
    ON refresh_tokens(expires_at)
    WHERE revoked_at IS NULL;

CREATE TRIGGER trg_refresh_tokens_set_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE refresh_tokens IS 'v1.2 P0：Refresh Token（CLAUDE.md §7.3 rotation + reuse detection）';

-- 28.2 email_verifications —— 邮箱验证
CREATE TABLE email_verifications (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 待验证邮箱（可能与 users.email 不同：如修改邮箱）
    email               TEXT NOT NULL CHECK (length(email) BETWEEN 3 AND 254),
    token_hash          TEXT NOT NULL UNIQUE CHECK (length(token_hash) BETWEEN 1 AND 128),

    -- 生命周期
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    consumed_at         TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verifications_user
    ON email_verifications(user_id, issued_at DESC)
    WHERE consumed_at IS NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_email_verifications_set_updated_at
    BEFORE UPDATE ON email_verifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE email_verifications IS 'v1.2 P0：邮箱验证 token';

-- 28.3 password_resets —— 密码重置
CREATE TABLE password_resets (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    token_hash          TEXT NOT NULL UNIQUE CHECK (length(token_hash) BETWEEN 1 AND 128),

    -- 生命周期
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    consumed_at         TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_resets_user
    ON password_resets(user_id, issued_at DESC)
    WHERE consumed_at IS NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_password_resets_set_updated_at
    BEFORE UPDATE ON password_resets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE password_resets IS 'v1.2 P0：密码重置 token';
