-- ============================================================
-- V12__create_mv_expense_monthly_category.sql
-- 物化视图：每用户每月每分类汇总
-- 关联：business-architecture §6.1 统计投影
-- 注：一次性 CREATE；后续 R__repeatable_mviews.sql 用 REFRESH 替代
-- ============================================================

CREATE MATERIALIZED VIEW mv_expense_monthly_category AS
SELECT
    e.user_id,
    to_char(e.local_date, 'YYYY-MM') AS period_year_month,
    e.category_id,
    c.name                          AS category_name,
    c.parent_id                     AS category_parent_id,
    e.currency,
    COUNT(*)                        AS expense_count,
    SUM(e.amount_cents)             AS total_amount_cents,
    AVG(e.amount_cents)             AS avg_amount_cents,
    MIN(e.amount_cents)             AS min_amount_cents,
    MAX(e.amount_cents)             AS max_amount_cents,
    MIN(e.local_date)               AS first_date,
    MAX(e.local_date)               AS last_date,
    NOW()                           AS refreshed_at
FROM expenses e
JOIN expense_categories c ON c.id = e.category_id
WHERE e.deleted_at IS NULL
GROUP BY e.user_id, to_char(e.local_date, 'YYYY-MM'),
         e.category_id, c.name, c.parent_id, e.currency;

-- CONCURRENTLY 刷新前置：必须有 UNIQUE INDEX
CREATE UNIQUE INDEX uq_mv_expense_monthly_category
    ON mv_expense_monthly_category(user_id, period_year_month, category_id, currency);

-- 常用查询索引
CREATE INDEX idx_mv_expense_monthly_user_period
    ON mv_expense_monthly_category(user_id, period_year_month);

CREATE INDEX idx_mv_expense_monthly_category
    ON mv_expense_monthly_category(category_id);

COMMENT ON MATERIALIZED VIEW mv_expense_monthly_category IS
    '每用户每月每分类消费汇总（每小时 03:00 REFRESH CONCURRENTLY）';
