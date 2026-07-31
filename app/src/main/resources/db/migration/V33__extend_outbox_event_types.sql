-- ============================================================
-- V33__extend_outbox_event_types.sql
-- v1.2 P1：outbox_events.event_type 白名单扩展至 25 条
-- 关联：data-model-v1.2-amendment.md §3.3 事件枚举扩展
-- 业务架构引用：business-architecture §5.3 事件契约
-- 断言触发：flyway_should_reject_invalid_outbox_event_type
-- ============================================================

-- 原 V2 约束已含 15 条；扩展至 25 条（删 + 加）
ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_event_type_check;

ALTER TABLE outbox_events ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        -- 任务（4）
        'task.created','task.updated','task.completed','task.reopened',
        -- 里程碑（4）
        'milestone.created','milestone.updated','milestone.completed','milestone.missed',
        -- 习惯（1）
        'habit.logged',
        -- 餐次（1）
        'meal.created',
        -- 消费（2）
        'expense.created','budget.threshold',
        -- 计划（1）
        'plan.created',
        -- AI（2）
        'ai.job.completed','ai.report.feedback',
        -- v1.2 新增（10）
        'export.requested','export.completed','export.failed',
        'notification.requested','notification.delivered','notification.failed',
        'conversation.created','conversation.archived',
        'auth.login','auth.logout'
    ));

COMMENT ON CONSTRAINT outbox_events_event_type_check ON outbox_events IS
    'v1.2 P1：事件枚举扩展至 25 条（业务架构 §5.3）';
