-- ============================================================
-- V37__align_expense_to_v1_1_1.sql
-- plan-03-expense：加性对齐到 v1.1.1（保留分区结构与现有数据）
-- 关联：plan-03-expense §3 数据模型；data-model-v1.1.1 §3.5
-- 原则：不破坏 V6/V11/V12/V14；不动分区；只加列/索引/约束
-- 覆盖 BR-09（金额正数）/ BR-10（预算唯一）/ BR-20（删除迁移）/ BR-23（名称唯一）/
--      BR-24（默认分类保护）/ H-5（预算静音）
-- ============================================================

-- ============================================================
-- 1. expenses：补 pay_method、occurred_at；金额 BIGINT；note 长度收紧
-- ============================================================

-- 1.1 添加 pay_method 列（CASH/ALIPAY/WECHAT/BANK）
ALTER TABLE expenses
    ADD COLUMN pay_method TEXT NOT NULL DEFAULT 'CASH'
        CHECK (pay_method IN ('CASH','ALIPAY','WECHAT','BANK'));

-- 1.2 添加 occurred_at 列（应用层 INSERT 时同步 local_date）
ALTER TABLE expenses
    ADD COLUMN occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 1.3 金额 INT → BIGINT（无数据损失，V10 的 CHECK 沿用）
ALTER TABLE expenses ALTER COLUMN amount_cents TYPE BIGINT;

-- 1.4 note 长度从 1000 收紧到 200
--     （现有数据理论上无超长；若存在，应用层先 truncate 再升级；migration 中不带数据修复）
ALTER TABLE expenses DROP CONSTRAINT IF EXISTS expenses_note_length;
ALTER TABLE expenses ADD CONSTRAINT expenses_note_length
    CHECK (note IS NULL OR length(note) <= 200);

