-- ============================================================
-- V31__extend_ai_jobs_status.sql
-- v1.2 P1：ai_jobs.status 扩展 PENDING_PARTIAL / DONE_PARTIAL / DONE_NO_LLM
-- 关联：data-model-v1.2-amendment.md §2.4 AI 状态机
-- 业务架构引用：technical-architecture §6.4 Ollama 故障降级
-- ============================================================

-- 原 CHECK 约束已包含 DONE_PARTIAL / DONE_NO_LLM；此迁移显式注释约束演进历史
-- 同时新增 PENDING_PARTIAL（Ollama 临时不可用，应用层重排）

-- 旧 CHECK 替换：先丢弃再重建（PG < 15 不支持 CHECK 增量）
ALTER TABLE ai_jobs DROP CONSTRAINT IF EXISTS ai_jobs_status_check;

ALTER TABLE ai_jobs ADD CONSTRAINT ai_jobs_status_check
    CHECK (status IN (
        'PENDING','PENDING_PARTIAL',  -- 等待 / 等待降级重排
        'RUNNING','RUNNING_DEGRADED', -- 运行 / 降级运行
        'DONE','DONE_PARTIAL','DONE_NO_LLM', -- 完成 / 部分完成 / 无 LLM 仅本地
        'FAILED','CANCELLED'
    ));

COMMENT ON CONSTRAINT ai_jobs_status_check ON ai_jobs IS
    'v1.2 P1：状态机扩展（PENDING_PARTIAL / DONE_PARTIAL / DONE_NO_LLM）';
