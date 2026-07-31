-- ============================================================
-- R__repeatable_mviews.sql
-- 可重复执行：每 30 分钟 REFRESH 物化视图（CONCURRENTLY；要求 UNIQUE INDEX 已存在）
-- 关联：business-architecture §6.1/§6.2 统计投影
-- 业务架构引用：technical-architecture §9.3 物化视图刷新策略
-- 注：Flyway 在每次启动时按 checksum 校验；VIEW 定义若改，自动重跑
-- ============================================================

-- mv_expense_monthly_category 增量刷新
-- （CONCURRENTLY 需 UNIQUE INDEX — V12 已建 uq_mv_expense_monthly_category）
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category;

-- mv_meal_nutrition_weekly 增量刷新
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_meal_nutrition_weekly;

-- 长期保留函数：未来物化视图统一入口
CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_meal_nutrition_weekly;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_all_materialized_views() IS
    '统一入口：刷新所有物化视图（业务架构 §6）';
