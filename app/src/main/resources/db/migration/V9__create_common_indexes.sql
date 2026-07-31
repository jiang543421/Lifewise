-- ============================================================
-- V9__create_common_indexes.sql
-- 通用索引补充（V1~V8 已加业务专属索引；这里补外键与跨域引用）
-- 关联：business-architecture §5.3 事件契约 / data-model-design-v1.1.1 索引清单
-- ============================================================

-- outbox_events 聚合根查询（按 aggregate_type + id 查找）
CREATE INDEX idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id, occurred_at DESC);

-- outbox_events 全局已发布扫描
CREATE INDEX idx_outbox_published_user
    ON outbox_events(user_id, published_at DESC)
    WHERE published_at IS NOT NULL;

-- job_runs 按状态监控
CREATE INDEX idx_job_runs_status_started
    ON job_runs(status, started_at DESC);

-- 任务→习惯 / 任务→里程碑 跨域查询
CREATE INDEX idx_milestones_user_completed
    ON milestones(user_id, completed_at DESC)
    WHERE completed_at IS NOT NULL;

-- habit_logs 用户-时间区间
CREATE INDEX idx_habit_logs_habit_date
    ON habit_logs(habit_id, local_date DESC);

-- ai_summaries 对话引用按 daily_report
CREATE INDEX idx_ai_summaries_daily_report
    ON ai_summaries(daily_report_id, local_date)
    WHERE daily_report_id IS NOT NULL;

-- ai_reports 关联 job
CREATE INDEX idx_ai_reports_job
    ON ai_reports(job_id);

-- chat_messages 用户会话顺序
CREATE INDEX idx_chat_messages_user_pending
    ON chat_messages(user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- 消费→餐次 反向引用
CREATE INDEX idx_meals_expense
    ON meals(expense_id) WHERE expense_id IS NOT NULL;

-- 消费分类排序列表
CREATE INDEX idx_expense_categories_parent
    ON expense_categories(parent_id) WHERE parent_id IS NOT NULL;

COMMENT ON INDEX idx_outbox_aggregate IS 'Outbox 消费方按聚合根查询（CLAUDE.md §10 改动须列出受影响模块）';
COMMENT ON INDEX idx_job_runs_status_started IS 'Job 监控路径（actuator / Prometheus 抓取）';
