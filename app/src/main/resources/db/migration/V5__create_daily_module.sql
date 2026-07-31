-- ============================================================
-- V5__create_daily_module.sql
-- 日报：daily_reports / daily_report_highlights / ai_summaries
-- 关联：data-model-design-v1.1.1 §3.4；BR-08/19/20
-- 注：daily_reports 改为按月 RANGE 分区表（V11 落地）
-- ============================================================

-- 7.1 daily_reports —— 一天一份；分表按 local_date 月份分区
CREATE TABLE daily_reports (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    local_date          DATE NOT NULL,
    timezone            TEXT NOT NULL DEFAULT 'UTC'
                        CHECK (length(timezone) BETWEEN 1 AND 64),

    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    content             TEXT NULL
                        CHECK (content IS NULL OR length(content) <= 50000),
    mood                TEXT NULL
                        CHECK (mood IS NULL OR mood IN ('GREAT','GOOD','NEUTRAL','BAD','TERRIBLE')),
    energy_score        INT NULL
                        CHECK (energy_score IS NULL OR (energy_score BETWEEN 1 AND 5)),

    -- BR-19 每日唯一（一用户一日一份）
    UNIQUE (user_id, local_date),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, local_date)
) PARTITION BY RANGE (local_date);

-- 7.2 daily_report_highlights —— 日报要点
CREATE TABLE daily_report_highlights (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    daily_report_id     BIGINT NOT NULL,
    local_date          DATE NOT NULL,

    highlight_type      TEXT NOT NULL
                        CHECK (highlight_type IN ('TASK','HABIT','MEAL','EXPENSE','MILESTONE','INSIGHT')),
    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    description         TEXT NULL CHECK (description IS NULL OR length(description) <= 2000),

    reference_type      TEXT NULL CHECK (reference_type IS NULL OR length(reference_type) <= 32),
    reference_id        BIGINT NULL,

    -- 一日最多展示 5 条（应用层兜底）
    sort_order          INT NOT NULL DEFAULT 0,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    FOREIGN KEY (daily_report_id, local_date)
        REFERENCES daily_reports(id, local_date) ON DELETE CASCADE
);

CREATE INDEX idx_highlights_report
    ON daily_report_highlights(daily_report_id, sort_order);

CREATE INDEX idx_highlights_reference
    ON daily_report_highlights(reference_type, reference_id)
    WHERE reference_id IS NOT NULL;

CREATE TRIGGER trg_highlights_set_updated_at
    BEFORE UPDATE ON daily_report_highlights
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE daily_report_highlights IS '日报要点（应用层限 5 条/日；FK 复合键跟随分区表）';

-- 7.3 ai_summaries —— 日报 AI 摘要缓存
CREATE TABLE ai_summaries (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    daily_report_id     BIGINT NULL,
    local_date          DATE NULL,

    summary_kind        TEXT NOT NULL CHECK (summary_kind IN ('DAILY','WEEKLY','PLAN','CUSTOM')),
    input_snapshot      JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary_text        TEXT NOT NULL CHECK (length(summary_text) BETWEEN 1 AND 10000),

    -- AI 报告治理
    model_name          TEXT NOT NULL CHECK (length(model_name) BETWEEN 1 AND 64),
    -- model_version 在 V25 改为 NOT NULL
    model_version       TEXT NULL,
    prompt_version      TEXT NOT NULL CHECK (length(prompt_version) BETWEEN 1 AND 32),

    -- 缓存命中（同一份 input + model_version + prompt_version 唯一）
    cache_key           TEXT NOT NULL UNIQUE CHECK (length(cache_key) BETWEEN 1 AND 200),

    tokens_used         INT NULL CHECK (tokens_used IS NULL OR tokens_used >= 0),
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_summaries_user_kind
    ON ai_summaries(user_id, summary_kind, generated_at DESC);

CREATE TRIGGER trg_ai_summaries_set_updated_at
    BEFORE UPDATE ON ai_summaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE ai_summaries IS 'AI 摘要缓存（按 cache_key 唯一；V25 改 model_version NOT NULL）';
