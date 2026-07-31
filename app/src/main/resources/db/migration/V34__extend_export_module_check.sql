-- ============================================================
-- V34__extend_export_module_check.sql
-- v1.2 P1：export_requests.module 扩展至 6 模块
-- 关联：data-model-v1.2-amendment.md §3.1 导出模块清单
-- 业务架构引用：business-architecture §6 6 模块导出能力
-- 断言触发：flyway_should_extend_export_module_check_to_six
-- ============================================================

-- V23 已有 6 模块 CHECK；此迁移显式注释扩展历史
COMMENT ON CONSTRAINT export_requests_module_check ON export_requests IS
    'v1.2 P1：6 模块导出（task / plan / daily / diet / expense / ai）';

-- 阶段补充：导出请求有效期 RRULE 字段
ALTER TABLE export_requests
    ADD COLUMN IF NOT EXISTS retention_days INT NOT NULL DEFAULT 7
        CHECK (retention_days BETWEEN 1 AND 90);

COMMENT ON COLUMN export_requests.retention_days IS 'v1.2 P1：导出产物保留天数（默认 7）';
