-- ============================================================
-- V10__add_business_rule_checks.sql
-- 业务规则（BR）CHECK 约束集中补齐
-- 关联：business-architecture §5.2 BR 清单；data-model-design-v1.1.1 §5
-- 关键 BR：BR-09 金额正数 / BR-13 营养非负 / BR-29 时区快照 / BR-30 last_activity_at
-- ============================================================

-- BR-09：消费金额必须正（命名为断言要求的名称）
ALTER TABLE expenses
    ADD CONSTRAINT expenses_amount_cents_positive
    CHECK (amount_cents > 0);

-- BR-09：预算金额必须正
ALTER TABLE budgets
    ADD CONSTRAINT budgets_amount_cents_positive
    CHECK (amount_cents > 0);

-- BR-13：营养字段非负（foods 已在 V7 列上加；这里加到 meal_items 的 snapshot）
ALTER TABLE meal_items
    ADD CONSTRAINT meal_items_kcal_non_negative
    CHECK (kcal_snapshot >= 0);

ALTER TABLE meal_items
    ADD CONSTRAINT meal_items_protein_non_negative
    CHECK (protein_g_snapshot >= 0);

ALTER TABLE meal_items
    ADD CONSTRAINT meal_items_fat_non_negative
    CHECK (fat_g_snapshot >= 0);

ALTER TABLE meal_items
    ADD CONSTRAINT meal_items_carb_non_negative
    CHECK (carb_g_snapshot >= 0);

-- BR-29：milestones 时区与 due_at 一致性（同时存在或同时为空）
ALTER TABLE milestones
    ADD CONSTRAINT milestones_due_at_tz_consistent
    CHECK (
        (due_at IS NULL AND time_zone IS NULL)
        OR (due_at IS NOT NULL AND time_zone IS NOT NULL)
    );

-- BR-30：plans 日期顺序合法性
ALTER TABLE plans
    ADD CONSTRAINT plans_date_order
    CHECK (start_date IS NULL OR target_end_date IS NULL OR start_date <= target_end_date);

-- 预算周期格式
ALTER TABLE budgets
    ADD CONSTRAINT budgets_period_format
    CHECK (period_year_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$');

-- 任务完成时间合理性：completed_at 不应早于 created_at
ALTER TABLE tasks
    ADD CONSTRAINT tasks_completion_order
    CHECK (completed_at IS NULL OR completed_at >= created_at);

-- 习惯打卡日期合理性
ALTER TABLE habit_logs
    ADD CONSTRAINT habit_logs_date_reasonable
    CHECK (local_date >= DATE '2020-01-01' AND local_date <= DATE '2100-12-31');

COMMENT ON CONSTRAINT expenses_amount_cents_positive ON expenses IS 'BR-09 金额统一为分（int）';
COMMENT ON CONSTRAINT milestones_due_at_tz_consistent ON milestones IS 'BR-29 时区快照一致性';
COMMENT ON CONSTRAINT plans_date_order ON plans IS 'BR-30 计划日期顺序';
