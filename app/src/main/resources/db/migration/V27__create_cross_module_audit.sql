-- ============================================================
-- V27__create_cross_module_audit.sql
-- v1.2 P0：跨模块操作审计 + Outbox 死信
-- 关联：business-architecture §5.4 跨模块协作规则
-- 业务架构引用：technical-architecture §9.4 审计日志
-- ============================================================

-- 27.1 operation_logs —— 跨模块操作审计
CREATE TABLE operation_logs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,

    -- 模块归属（一级范畴）
    module              TEXT NOT NULL
                        CHECK (module IN ('task','plan','daily','diet','expense','ai','shared','auth')),

    -- 操作类型（创建 / 更新 / 删除 / 跨域引用）
    operation           TEXT NOT NULL CHECK (length(operation) BETWEEN 1 AND 50),

    -- 聚合根
    aggregate_type      TEXT NOT NULL CHECK (length(aggregate_type) BETWEEN 1 AND 64),
    aggregate_id        BIGINT NULL,

    -- 关联 Outbox 事件 ID（V30 落 tracing 后联动）
    outbox_event_id     BIGINT NULL,

    -- 详情（JSONB 灵活）
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 来源
    source_ip           TEXT NULL,
    user_agent          TEXT NULL,

    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operation_logs_user_occurred
    ON operation_logs(user_id, occurred_at DESC);

CREATE INDEX idx_operation_logs_module_occurred
    ON operation_logs(module, occurred_at DESC);

CREATE INDEX idx_operation_logs_aggregate
    ON operation_logs(aggregate_type, aggregate_id, occurred_at DESC)
    WHERE aggregate_id IS NOT NULL;

COMMENT ON TABLE operation_logs IS 'v1.2 P0：跨模块操作审计（CLAUDE.md §10 改动必查）';

-- 27.2 outbox_dead_letter —— Outbox 死信
CREATE TABLE outbox_dead_letter (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 原 outbox 引用（逻辑外键，不强制 FK 防止删除污染）
    original_outbox_id  BIGINT NOT NULL,

    user_id             BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,

    aggregate_type      TEXT NOT NULL,
    aggregate_id        BIGINT NOT NULL,
    event_type          TEXT NOT NULL,
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 失败原因
    error               TEXT NOT NULL CHECK (length(error) BETWEEN 1 AND 4000),
    retry_count         INT NOT NULL CHECK (retry_count >= 0),

    -- 死信时间
    dead_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ NULL,
    resolved_by         TEXT NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_dead_letter_unresolved
    ON outbox_dead_letter(dead_at DESC)
    WHERE resolved_at IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_outbox_dead_letter_user
    ON outbox_dead_letter(user_id, dead_at DESC)
    WHERE user_id IS NOT NULL;

CREATE TRIGGER trg_outbox_dead_letter_set_updated_at
    BEFORE UPDATE ON outbox_dead_letter
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE outbox_dead_letter IS 'v1.2 P0：Outbox 投递多次失败的死信收容（人工介入）';
