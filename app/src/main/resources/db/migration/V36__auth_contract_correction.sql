-- ============================================================
-- V36__auth_contract_correction.sql
-- v1.2 P0 步骤 5 修正：认证契约对齐 EventType 与 plan-auth §3.1/§3.2
-- 关联：plan-auth §3.1 users 列补齐；§3.2 refresh_tokens 必须有 family_id；
--       §4 auth.* 事件名
-- 业务架构引用：business-architecture §5.5 认证授权
--
-- 修正内容：
--   1. users 补列：display_name（V1 NOT NULL，要求 JPA 实体一致）、
--                  status（LOCKED 状态；V1 仅有 role，无法承载锁定）
--   2. refresh_tokens 增加 family_id UUID NOT NULL（rotation chain 标识）
--      —— V28 创建时未含此列（按 plan-auth §3.2 必须补齐）
--   3. refresh_tokens 增加 (user_id, family_id) 索引
--      —— revokeFamily 高频路径（plan-auth §3.2）
--   4. outbox_events.event_type CHECK 重写，对齐 EventType enum（25 条）
--      + V33 遗留 auth.login/auth.logout 别名
--
-- 断言触发：
--   - flyway_should_apply_v36_cleanly
--   - flyway_should_add_non_null_uuid_family_id_to_refresh_tokens
--   - flyway_should_accept_every_canonical_event_type_and_legacy_auth_aliases
-- ============================================================

-- --------------------------------------------------
-- 1. users 补列（display_name / status）
-- --------------------------------------------------

-- display_name：V1 已有 NOT NULL，但旧 JPA 实体未消费；此处保留 DEFAULT
-- 以保证历史行可被 backfill；新 row 由 User.create() 显式提供
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name TEXT;

-- 兜底：NULL 历史行写入 'User'
UPDATE users
SET display_name = 'User'
WHERE display_name IS NULL;

ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL;

-- locale：BCP-47（如 zh-CN），用于 AI 提示 / 错误信息本地化（plan-auth §2.1）
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS locale TEXT NOT NULL DEFAULT 'en'
    CHECK (length(locale) BETWEEN 2 AND 16);

-- status：用于 5 次失败锁定 / 手动封禁（plan-auth §5.3 锁定策略）
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','LOCKED','DISABLED'));

COMMENT ON COLUMN users.display_name IS
    'v1.2 P0 步骤 5：用户展示名（V1 表已存在，未被 JPA 实体消费）';
COMMENT ON COLUMN users.locale IS
    'v1.2 P0 步骤 5：BCP-47（如 zh-CN），用于 AI 提示 / 错误信息本地化（plan-auth §2.1）';
COMMENT ON COLUMN users.status IS
    'v1.2 P0 步骤 5：用户状态；ACTIVE / LOCKED（5 次失败）/ DISABLED（v1.1+ 手动）';

-- --------------------------------------------------
-- 2. refresh_tokens.family_id
-- --------------------------------------------------

-- 加列（用 NOT NULL，需先 backfill）
ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID;

-- 历史行 backfill（plan-auth review H3 修复）：
-- 沿 V28 parent_id/replaced_by 链合并 — 同一 chain 内的 token 共用 root UUID；
-- 孤立行（parent_id IS NULL 且没有指向任何 chain）独立分配 UUID。
-- 这是 plan-auth §3.2 rotation chain 的契约级保证：reuse detection 必须
-- 覆盖整条 chain，而非单 row。V36 原本的「per-row gen_random_uuid()」
-- 会让历史 chain 断裂（reuse 时只能撤销单 row），已修复为递归 CTE。
WITH RECURSIVE chain_root AS (
    -- 锚点：parent_id IS NULL 的行是 chain 起点，自身即 root，
    -- 一次性为该 root 生成 family_id_value
    SELECT
        id,
        id                              AS root_id,
        gen_random_uuid()               AS family_id_value
    FROM refresh_tokens
    WHERE family_id IS NULL
      AND parent_id IS NULL

    UNION ALL

    -- 递归：parent_id 指向上游链中某行的行继承该 chain root 的 family_id_value
    SELECT
        rt.id,
        cr.root_id,
        cr.family_id_value
    FROM refresh_tokens rt
    JOIN chain_root cr ON rt.parent_id = cr.id
    WHERE rt.family_id IS NULL
)
UPDATE refresh_tokens rt
SET family_id = cr.family_id_value
FROM chain_root cr
WHERE rt.id = cr.id;

-- NOT NULL 约束
ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

COMMENT ON COLUMN refresh_tokens.family_id IS
    'v1.2 P0 步骤 5：refresh token rotation family 标识（plan-auth §3.2；replaces parent_id/replaced_by 推断）';

-- 索引：revokeFamily(userId, familyId) 高频路径
CREATE INDEX idx_refresh_tokens_user_family
    ON refresh_tokens(user_id, family_id)
    WHERE revoked_at IS NULL AND deleted_at IS NULL;

-- --------------------------------------------------
-- 3. outbox_events.event_type CHECK 对齐 EventType enum
-- --------------------------------------------------

-- 重建 CHECK：原 V33 含 auth.login/auth.logout 但缺 EventType 已有事件（daily_report.* / ai.summary.* / auth.user.* / auth.token.*）
ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_event_type_check;

ALTER TABLE outbox_events ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        -- task / habit（5）
        'task.created','task.updated','task.completed','task.reopened',
        'habit.logged',
        -- plan / milestone（5）
        'plan.created',
        'milestone.created','milestone.updated','milestone.completed','milestone.missed',
        -- daily_report / ai.summary（3）
        'daily_report.created','daily_report.updated',
        'ai.summary.generated',
        -- meal / expense / budget（3）
        'meal.created','expense.created','budget.threshold',
        -- ai（2）
        'ai.job.completed','ai.report.feedback',
        -- export（2）
        'export.completed','export.failed',
        -- notification（1）
        'notification.requested',
        -- auth canonical（4，EventType AUTH_*）
        'auth.user.registered','auth.user.logged_in',
        'auth.user.password_reset_requested','auth.token.reuse_detected',
        -- auth legacy（2，V33 已发布别名，保留以兼容历史数据）
        'auth.login','auth.logout'
    ));

COMMENT ON CONSTRAINT outbox_events_event_type_check ON outbox_events IS
    'v1.2 P0 步骤 5：27 条事件白名单（25 EventType + 2 auth legacy；业务架构 §5.5 认证授权）';