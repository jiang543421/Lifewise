-- ============================================================
-- V13__create_mv_meal_nutrition_weekly.sql
-- 物化视图：每用户每周每餐次营养汇总
-- 关联：business-architecture §6.2 饮食统计
-- ============================================================

CREATE MATERIALIZED VIEW mv_meal_nutrition_weekly AS
SELECT
    m.user_id,
    date_trunc('week', m.local_date)::DATE AS week_start,
    m.meal_type,
    COUNT(DISTINCT m.id)             AS meal_count,
    SUM(mi.kcal_snapshot)            AS total_kcal,
    SUM(mi.protein_g_snapshot)       AS total_protein_g,
    SUM(mi.fat_g_snapshot)           AS total_fat_g,
    SUM(mi.carb_g_snapshot)          AS total_carb_g,
    AVG(mi.kcal_snapshot)            AS avg_kcal_per_item,
    MIN(m.local_date)                AS first_date,
    MAX(m.local_date)                AS last_date,
    NOW()                            AS refreshed_at
FROM meals m
JOIN meal_items mi ON mi.meal_id = m.id AND mi.local_date = m.local_date
WHERE m.deleted_at IS NULL
GROUP BY m.user_id, date_trunc('week', m.local_date), m.meal_type;

-- CONCURRENTLY 刷新前置
CREATE UNIQUE INDEX uq_mv_meal_nutrition_weekly
    ON mv_meal_nutrition_weekly(user_id, week_start, meal_type);

CREATE INDEX idx_mv_meal_nutrition_user_week
    ON mv_meal_nutrition_weekly(user_id, week_start DESC);

COMMENT ON MATERIALIZED VIEW mv_meal_nutrition_weekly IS
    '每用户每周每餐次营养汇总（应用层按需 REFRESH）';
