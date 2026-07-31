-- ============================================================
-- V14__seed_default_categories.sql
-- 默认种子数据：系统默认 expense_categories + foods
-- 关联：technical-architecture §6.1 种子清单
-- 策略：user_id = NULL 表示系统默认；前端做合并展示
-- ============================================================

-- 一级分类（10 个）
INSERT INTO expense_categories (user_id, name, icon, color, sort_order, parent_id) VALUES
    (NULL, '餐饮',     '🍽️', '#F97316', 100, NULL),
    (NULL, '交通',     '🚇', '#3B82F6', 200, NULL),
    (NULL, '购物',     '🛍️', '#EC4899', 300, NULL),
    (NULL, '居住',     '🏠', '#10B981', 400, NULL),
    (NULL, '娱乐',     '🎮', '#A855F7', 500, NULL),
    (NULL, '医疗',     '💊', '#EF4444', 600, NULL),
    (NULL, '学习',     '📚', '#0EA5E9', 700, NULL),
    (NULL, '通讯',     '📱', '#6366F1', 800, NULL),
    (NULL, '人情往来', '🎁', '#F59E0B', 900, NULL),
    (NULL, '其他',     '📦', '#6B7280', 999, NULL);

-- 餐饮子分类
INSERT INTO expense_categories (user_id, name, icon, color, sort_order, parent_id)
SELECT NULL, c.name, c.icon, c.color, c.sort_order, parent.id
FROM (VALUES
    ('早餐', '🥐', '#FB923C', 110),
    ('午餐', '🍱', '#F97316', 120),
    ('晚餐', '🍲', '#EA580C', 130),
    ('夜宵', '🌙', '#C2410C', 140),
    ('饮料', '🥤', '#0EA5E9', 150),
    ('外卖', '📦', '#F59E0B', 160)
) AS c(name, icon, color, sort_order)
CROSS JOIN (SELECT id FROM expense_categories WHERE name = '餐饮' AND user_id IS NULL LIMIT 1) parent;

-- 交通子分类
INSERT INTO expense_categories (user_id, name, icon, color, sort_order, parent_id)
SELECT NULL, c.name, c.icon, c.color, c.sort_order, parent.id
FROM (VALUES
    ('地铁公交', '🚇', '#3B82F6', 210),
    ('打车',     '🚕', '#F59E0B', 220),
    ('加油',     '⛽', '#EF4444', 230),
    ('停车',     '🅿️', '#6B7280', 240),
    ('高速',     '🛣️', '#10B981', 250)
) AS c(name, icon, color, sort_order)
CROSS JOIN (SELECT id FROM expense_categories WHERE name = '交通' AND user_id IS NULL LIMIT 1) parent;

-- 系统默认食物（20 个常见）
INSERT INTO foods (user_id, name, kcal_per_100g, protein_g_per_100g, fat_g_per_100g, carb_g_per_100g, source) VALUES
    (NULL, '白米饭',  130, 2.7,  0.3, 28.0, 'SYSTEM'),
    (NULL, '白面包',  265, 9.0,  3.2, 49.0, 'SYSTEM'),
    (NULL, '鸡蛋',    155, 13.0, 11.0, 1.1, 'SYSTEM'),
    (NULL, '鸡胸肉',  165, 31.0, 3.6, 0.0,  'SYSTEM'),
    (NULL, '牛肉',    250, 26.0, 17.0, 0.0,  'SYSTEM'),
    (NULL, '三文鱼',  208, 20.0, 13.0, 0.0,  'SYSTEM'),
    (NULL, '牛奶',    42,  3.4,  1.0,  5.0,  'SYSTEM'),
    (NULL, '酸奶',    59,  3.5,  0.4,  9.0,  'SYSTEM'),
    (NULL, '苹果',    52,  0.3,  0.2,  14.0,'SYSTEM'),
    (NULL, '香蕉',    89,  1.1,  0.3,  23.0,'SYSTEM'),
    (NULL, '西兰花',  34,  2.8,  0.4,  7.0, 'SYSTEM'),
    (NULL, '胡萝卜',  41,  0.9,  0.2,  10.0,'SYSTEM'),
    (NULL, '西红柿',  18,  0.9,  0.2,  3.9, 'SYSTEM'),
    (NULL, '黄瓜',    16,  0.7,  0.1,  3.6, 'SYSTEM'),
    (NULL, '豆腐',    76,  8.0,  4.8,  1.9, 'SYSTEM'),
    (NULL, '土豆',    77,  2.0,  0.1,  17.0,'SYSTEM'),
    (NULL, '面条',    138, 4.5,  2.1,  25.0,'SYSTEM'),
    (NULL, '饺子',    250, 12.0, 11.0,  26.0,'SYSTEM'),
    (NULL, '可乐',    42,  0.0,  0.0,  11.0,'SYSTEM'),
    (NULL, '啤酒',    43,  0.5,  0.0,  3.6, 'SYSTEM');

COMMENT ON TABLE expense_categories IS '消费分类（含 V14 种子 10 个一级 + 11 个二级默认）';
COMMENT ON TABLE foods              IS '食物库（含 V14 种子 20 个常用食物）';
