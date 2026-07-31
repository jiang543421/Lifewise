-- ============================================================
-- V30__outbox_tracing_columns.sql
-- v1.2 P1：outbox 事件链路追踪（correlation_id / causation_id / event_version）
-- 关联：data-model-v1.2-amendment.md §4.1 可观测性链路
-- 业务架构引用：technical-architecture §9.4 链路追踪
-- ============================================================

-- 事件 schema 版本（向后兼容：旧消费者忽略未知字段）
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS event_version INT NOT NULL DEFAULT 1
        CHECK (event_version BETWEEN 1 AND 100);

-- 链路 ID（同一笔业务操作的所有事件共享）
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS correlation_id TEXT NULL
        CHECK (correlation_id IS NULL OR length(correlation_id) BETWEEN 1 AND 64);

-- 因果 ID（触发此事件的上游事件）
-- 注：outbox_events 为分区表（PK 包含 occurred_at），跨分区 FK 不可行，
--     故仅保留 BIGINT 列；不在数据库层强制 FK，改为应用层校验。
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS causation_id BIGINT NULL;

-- 索引（按 correlation 聚合）
CREATE INDEX IF NOT EXISTS idx_outbox_correlation
    ON outbox_events(correlation_id, occurred_at)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_causation
    ON outbox_events(causation_id)
    WHERE causation_id IS NOT NULL;

-- 部分索引带 application_name 关联（应用 + 业务事件串联）
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS trace_id TEXT NULL
        CHECK (trace_id IS NULL OR length(trace_id) BETWEEN 1 AND 64);

CREATE INDEX IF NOT EXISTS idx_outbox_trace
    ON outbox_events(trace_id)
    WHERE trace_id IS NOT NULL;

COMMENT ON COLUMN outbox_events.event_version IS 'v1.2 P1：事件 schema 版本（默认 1）';
COMMENT ON COLUMN outbox_events.correlation_id IS 'v1.2 P1：链路 ID（同一业务操作的所有事件共享）';
COMMENT ON COLUMN outbox_events.causation_id IS 'v1.2 P1：因果 ID（触发此事件的上游事件）';
COMMENT ON COLUMN outbox_events.trace_id IS 'v1.2 P1：应用层追踪 ID（W3C traceparent 简化）';
