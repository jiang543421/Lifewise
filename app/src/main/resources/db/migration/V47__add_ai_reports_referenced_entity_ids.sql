-- ============================================================
-- V47__add_ai_reports_referenced_entity_ids.sql
-- 补 ai_reports 缺 referenced_entity_ids JSONB 列
-- 关联：plan-06-ai.md §2.1 GET /api/ai/reports 契约 + code-review Finding #3
-- 业务架构引用：business-architecture §6.6/§6.7 报告引用源 aggregateId 追溯
-- 注：DTO AiReportView 已改 List<Long> referencedEntityIds（commit f2336b3）；
--     此迁移让 DDL 与契约对齐，服务层可直接写入
-- ============================================================

ALTER TABLE ai_reports ADD COLUMN referenced_entity_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN ai_reports.referenced_entity_ids IS
    'v1.2 P1：报告引用的源 aggregateId 列表（plan §2.1 契约）；AiReportService 在生成时由 ScopedDataFetcher 填充';