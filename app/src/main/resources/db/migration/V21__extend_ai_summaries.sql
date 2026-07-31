-- ============================================================
-- V21__extend_ai_summaries.sql
-- v1.2 P0：ai_summaries 扩展标签 / / 关联
-- 关联：data-model-v1.2-amendment.md §2.1 ai_summaries 扩展
-- ============================================================

-- 标签（用户自定义标记；JSONB 数组）
ALTER TABLE ai_summaries
    ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]'::jsonb;

-- 关联源（日报 / 周报 / 计划复盘）
ALTER TABLE ai_summaries
    ADD COLUMN IF NOT EXISTS local_locale TEXT NULL
        CHECK (local_locale IS NULL OR length(local_locale) BETWEEN 2 AND 16);

-- 反馈平均分（应用层聚合）
ALTER TABLE ai_summaries
    ADD COLUMN IF NOT EXISTS helpful_score NUMERIC(3,2) NULL
        CHECK (helpful_score IS NULL OR (helpful_score >= 0 AND helpful_score <= 5));

-- 副作用轻微：cache_key 唯一索引已建，新列不破坏唯一性
CREATE INDEX IF NOT EXISTS idx_ai_summaries_tags_gin
    ON ai_summaries USING GIN (tags);

COMMENT ON COLUMN ai_summaries.tags IS 'v1.2 P0：用户标签 JSONB 数组（应用层分析用）';
COMMENT ON COLUMN ai_summaries.helpful_score IS 'v1.2 P0：用户反馈均分（0~5）';
