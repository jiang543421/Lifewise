-- ============================================================
-- V6__create_expense_module.sql
-- 消费：expense_categories / expenses / budgets
-- 关联：data-model-design-v1.1.1 §3.5；BR-09/10/11/12
-- 注：expenses 改为按月 RANGE 分区表（V11 落地）
-- ============================================================

-- 8.1 expense_categories —— 分类目录（默认种子 + 用户自定义）
CREATE TABLE expense_categories (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NULL REFERENCES users(id) ON DELETE CASCADE,

    name                TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 50),
    icon                TEXT NULL CHECK (icon IS NULL OR length(icon) <= 50),
    color               TEXT NULL CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),

    -- 父分类（一级如「餐饮」→ 子类「午餐 / 晚餐 / 夜宵」）
    parent_id           BIGINT NULL REFERENCES expense_categories(id) ON DELETE SET NULL,

    -- 排序
    sort_order          INT NOT NULL DEFAULT 0,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expense_categories_user
    ON expense_categories(user_id, sort_order);

CREATE TRIGGER trg_expense_categories_set_updated_at
    BEFORE UPDATE ON expense_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE expense_categories IS '消费分类（user_id NULL 表示系统默认；用户可自定义）';

-- 8.2 expenses —— 消费记录；按月 RANGE 分区
CREATE TABLE expenses (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    category_id         BIGINT NOT NULL REFERENCES expense_categories(id) ON DELETE RESTRICT,

    local_date          DATE NOT NULL,
    timezone            TEXT NOT NULL DEFAULT 'UTC'
                        CHECK (length(timezone) BETWEEN 1 AND 64),

    -- BR-09 金额统一为分（int）
    amount_cents        INT NOT NULL CHECK (amount_cents > 0),
    currency            TEXT NOT NULL DEFAULT 'CNY'
                        CHECK (length(currency) = 3),

    -- 备忘
    note                TEXT NULL CHECK (note IS NULL OR length(note) <= 1000),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, local_date)
) PARTITION BY RANGE (local_date);

-- 8.3 budgets —— 月度预算（按 category + 月份）
CREATE TABLE budgets (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id         BIGINT NOT NULL REFERENCES expense_categories(id) ON DELETE CASCADE,

    period_year_month   CHAR(7) NOT NULL
                        CHECK (period_year_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),

    amount_cents        INT NOT NULL CHECK (amount_cents > 0),
    currency            TEXT NOT NULL DEFAULT 'CNY'
                        CHECK (length(currency) = 3),

    -- 阈值告警比例（BR-11 默认 80%）
    alert_threshold_pct INT NOT NULL DEFAULT 80
                        CHECK (alert_threshold_pct BETWEEN 1 AND 100),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- BR-10 月内同分类预算唯一
    UNIQUE (user_id, category_id, period_year_month)
);

CREATE INDEX idx_budgets_user_period
    ON budgets(user_id, period_year_month)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_budgets_set_updated_at
    BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE expenses IS '消费记录（BR-09 金额为分；按月分区）';
