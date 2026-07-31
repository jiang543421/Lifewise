-- ============================================================
-- V25__chat_and_ai_summaries_revision.sql
-- v1.2 P0：chat_messages / ai_summaries 修订
-- 关联：data-model-v1.2-amendment.md §2.1/§2.3
-- ============================================================

-- ai_summaries.model_version 提为 NOT NULL（V5 留 NULL 以利回头补）
-- 现有非空验证：所有 v1.1 写入必须已带 model_version；
-- 过渡：用 COALESCE 把历史 NULL 标 'unknown'
UPDATE ai_summaries
SET model_version = 'unknown'
WHERE model_version IS NULL;

ALTER TABLE ai_summaries
    ALTER COLUMN model_version SET NOT NULL;

-- chat_messages.conversation_id 改可空（V8 已留 NULL；此处显式声明）
COMMENT ON COLUMN chat_messages.conversation_id IS
    'v1.2 P1：nullable（ad-hoc 单条问询允许不关联会话）';

-- chat_messages 增 LOCAL 日期合理性约束
ALTER TABLE chat_messages
    ADD CONSTRAINT chat_messages_date_reasonable
    CHECK (local_date >= DATE '2020-01-01' AND local_date <= DATE '2100-12-31');

-- chat_messages 增 meta 元数据（应用层写入；如 model_token 计数）
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS meta JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN chat_messages.meta IS 'v1.2 P1：消息级元数据（token 数 / 延迟 / 引用源）';

-- ai_summaries 增 archived 标记
ALTER TABLE ai_summaries
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_ai_summaries_archived
    ON ai_summaries(archived_at)
    WHERE archived_at IS NOT NULL;
