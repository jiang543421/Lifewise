-- ============================================================
-- V46__extend_ai_jobs_job_type.sql
-- 扩 ai_jobs.job_type CHECK 加 MONTHLY_SUMMARY / TASK_ADVICE
-- 关联：plan-06-ai.md §3 数据模型；review Findings #2
-- 业务架构引用：business-architecture §6.6 流程 6 月报 / §6.7 流程 7 任务建议
-- 注：Java enum 已在 feature/ai-step11-skeleton-wip 加这 2 值；
--     此迁移把 DDL 与 code 对齐，避免 UI "月报"/"任务建议" 触发 CHECK violation
-- ============================================================

ALTER TABLE ai_jobs DROP CONSTRAINT IF EXISTS ai_jobs_job_type_check;

ALTER TABLE ai_jobs ADD CONSTRAINT ai_jobs_job_type_check
    CHECK (job_type IN (
        'DAILY_SUMMARY','WEEKLY_SUMMARY','MONTHLY_SUMMARY',  -- 日 / 周 / 月 报告
        'PLAN_REVIEW',                                       -- 计划复盘
        'TASK_ADVICE',                                       -- 任务建议
        'HABIT_ANALYSIS','MEAL_ANALYSIS','EXPENSE_ANALYSIS', -- 习惯 / 饮食 / 消费分析
        'CUSTOM_PROMPT'                                      -- 自定义 prompt
    ));

COMMENT ON CONSTRAINT ai_jobs_job_type_check ON ai_jobs IS
    'v1.2 P0：job_type 扩 MONTHLY_SUMMARY / TASK_ADVICE（plan-06 §3 月报/任务建议）';