-- 1.5 目标索引（v1.1.1 §3.5.2）
CREATE INDEX IF NOT EXISTS idx_expenses_user_occurred
    ON expenses(user_id, occurred_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_expenses_user_category_occurred
    ON expenses(user_id, category_id, occurred_at DESC)
    WHERE deleted_at IS NULL;

-- ============================================================
-- 2. expense_categories：is_archived / is_user_default；name 长度；partial unique
-- ============================================================

-- 2.1 添加 is_archived / is_user_default 列
ALTER TABLE expense_categories
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_user_default BOOLEAN NOT NULL DEFAULT FALSE;

-- 2.2 name 长度从 1-50 收紧到 1-20
ALTER TABLE expense_categories DROP CONSTRAINT IF EXISTS expense_categories_name_length;
ALTER TABLE expense_categories ADD CONSTRAINT expense_categories_name_length
    CHECK (length(name) BETWEEN 1 AND 20);

-- 2.3 BR-24：is_user_default 必须 user_id 非空且未归档
ALTER TABLE expense_categories ADD CONSTRAINT expense_categories_default_protected
    CHECK (
        is_user_default = FALSE
        OR (is_archived = FALSE AND user_id IS NOT NULL AND deleted_at IS NULL)
    );

-- 2.4 BR-23：系统分类全局唯一（user_id IS NULL 且未软删）
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_categories_system_name
    ON expense_categories(name)
    WHERE user_id IS NULL AND deleted_at IS NULL;

-- 2.5 BR-23：用户自定义分类在用户内唯一
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_categories_user_name
    ON expense_categories(user_id, name)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;

-- 2.6 BR-24：每用户仅一个 is_user_default
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_categories_user_default
    ON expense_categories(user_id)
    WHERE is_user_default = TRUE;

-- 2.7 活跃分类索引（用户未归档视图）
CREATE INDEX IF NOT EXISTS idx_expense_categories_user_active
    ON expense_categories(user_id, sort_order)
    WHERE is_archived = FALSE AND deleted_at IS NULL;

-- ============================================================
-- 3. budgets：scope / period_year / period_month / notify_*；UNIQUE 重写；FK RESTRICT
-- ============================================================

-- 3.1 添加 scope（默认 CATEGORY 兼容旧数据）
ALTER TABLE budgets
    ADD COLUMN scope TEXT NOT NULL DEFAULT 'CATEGORY'
        CHECK (scope IN ('TOTAL','CATEGORY'));

-- 3.2 添加 period_year / period_month 派生列（nullable 先回填，再 SET NOT NULL）
ALTER TABLE budgets
    ADD COLUMN period_year INT NULL
        CHECK (period_year BETWEEN 2000 AND 2100),
    ADD COLUMN period_month INT NULL
        CHECK (period_month BETWEEN 1 AND 12);

-- 3.3 回填 period_year / period_month（从 period_year_month CHAR(7) 解析）
UPDATE budgets
SET period_year = CAST(substring(period_year_month FROM 1 FOR 4) AS INT),
    period_month = CAST(substring(period_year_month FROM 6 FOR 2) AS INT)
WHERE period_year IS NULL OR period_month IS NULL;

-- 3.4 设为 NOT NULL
ALTER TABLE budgets ALTER COLUMN period_year SET NOT NULL;
ALTER TABLE budgets ALTER COLUMN period_month SET NOT NULL;

-- 3.5 添加 notify_enabled / notify_muted_until（H-5）
ALTER TABLE budgets
    ADD COLUMN notify_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_muted_until DATE NULL;

-- 3.6 category_id 改为 nullable（TOTAL 预算无分类）；FK 改 RESTRICT
ALTER TABLE budgets ALTER COLUMN category_id DROP NOT NULL;
ALTER TABLE budgets DROP CONSTRAINT IF EXISTS budgets_category_id_fkey;
ALTER TABLE budgets ADD CONSTRAINT budgets_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES expense_categories(id) ON DELETE RESTRICT;

-- 3.7 scope / category_id 一致性 CHECK
ALTER TABLE budgets ADD CONSTRAINT budgets_scope_category_consistent
    CHECK (
        (scope = 'TOTAL' AND category_id IS NULL)
        OR (scope = 'CATEGORY' AND category_id IS NOT NULL)
    );

-- 3.8 H-5：notify_muted_until 必须落在当前预算月份内
ALTER TABLE budgets ADD CONSTRAINT budgets_mute_within_period
    CHECK (
        notify_muted_until IS NULL
        OR (
            notify_muted_until >= make_date(period_year, period_month, 1)
            AND notify_muted_until < (make_date(period_year, period_month, 1) + INTERVAL '1 month')::date
        )
    );

-- 3.9 替换 UNIQUE 约束（V6 的 3 列 → v1.1.1 的 5 列）
ALTER TABLE budgets DROP CONSTRAINT IF EXISTS budgets_user_id_category_id_period_year_month_key;
ALTER TABLE budgets ADD CONSTRAINT uq_budgets_user_scope_period
    UNIQUE (user_id, scope, category_id, period_year, period_month);

-- 3.10 金额 BIGINT
ALTER TABLE budgets ALTER COLUMN amount_cents TYPE BIGINT;

-- 3.11 索引
CREATE INDEX IF NOT EXISTS idx_budgets_user_period_v2
    ON budgets(user_id, period_year DESC, period_month DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_budgets_active_notify
    ON budgets(user_id, period_year, period_month)
    WHERE notify_enabled = TRUE AND deleted_at IS NULL;

-- 3.12 删除 V6 创建的 idx_budgets_user_period（V10 注释里指向 3 列索引）；
--     重建为 (user_id, period_year, period_month) 形式（保留同名索引，但 3 列形式替换）
--     注：保留 V6 索引（针对 period_year_month）即可；新索引名为 idx_budgets_user_period_v2
--     避免破坏既有 IT。后续 plan 可清理旧索引。

-- ============================================================
-- 注释
-- ============================================================

COMMENT ON COLUMN expenses.pay_method IS 'plan-03 §2.1：支付方式 CASH/ALIPAY/WECHAT/BANK';
COMMENT ON COLUMN expenses.occurred_at IS 'plan-03 §3：账单发生时间（UTC），分区仍按 local_date';
COMMENT ON COLUMN expense_categories.is_archived IS 'BR-24 / UI 归档：归档后不出现在默认列表';
COMMENT ON COLUMN expense_categories.is_user_default IS 'BR-24：每用户预置的「其他」分类（保护不可改/删）';
COMMENT ON COLUMN budgets.scope IS 'plan-03 §2.3：TOTAL 总预算 / CATEGORY 分类预算';
COMMENT ON COLUMN budgets.period_year IS 'plan-03 §2.3：预算年份（period_year_month 的派生列）';
COMMENT ON COLUMN budgets.period_month IS 'plan-03 §2.3：预算月份 1-12';
COMMENT ON COLUMN budgets.notify_enabled IS 'H-5：预算通知总开关';
COMMENT ON COLUMN budgets.notify_muted_until IS 'H-5：静音到期日期（仅当月有效）';