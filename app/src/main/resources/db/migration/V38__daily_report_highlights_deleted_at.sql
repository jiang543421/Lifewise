-- ============================================================
-- V38__daily_module_deleted_at_columns.sql
-- 补齐 daily_report_highlights.deleted_at + ai_summaries.deleted_at 列
-- （BaseEntity 软删必需）
--
-- 背景：plan-02-daily §9 已知缺陷 —— V5 DDL 漏掉 deleted_at，
-- DailyReportHighlight / AiSummary 继承 BaseEntity 在 INSERT 时尝试写入此列。
-- 与 task_tags / habits 同类缺陷。本迁移对齐。
-- ============================================================

ALTER TABLE daily_report_highlights
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_daily_report_highlights_deleted_at
    ON daily_report_highlights(deleted_at);

COMMENT ON COLUMN daily_report_highlights.deleted_at IS
    'v1.2 P2：BaseEntity 软删时间戳；NULL = 有效；NOT NULL = 已删除';

ALTER TABLE ai_summaries
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_ai_summaries_deleted_at
    ON ai_summaries(deleted_at);

COMMENT ON COLUMN ai_summaries.deleted_at IS
    'v1.2 P2：BaseEntity 软删时间戳；NULL = 有效；NOT NULL = 已删除';

-- BR-21.c：用户编辑过摘要后 AI 不自动覆盖（应用层强制；DB 仅持久化标记）
ALTER TABLE ai_summaries
    ADD COLUMN IF NOT EXISTS user_edited BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ai_summaries.user_edited IS
    'v1.2 P2：BR-21.c 用户编辑标记；TRUE 后 AI 不得自动覆盖';