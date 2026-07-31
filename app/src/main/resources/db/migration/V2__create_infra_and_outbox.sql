-- ============================================================
-- V2__create_infra_and_outbox.sql
-- 公共基础设施：user_profiles + push_subscriptions + outbox_events + job_runs
-- 关联：data-model-design-v1.1.1 §3.1.2~3.1.5；BR-22 outbox.user_id NOT NULL
-- ============================================================

-- 4.1 user_profiles —— 用户级偏好与同意（1:1 延展；与 users 共用主键）
CREATE TABLE user_profiles (
    user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

    locale                  TEXT NOT NULL DEFAULT 'zh-CN'
                            CHECK (length(locale) BETWEEN 2 AND 16),
    avatar_url              TEXT NULL
                            CHECK (avatar_url IS NULL OR length(avatar_url) BETWEEN 1 AND 2048),
    bio                     TEXT NULL
                            CHECK (bio IS NULL OR length(bio) <= 2000),

    -- AI 同意（PRD-06 §7 隐私）
    ai_consent              BOOLEAN NOT NULL DEFAULT FALSE,
    ai_consent_at           TIMESTAMPTZ NULL,
    ai_interpretation_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- 通知偏好
    push_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    email_digest_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    notify_muted_until      TIMESTAMPTZ NULL,

    -- 营养目标（H-3 单字段化）
    daily_kcal_target       INT NULL
                            CHECK (daily_kcal_target IS NULL OR (daily_kcal_target BETWEEN 500 AND 10000)),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_user_profiles_set_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE user_profiles IS '用户偏好 1:1 延展表（共用 user_id 主键；含 AI/通知同意、营养目标）';

-- 4.2 push_subscriptions
CREATE TABLE push_subscriptions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    endpoint            TEXT NOT NULL UNIQUE
                        CHECK (length(endpoint) BETWEEN 1 AND 2048),
    p256dh              TEXT NOT NULL CHECK (length(p256dh) BETWEEN 1 AND 200),
    auth                TEXT NOT NULL CHECK (length(auth) BETWEEN 1 AND 50),

    user_agent          TEXT NULL
                        CHECK (user_agent IS NULL OR length(user_agent) <= 512),

    last_used_at        TIMESTAMPTZ NULL,
    expires_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_subscriptions_user
    ON push_subscriptions(user_id);

CREATE TRIGGER trg_push_subscriptions_set_updated_at
    BEFORE UPDATE ON push_subscriptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE push_subscriptions IS 'Web Push 多设备订阅（endpoint 全局唯一）';

-- 4.3 outbox_events（事务性 Outbox；BR-22 user_id NOT NULL + 部分索引）
-- V11 改为按 occurred_at 月 RANGE 分区（local_date 切分时间字段）
CREATE TABLE outbox_events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,

    -- 租户与归属（BR-22 user_id NOT NULL）
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 事件契约（业务架构 §5.3）
    aggregate_type      TEXT NOT NULL CHECK (length(aggregate_type) BETWEEN 1 AND 64),
    aggregate_id        BIGINT NOT NULL,

    -- payload 由 INSERT 时显式赋值，类型 JSONB
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- V2 阶段暂留：event_type 完整白名单由 V23/V33 落地
    event_type          TEXT NOT NULL
                        CHECK (event_type IN (
                            'task.completed','task.reopened','task.created','task.updated',
                            'milestone.created','milestone.updated','milestone.completed','milestone.missed',
                            'habit.logged','meal.created','expense.created','budget.threshold',
                            'plan.created','ai.job.completed','ai.report.feedback'
                        )),

    -- 分区键：V11 月分区
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ NULL,

    -- 投递去重（BR-16 同 aggregate + 同事件类型 唯一）
    UNIQUE (aggregate_type, aggregate_id, event_type, occurred_at),

    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- 待发布扫描索引（未发布的高频路径）
CREATE INDEX idx_outbox_pending
    ON outbox_events(occurred_at)
    WHERE published_at IS NULL;

-- BR-22：user_id NOT NULL + 部分索引（在 delivery / 监控路径热点）
CREATE INDEX idx_outbox_user_pending
    ON outbox_events(user_id, occurred_at DESC)
    WHERE published_at IS NULL;

COMMENT ON TABLE  outbox_events IS '事务性 Outbox；同事务写入；进程内 / OutboxWorker 1s 轮询投递（BR-16/22）';
COMMENT ON COLUMN outbox_events.event_type IS '业务架构 §5.3 事件枚举（V33 扩展至 25 条）';

-- 4.4 job_runs — 异步 Job 运行记录
CREATE TABLE job_runs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    job_name            TEXT NOT NULL CHECK (length(job_name) BETWEEN 1 AND 100),
    status              TEXT NOT NULL DEFAULT 'RUNNING'
                        CHECK (status IN ('RUNNING','SUCCESS','FAILED')),

    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at         TIMESTAMPTZ NULL,
    duration_ms         INT NULL CHECK (duration_ms IS NULL OR duration_ms >= 0),

    error               TEXT NULL
                        CHECK (error IS NULL OR length(error) <= 4000),

    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    triggered_by        TEXT NULL CHECK (triggered_by IS NULL OR length(triggered_by) <= 64)
);

CREATE INDEX idx_job_runs_name_started
    ON job_runs(job_name, started_at DESC);

COMMENT ON TABLE job_runs IS '异步 Job 运行记录（OutboxWorker / 日终 Job 写入）';
