-- V40 diet 模块扩展字段
-- 目的:
--   1) user_profiles 补齐 BMR/TDEE 所需字段 (plan-04-diet §5.5)
--   2) meals 补齐 total_kcal_cents (BIGINT) 用于跨模块 cents 聚合
--   3) foods 补齐 aliases JSONB 与 default_unit_g

BEGIN;

-- 1) user_profiles 扩展
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS height_cm        NUMERIC(5,1),
    ADD COLUMN IF NOT EXISTS weight_kg        NUMERIC(5,1),
    ADD COLUMN IF NOT EXISTS age              SMALLINT,
    ADD COLUMN IF NOT EXISTS gender           VARCHAR(8),
    ADD COLUMN IF NOT EXISTS activity_level   VARCHAR(16),
    ADD COLUMN IF NOT EXISTS updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2) meals.total_kcal_cents (BIGINT cents)
ALTER TABLE meals
    ADD COLUMN IF NOT EXISTS total_kcal_cents BIGINT NOT NULL DEFAULT 0;

-- 3) foods.aliases JSONB + default_unit_g
ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS aliases        JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS default_unit_g SMALLINT NOT NULL DEFAULT 100;

-- 索引
CREATE INDEX IF NOT EXISTS foods_aliases_gin_idx
    ON foods USING GIN (aliases jsonb_path_ops);

CREATE INDEX IF NOT EXISTS meals_user_local_date_idx
    ON meals (user_id, local_date) WHERE deleted_at IS NULL;

COMMIT;