-- ============================================================
-- V3__create_task_module.sql
-- 任务与习惯：tasks / task_tags / task_tag_links / habits / habit_logs
-- 关联：data-model-design-v1.1.1 §3.2；BR-01/02/04/05/27
-- ============================================================

-- 5.1 tasks（自循环 parent_id，BR-27 子任务最多一层）
CREATE TABLE tasks (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title               TEXT NOT NULL
                        CHECK (length(title) BETWEEN 1 AND 200),
    description         TEXT NULL
                        CHECK (description IS NULL OR length(description) <= 10000),
    status              TEXT NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','IN_PROGRESS','DONE','CANCELLED')),

    priority            TEXT NOT NULL DEFAULT 'NORMAL'
                        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),

    due_at              TIMESTAMPTZ NULL,
    completed_at        TIMESTAMPTZ NULL,

    -- 自循环子任务（最多一层）
    parent_id           BIGINT NULL REFERENCES tasks(id) ON DELETE CASCADE,

    -- 软删除（CLAUDE.md §不变量 5）
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_user_status_due
    ON tasks(user_id, status, due_at NULLS LAST)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tasks_parent
    ON tasks(parent_id) WHERE parent_id IS NOT NULL;

-- BR-27 自循环禁止
ALTER TABLE tasks
    ADD CONSTRAINT tasks_parent_not_self
    CHECK (parent_id IS NULL OR parent_id <> id);

CREATE TRIGGER trg_tasks_set_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE tasks IS '一次性任务（BR-01/02/04/27；自循环禁止）';

-- 5.2 task_tags
CREATE TABLE task_tags (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 50),
    color               TEXT NULL CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (user_id, name)
);

CREATE INDEX idx_task_tags_user ON task_tags(user_id);

CREATE TRIGGER trg_task_tags_set_updated_at
    BEFORE UPDATE ON task_tags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE task_tags IS '用户私有标签（每任务最多 5 个 — 应用层约束）';

-- 5.3 task_tag_links
CREATE TABLE task_tag_links (
    task_id             BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id              BIGINT NOT NULL REFERENCES task_tags(id) ON DELETE CASCADE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (task_id, tag_id)
);

CREATE INDEX idx_task_tag_links_tag ON task_tag_links(tag_id);

COMMENT ON TABLE task_tag_links IS '任务↔标签 N:M';

-- 5.4 habits
CREATE TABLE habits (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    description         TEXT NULL CHECK (description IS NULL OR length(description) <= 2000),

    frequency           TEXT NOT NULL DEFAULT 'DAILY'
                        CHECK (frequency IN ('DAILY','WEEKLY')),
    target_per_period   INT NOT NULL DEFAULT 1
                        CHECK (target_per_period BETWEEN 1 AND 7),

    -- 习惯归档
    is_archived         BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at         TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_habits_user_active
    ON habits(user_id) WHERE is_archived = FALSE AND deleted_at IS NULL;

CREATE TRIGGER trg_habits_set_updated_at
    BEFORE UPDATE ON habits
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE habits IS '习惯定义（频率 DAILY/WEEKLY；目标次数 1~7）';

-- 5.5 habit_logs（BR-01 habit_id+local_date UNIQUE）
CREATE TABLE habit_logs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    habit_id            BIGINT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    local_date          DATE NOT NULL,
    logged_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 补卡来源（应用层写入；区分普通打卡 vs 补卡）
    source              TEXT NOT NULL DEFAULT 'NORMAL'
                        CHECK (source IN ('NORMAL','BACKFILL')),

    note                TEXT NULL CHECK (note IS NULL OR length(note) <= 1000),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- BR-01 habitId + localDate 唯一
    UNIQUE (habit_id, local_date)
);

CREATE INDEX idx_habit_logs_user_logged
    ON habit_logs(user_id, logged_at DESC);

COMMENT ON TABLE habit_logs IS '习惯打卡记录（BR-01 唯一性 + 应用层补卡窗口校验）';
