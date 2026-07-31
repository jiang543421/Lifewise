-- ============================================================
-- V4__create_plan_module.sql
-- 计划：plans / milestones / milestone_task_links
-- 关联：data-model-design-v1.1.1 §3.3；BR-14/15/29/30
-- ============================================================

-- 6.1 plans（BR-30 plans.last_activity_at 由 outbox 消费方刷新）
CREATE TABLE plans (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    description         TEXT NULL CHECK (description IS NULL OR length(description) <= 5000),

    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED','CANCELLED')),

    start_date          DATE NULL,
    target_end_date     DATE NULL,

    -- BR-30 由 Outbox 消费方更新
    last_activity_at    TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plans_user_status
    ON plans(user_id, status)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_plans_set_updated_at
    BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE plans IS '长期计划；含 BR-30 last_activity_at（Outbox 消费刷新）';

-- 6.2 milestones（含 BR-29 due_at_tz 时区快照）
CREATE TABLE milestones (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    plan_id             BIGINT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    description         TEXT NULL CHECK (description IS NULL OR length(description) <= 5000),

    status              TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','IN_PROGRESS','DONE','MISSED','CANCELLED')),

    -- BR-29 due_at + time_zone 时区快照
    due_at              TIMESTAMPTZ NULL,
    time_zone           TEXT NULL CHECK (time_zone IS NULL OR length(time_zone) BETWEEN 1 AND 64),

    completed_at        TIMESTAMPTZ NULL,

    -- 顺序
    sort_order          INT NOT NULL DEFAULT 0,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_milestones_plan_order
    ON milestones(plan_id, sort_order);

CREATE INDEX idx_milestones_user_status_due
    ON milestones(user_id, status, due_at NULLS LAST)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_milestones_set_updated_at
    BEFORE UPDATE ON milestones
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE milestones IS '里程碑（BR-29 时区快照；状态机 PENDING→IN_PROGRESS→DONE/MISSED）';

-- 6.3 milestone_task_links
CREATE TABLE milestone_task_links (
    milestone_id        BIGINT NOT NULL REFERENCES milestones(id) ON DELETE CASCADE,
    task_id             BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (milestone_id, task_id)
);

CREATE INDEX idx_milestone_task_links_task ON milestone_task_links(task_id);

COMMENT ON TABLE milestone_task_links IS '里程碑↔任务 N:M（业务架构 §4.3 plan→task 跨域引用）';
