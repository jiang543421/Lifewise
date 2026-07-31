-- ============================================================
-- V7__create_diet_module.sql
-- 饮食：foods / meals / meal_items
-- 关联：data-model-design-v1.1.1 §3.6；BR-13
-- 注：meals 改为按月 RANGE 分区表（V11 落地）
-- ============================================================

-- 9.1 foods —— 食物库（系统默认 + 用户自定义）
CREATE TABLE foods (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NULL REFERENCES users(id) ON DELETE CASCADE,

    name                TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 100),

    -- 营养（每 100g 标准化）
    kcal_per_100g       NUMERIC(8,2) NOT NULL CHECK (kcal_per_100g >= 0),
    protein_g_per_100g  NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (protein_g_per_100g >= 0),
    fat_g_per_100g      NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (fat_g_per_100g >= 0),
    carb_g_per_100g     NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (carb_g_per_100g >= 0),

    -- 食用单位（参考；可选）
    default_unit_g      NUMERIC(8,2) NULL CHECK (default_unit_g IS NULL OR default_unit_g > 0),

    -- 来源
    source              TEXT NOT NULL DEFAULT 'USER'
                        CHECK (source IN ('USER','SYSTEM','IMPORT')),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_foods_user_name
    ON foods(user_id, name)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_foods_set_updated_at
    BEFORE UPDATE ON foods
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE foods IS '食物库（user_id NULL 表示系统默认；营养按 100g 标准化）';

-- 9.2 meals —— 一餐；按月 RANGE 分区
CREATE TABLE meals (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    local_date          DATE NOT NULL,
    timezone            TEXT NOT NULL DEFAULT 'UTC'
                        CHECK (length(timezone) BETWEEN 1 AND 64),

    meal_type           TEXT NOT NULL
                        CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')),

    -- 消费链路（可选引用 expense 行）
    expense_id          BIGINT NULL,
    note                TEXT NULL CHECK (note IS NULL OR length(note) <= 2000),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, local_date)
) PARTITION BY RANGE (local_date);

-- 9.3 meal_items —— 一餐的每道食物
CREATE TABLE meal_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    meal_id             BIGINT NOT NULL,
    local_date          DATE NOT NULL,

    food_id             BIGINT NOT NULL REFERENCES foods(id) ON DELETE RESTRICT,

    -- 食用量（克）
    amount_g            NUMERIC(8,2) NOT NULL CHECK (amount_g > 0),

    -- 冗余餐标 ci（便于溯源历史）
    kcal_snapshot       NUMERIC(10,2) NOT NULL CHECK (kcal_snapshot >= 0),
    protein_g_snapshot  NUMERIC(8,2) NOT NULL CHECK (protein_g_snapshot >= 0),
    fat_g_snapshot      NUMERIC(8,2) NOT NULL CHECK (fat_g_snapshot >= 0),
    carb_g_snapshot     NUMERIC(8,2) NOT NULL CHECK (carb_g_snapshot >= 0),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    FOREIGN KEY (meal_id, local_date)
        REFERENCES meals(id, local_date) ON DELETE CASCADE
);

CREATE INDEX idx_meal_items_meal
    ON meal_items(meal_id);

CREATE INDEX idx_meal_items_food
    ON meal_items(food_id);

CREATE TRIGGER trg_meal_items_set_updated_at
    BEFORE UPDATE ON meal_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE meals IS '餐次（按月分区；FK 引用 expense 在 V20 注解）';
