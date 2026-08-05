-- ============================================================
-- V39__add_expense_updated_deleted_events.sql
-- plan-03-expense Phase B-3：outbox 事件扩展（27 → 30 条）
-- 修订编号：P0-EXPENSE-EVENTS-01
-- 关联：known-limitations-v1 §B-3；business-architecture §5.3 事件契约
-- 断言触发：flyway_should_accept_new_outbox_event_types
-- ============================================================

-- 原 V36 约束已含 27 条；扩展至 30 条（删 + 加 3 条）
ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_event_type_check;

ALTER TABLE outbox_events ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        -- task / habit（5）
        'task.created','task.updated','task.completed','task.reopened',
        'habit.logged',
        -- plan / milestone（5）
        'plan.created',
        'milestone.created','milestone.updated','milestone.completed','milestone.missed',
        -- daily_report / ai.summary（3）
        'daily_report.created','daily_report.updated',
        'ai.summary.generated',
        -- meal / expense / budget（6，v1.2 +3）
        'meal.created',
        'expense.created','expense.updated','expense.restored','expense.deleted',
        'budget.threshold',
        -- ai（2）
        'ai.job.completed','ai.report.feedback',
        -- export（2）
        'export.completed','export.failed',
        -- notification（1）
        'notification.requested',
        -- auth canonical（4）
        'auth.user.registered','auth.user.logged_in',
        'auth.user.password_reset_requested','auth.token.reuse_detected',
        -- auth legacy（2）
        'auth.login','auth.logout'
    ));

COMMENT ON CONSTRAINT outbox_events_event_type_check ON outbox_events IS
    'v1.2 P0-EXPENSE-EVENTS-01：30 条事件白名单（plan-03-expense Phase B-3 追加 expense.updated + expense.restored + expense.deleted）';
