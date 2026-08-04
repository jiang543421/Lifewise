-- ============================================================
-- V38__budgets_partial_unique_index.sql
-- plan-03-expense review H4：替换 budgets UNIQUE 约束为 partial unique index
-- 关联：plan-03 §1.2 V37 §3.9 + review finding H4 (BLOCK)
--
-- V37 (line 146) 的 uq_budgets_user_scope_period 有两个缺陷：
--   1) 未排除软删行：软删后重建同期预算会违反 UNIQUE → DataIntegrityViolationException 500
--   2) PG UNIQUE 默认 NULLS DISTINCT：TOTAL 预算 (category_id IS NULL) 不参与唯一性
--      → 同一 (user, scope=TOTAL, year, month) 可创建多条 TOTAL 预算
-- 修复：用 partial unique index + coalesce 把 NULL category_id 映射为 -1，
--       并通过 WHERE deleted_at IS NULL 排除软删行。
-- ============================================================

-- 1. 删 V37 加的 UNIQUE 约束（IF EXISTS 保证幂等）
ALTER TABLE budgets DROP CONSTRAINT IF EXISTS uq_budgets_user_scope_period;

-- 2. 创建 partial unique index（IF NOT EXISTS 保证幂等）
--    coalesce(category_id, -1) 让 TOTAL (NULL) 也参与唯一性
--    WHERE deleted_at IS NULL 排除软删行
CREATE UNIQUE INDEX IF NOT EXISTS uq_budgets_user_scope_period
    ON budgets(user_id, scope, coalesce(category_id, -1), period_year, period_month)
    WHERE deleted_at IS NULL;