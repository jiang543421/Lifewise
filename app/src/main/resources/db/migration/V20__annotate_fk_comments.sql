-- ============================================================
-- V20__annotate_fk_comments.sql
-- 表级 COMMENT 注解收尾 + 1 个轻量索引
-- 关联：business-architecture §4.3 跨域引用
-- ============================================================

COMMENT ON COLUMN meals.expense_id IS
    '关联消费记录（餐次可由消费导入；不强制 FK，应用层异步回填）';

COMMENT ON COLUMN meal_items.kcal_snapshot IS
    '餐标冗余（避免 foods 营养变更影响历史记录）';

COMMENT ON COLUMN expense_categories.user_id IS
    'NULL 表示系统默认；用户可自定义覆盖';

COMMENT ON COLUMN foods.user_id IS
    'NULL 表示系统默认；用户可扩展自有食物';

CREATE INDEX IF NOT EXISTS idx_meals_user_date_active
    ON meals(user_id, local_date)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX idx_meals_user_date_active IS
    '餐次按用户-日期快速查询（日报生成路径）';
