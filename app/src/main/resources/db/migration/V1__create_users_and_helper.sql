-- ============================================================
-- V1__create_users_and_helper.sql
-- Lifewise MVP 基线
-- 关联：data-model-design-v1.1.1 §3.1.1 users / CLAUDE.md §0 主键策略
-- ============================================================

-- 共享 updated_at 触发器函数（被所有带 updated_at 列的表调用）
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION set_updated_at() IS
    '通用 BEFORE UPDATE 触发器：自动写 updated_at = NOW()（V1 基线）';

-- users：账号体系主表
CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    email           TEXT NOT NULL UNIQUE
                    CHECK (length(email) BETWEEN 3 AND 254
                           AND email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    password_hash   TEXT NOT NULL CHECK (length(password_hash) BETWEEN 20 AND 200),
    display_name    TEXT NOT NULL CHECK (length(display_name) BETWEEN 1 AND 100),

    timezone        TEXT NOT NULL DEFAULT 'UTC'
                    CHECK (length(timezone) BETWEEN 1 AND 64),

    role            TEXT NOT NULL DEFAULT 'USER'
                    CHECK (role IN ('USER','ADMIN')),

    email_verified_at   TIMESTAMPTZ NULL,
    last_login_at       TIMESTAMPTZ NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ NULL
);

CREATE INDEX idx_users_active_created
    ON users(id, created_at DESC)
    WHERE deleted_at IS NULL;

-- updated_at 自动维护
CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  users IS
    '账号体系主表（v1.1.1 §3.1.1；主键 BIGINT GENERATED ALWAYS AS IDENTITY — 见 CLAUDE.md §0）';
COMMENT ON COLUMN users.email          IS '登录邮箱（全局唯一）';
COMMENT ON COLUMN users.password_hash  IS 'bcrypt(cost=12) 哈希（technical-architecture §5.1）';
COMMENT ON COLUMN users.timezone       IS '业务时区（CLAUDE.md §3 时区策略）';
COMMENT ON COLUMN users.role           IS '角色：USER / ADMIN';
COMMENT ON COLUMN users.deleted_at     IS '软删除（CLAUDE.md §不变量 5：保留 30 天）';
