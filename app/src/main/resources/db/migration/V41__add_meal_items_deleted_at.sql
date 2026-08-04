-- ============================================================
-- V41__add_meal_items_deleted_at.sql
-- 修复 meal_items 缺 deleted_at 列与实体不匹配
-- 背景：MealItem 继承 BaseEntity 含 deletedAt 字段，但 V7 建表漏写该列；
--       unit 测试用 mock MealRepository 走不到 INSERT 路径，IT 暴露 schema 缺陷。
-- 设计：meal_items 软删除由 meal.softDelete + orphanRemoval 整替换触发，
--       单条 item.deleted_at 实际不会写 NULL（始终跟随 parent meal），
--       但保留列以满足 Hibernate INSERT/UPDATE 不报列缺失。
-- 关联：plan-04-diet §3
-- ============================================================

ALTER TABLE meal_items
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN meal_items.deleted_at IS
    '保留字段以匹配 BaseEntity；item 软删除跟随 parent meal.deleted_at（orphanRemoval 触发整替换）';