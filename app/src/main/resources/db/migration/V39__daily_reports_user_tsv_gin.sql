-- ============================================================
-- V39__daily_reports_user_tsv_gin.sql
-- 修复 V37 中 idx_daily_reports_user_content_tsv 的索引类型 bug：
-- 该索引原本按默认 btree 建立在 (user_id, content_tsv) 上，content_tsv 是 tsvector，
-- content 允许 50000 字符，tsvector 通常 > 2.7KB，超过 btree version 4 单条上限
-- （索引页 ~8KB，去除 header 后可用 ~2700 字节），导致长日报 INSERT/UPDATE 报错
-- "index row size ... exceeds btree version 4 maximum"。
--
-- 修复策略：
--   1. 删除 btree 索引
--   2. 用 btree_gin 扩展建立 (user_id, content_tsv) 复合 GIN 索引，
--      既保留 user_id 等值裁剪（作为 GIN 的 where 过滤），又允许 tsvector 列做 @@ 匹配
--      （如果未来想用复合 @@ 查询，例如 user_id-bound 全文检索可受益）
--   3. 主 GIN 索引 idx_daily_reports_content_tsv 仍由原生查询按 user_id 过滤
--      （plan-02-daily §5.1 daily_search_should_use_gin_index）使用，无需重建
--
-- 依赖：btree_gin 扩展（PG 内置 contrib）
-- 回滚：手工 DROP INDEX
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gin;

DROP INDEX IF EXISTS idx_daily_reports_user_content_tsv;

-- btree_gin 让 (user_id, content_tsv) 复合索引以 GIN 方式构建，
-- 既支持 user_id 等值裁剪（gin 内 btree 子索引）也支持 tsvector @@ 匹配。
CREATE INDEX IF NOT EXISTS idx_daily_reports_user_content_tsv
    ON daily_reports USING GIN (user_id, content_tsv);

COMMENT ON INDEX idx_daily_reports_user_content_tsv IS
    'v1.2 P2：V39 替换 V37 btree 索引；btree_gin 复合 GIN 同时支持 user_id 裁剪与 tsvector @@ 匹配';
