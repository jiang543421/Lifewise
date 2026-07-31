-- ============================================================
-- V23__create_export_module.sql
-- v1.2 P0：导出模块（export_requests / export_artifacts）
-- 关联：data-model-v1.2-amendment.md §3.1 导出扩展
-- 业务架构引用：technical-architecture §8.3 导出任务
-- ============================================================

-- 23.1 export_requests —— 导出请求
CREATE TABLE export_requests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 扩展 V34：6 模块白名单
    module              TEXT NOT NULL
                        CHECK (module IN (
                            'task','plan','daily','diet','expense','ai'
                        )),

    -- 导出格式
    format              TEXT NOT NULL DEFAULT 'JSON'
                        CHECK (format IN ('JSON','CSV','PDF','MARKDOWN')),

    -- 期间
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,

    -- 过滤条件（JSONB；可选）
    filters             JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 状态
    status              TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED')),

    -- 错误
    error               TEXT NULL CHECK (error IS NULL OR length(error) <= 4000),

    -- 进度
    progress_pct        INT NOT NULL DEFAULT 0
                        CHECK (progress_pct BETWEEN 0 AND 100),

    -- 调度
    scheduled_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ NULL,
    finished_at         TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CHECK (period_start <= period_end)
);

CREATE INDEX idx_export_requests_user_status
    ON export_requests(user_id, status, scheduled_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_export_requests_set_updated_at
    BEFORE UPDATE ON export_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE export_requests IS 'v1.2 P0：导出请求（V34 扩展 module 白名单至 6）';

-- 23.2 export_artifacts —— 导出产物
CREATE TABLE export_artifacts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    export_request_id   BIGINT NOT NULL REFERENCES export_requests(id) ON DELETE CASCADE,

    -- 存储路径（本地卷 / nginx 静态文件 / S3 等）
    storage_path        TEXT NOT NULL
                        CHECK (length(storage_path) BETWEEN 1 AND 2048),
    file_name           TEXT NOT NULL
                        CHECK (length(file_name) BETWEEN 1 AND 255),
    mime_type           TEXT NOT NULL
                        CHECK (length(mime_type) BETWEEN 1 AND 128),
    byte_size           BIGINT NOT NULL CHECK (byte_size >= 0),

    -- 校验
    checksum_sha256     TEXT NOT NULL
                        CHECK (length(checksum_sha256) = 64),

    -- 过期（默认 7 天）
    expires_at          TIMESTAMPTZ NOT NULL,
    downloaded_at       TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_artifacts_request
    ON export_artifacts(export_request_id);

CREATE INDEX idx_export_artifacts_expires
    ON export_artifacts(expires_at)
    WHERE downloaded_at IS NULL;

CREATE TRIGGER trg_export_artifacts_set_updated_at
    BEFORE UPDATE ON export_artifacts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE export_artifacts IS 'v1.2 P0：导出产物（指向本地卷 / cloud storage）';
