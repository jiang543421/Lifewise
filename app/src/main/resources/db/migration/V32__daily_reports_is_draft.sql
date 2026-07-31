-- ============================================================
-- V32__daily_reports_is_draft.sql
-- v1.2 P1：daily_reports.is_draft 草稿标记
-- 关联：data-model-v1.2-amendment.md §4.2 日报草稿
-- ============================================================

ALTER TABLE daily_reports
    ADD COLUMN IF NOT EXISTS is_draft BOOLEAN NOT NULL DEFAULT TRUE;

-- 索引：仅查已发布
CREATE INDEX IF NOT EXISTS idx_daily_reports_published
    ON daily_reports(user_id, local_date DESC)
    WHERE is_draft = FALSE AND deleted_at IS NULL;

COMMENT ON COLUMN daily_reports.is_draft IS 'v1.2 P1：草稿标记（用户确认后才置 FALSE）';
