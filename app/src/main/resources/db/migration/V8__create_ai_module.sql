-- ============================================================
-- V8__create_ai_module.sql
-- AI：ai_jobs / ai_reports / chat_messages / chat_feedbacks
-- 关联：data-model-design-v1.1.1 §3.7；BR-21/24/25
-- 注：chat_messages 改为按月 RANGE 分区表（V11 落地）
-- ============================================================

-- 10.1 ai_jobs —— 异步 AI 任务
CREATE TABLE ai_jobs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    job_type            TEXT NOT NULL
                        CHECK (job_type IN (
                            'DAILY_SUMMARY','WEEKLY_SUMMARY','PLAN_REVIEW',
                            'HABIT_ANALYSIS','MEAL_ANALYSIS','EXPENSE_ANALYSIS',
                            'CUSTOM_PROMPT'
                        )),

    -- 状态（V31 扩展含 DONE_PARTIAL / DONE_NO_LLM）
    status              TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN (
                            'PENDING','RUNNING','DONE','DONE_PARTIAL','DONE_NO_LLM',
                            'FAILED','CANCELLED'
                        )),

    -- 输入/输出（JSONB 灵活）
    input               JSONB NOT NULL DEFAULT '{}'::jsonb,
    output              JSONB NULL,

    -- 关联业务实体（可选）
    reference_type      TEXT NULL CHECK (reference_type IS NULL OR length(reference_type) <= 32),
    reference_id        BIGINT NULL,

    -- 队列调度
    priority            INT NOT NULL DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
    scheduled_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ NULL,
    finished_at         TIMESTAMPTZ NULL,

    -- AI 治理
    model_name          TEXT NULL CHECK (model_name IS NULL OR length(model_name) <= 64),
    model_version       TEXT NULL CHECK (model_version IS NULL OR length(model_version) <= 32),
    tokens_used         INT NULL CHECK (tokens_used IS NULL OR tokens_used >= 0),

    -- 失败详情
    error               TEXT NULL CHECK (error IS NULL OR length(error) <= 4000),
    retry_count         INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_jobs_user_status
    ON ai_jobs(user_id, status, scheduled_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_ai_jobs_pending
    ON ai_jobs(priority DESC, scheduled_at)
    WHERE status IN ('PENDING','RUNNING');

CREATE TRIGGER trg_ai_jobs_set_updated_at
    BEFORE UPDATE ON ai_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE ai_jobs IS 'AI 任务（V31 状态扩展；BR-21 限流 10次/分钟在应用层）';

-- 10.2 ai_reports —— AI 输出报告
CREATE TABLE ai_reports (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    job_id              BIGINT NOT NULL REFERENCES ai_jobs(id) ON DELETE CASCADE,

    report_kind         TEXT NOT NULL
                        CHECK (report_kind IN ('DAILY','WEEKLY','PLAN','HABIT','MEAL','EXPENSE','CUSTOM')),
    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),

    -- 渲染存储（HTML 或 Markdown）
    content_format      TEXT NOT NULL DEFAULT 'MARKDOWN'
                        CHECK (content_format IN ('MARKDOWN','HTML')),
    content             TEXT NOT NULL CHECK (length(content) BETWEEN 1 AND 100000),

    -- 报告元数据
    period_start        DATE NULL,
    period_end          DATE NULL,

    -- 反馈聚合（应用层维护）
    feedback_count      INT NOT NULL DEFAULT 0 CHECK (feedback_count >= 0),
    helpful_count       INT NOT NULL DEFAULT 0 CHECK (helpful_count >= 0),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_reports_user_created
    ON ai_reports(user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_ai_reports_set_updated_at
    BEFORE UPDATE ON ai_reports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE ai_reports IS 'AI 报告表（应用层反馈计数由 chat_feedbacks 聚合）';

-- 10.3 chat_messages —— 对话消息；按月 RANGE 分区
CREATE TABLE chat_messages (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- V25 修订：conversation_id 改为可空（单条 ad-hoc 问询允许）
    conversation_id     BIGINT NULL,
    local_date          DATE NOT NULL,

    role                TEXT NOT NULL CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
    content             TEXT NOT NULL CHECK (length(content) BETWEEN 1 AND 50000),

    -- 引用片段（可选）
    message_refs        JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 关联 AI 任务
    job_id              BIGINT NULL REFERENCES ai_jobs(id) ON DELETE SET NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, local_date)
) PARTITION BY RANGE (local_date);

-- 10.4 chat_feedbacks —— 用户反馈
CREATE TABLE chat_feedbacks (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    message_id          BIGINT NOT NULL,
    local_date          DATE NOT NULL,

    -- 反馈类型
    feedback_type       TEXT NOT NULL
                        CHECK (feedback_type IN ('HELPFUL','NOT_HELPFUL','INCORRECT','OFFENSIVE')),
    comment             TEXT NULL CHECK (comment IS NULL OR length(comment) <= 2000),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- BR-24 同 message 仅一次有效反馈
    UNIQUE (message_id, local_date, user_id),

    FOREIGN KEY (message_id, local_date)
        REFERENCES chat_messages(id, local_date) ON DELETE CASCADE
);

CREATE INDEX idx_chat_feedbacks_user
    ON chat_feedbacks(user_id, created_at DESC);

CREATE TRIGGER trg_chat_feedbacks_set_updated_at
    BEFORE UPDATE ON chat_feedbacks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE chat_feedbacks IS '用户反馈（BR-24 同 message 唯一一条）';
