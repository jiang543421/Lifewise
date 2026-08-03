-- ============================================================
-- V37__daily_reports_fulltext_search.sql
-- 日报全文检索：content_tsv 列 + GIN 索引
-- 关联：plan-02-daily §3 数据模型索引 + 业务架构 §3.6
-- ============================================================

-- 触发器函数：把 content 投影为 tsvector
-- NOTE：使用 IMMUTABLE 包装让 generated column 可索引
CREATE OR REPLACE FUNCTION daily_reports_content_tsv_update()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', coalesce(NEW.content, ''));
    RETURN NEW;
END;
$$;

-- 加 content_tsv 列（与应用并行，先 NULL）
ALTER TABLE daily_reports
    ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- 回填历史
UPDATE daily_reports
SET content_tsv = to_tsvector('simple', coalesce(content, ''))
WHERE content_tsv IS NULL;

-- 维护触发器（BEFORE INSERT/UPDATE）
DROP TRIGGER IF EXISTS trg_daily_reports_content_tsv ON daily_reports;
CREATE TRIGGER trg_daily_reports_content_tsv
    BEFORE INSERT OR UPDATE OF content ON daily_reports
    FOR EACH ROW EXECUTE FUNCTION daily_reports_content_tsv_update();

-- GIN 索引（全文检索主路径）
CREATE INDEX IF NOT EXISTS idx_daily_reports_content_tsv
    ON daily_reports USING GIN (content_tsv);

-- 用户视角检索复合索引（user_id + content_tsv）—— 减小权限裁剪代价
CREATE INDEX IF NOT EXISTS idx_daily_reports_user_content_tsv
    ON daily_reports(user_id, content_tsv);

COMMENT ON COLUMN daily_reports.content_tsv IS
    'v1.2 P2：tsvector 投影（simple 配置）；INSERT/UPDATE 由触发器维护；GIN 索引支持全文检索 + ts_headline 摘要';
COMMENT ON INDEX idx_daily_reports_content_tsv IS
    'v1.2 P2：tsvector GIN 索引，支撑 daily_report 全文检索';
COMMENT ON INDEX idx_daily_reports_user_content_tsv IS
    'v1.2 P2：(user_id, content_tsv) 复合索引，减小跨用户裁剪代价';
