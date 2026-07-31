-- ============================================================
-- V22__ai_reports_archival.sql
-- v1.2 P0：ai_reports 归档 + 留存衰减
-- 关联：data-model-v1.2-amendment.md §2.2 ai_reports 归档
-- ============================================================

-- 归档时间戳（应用层标记；超过 365 天的判定为 archived）
ALTER TABLE ai_reports
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ NULL;

-- 报告大小（字节；前端展示 / 清理依据）
ALTER TABLE ai_reports
    ADD COLUMN IF NOT EXISTS byte_size INT NULL
        CHECK (byte_size IS NULL OR byte_size >= 0);

-- 主题（用户可命名；便于检索）
ALTER TABLE ai_reports
    ADD COLUMN IF NOT EXISTS subject TEXT NULL
        CHECK (subject IS NULL OR length(subject) BETWEEN 1 AND 200);

-- 索引（归档后扫描）
CREATE INDEX IF NOT EXISTS idx_ai_reports_archived
    ON ai_reports(archived_at)
    WHERE archived_at IS NOT NULL;

COMMENT ON COLUMN ai_reports.archived_at IS 'v1.2 P0：归档标记（>365 天自动归档）';
COMMENT ON COLUMN ai_reports.byte_size IS 'v1.2 P0：报告字节大小（清理依据）';
COMMENT ON COLUMN ai_reports.subject IS 'v1.2 P0：用户自定义主题';
