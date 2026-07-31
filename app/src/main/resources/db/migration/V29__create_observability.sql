-- ============================================================
-- V29__create_observability.sql
-- v1.2 P0：可观测性（scheduled_jobs / backup_manifests）
-- 关联：business-architecture §6 监控；technical-architecture §9 Actuator
-- 业务架构引用：deploy/backup/ ；docker-compose 每日 03:00 备份
-- ============================================================

-- 29.1 scheduled_jobs —— 定时任务清单
CREATE TABLE scheduled_jobs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 任务唯一名（如 'outbox.flush','export.cleanup','backup.daily'）
    job_name            TEXT NOT NULL UNIQUE CHECK (length(job_name) BETWEEN 1 AND 100),

    -- 任务对应用类（Spring @Scheduled 名）
    bean_name           TEXT NOT NULL CHECK (length(bean_name) BETWEEN 1 AND 100),
    method_name         TEXT NOT NULL CHECK (length(method_name) BETWEEN 1 AND 100),

    -- Cron 表达式
    cron_expression     TEXT NOT NULL CHECK (length(cron_expression) BETWEEN 1 AND 100),

    -- 启用 / 暂停
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    -- 时区
    time_zone           TEXT NOT NULL DEFAULT 'UTC'
                        CHECK (length(time_zone) BETWEEN 1 AND 64),

    -- 描述
    description         TEXT NULL CHECK (description IS NULL OR length(description) <= 1000),

    -- 最近一次执行引用
    last_run_id         BIGINT NULL REFERENCES job_runs(id) ON DELETE SET NULL,
    last_run_at         TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_jobs_enabled
    ON scheduled_jobs(job_name) WHERE enabled = TRUE AND deleted_at IS NULL;

CREATE TRIGGER trg_scheduled_jobs_set_updated_at
    BEFORE UPDATE ON scheduled_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE scheduled_jobs IS 'v1.2 P0：定时任务清单（应用启动时同步）';

-- 29.2 backup_manifests —— 备份清单
CREATE TABLE backup_manifests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 备份类型
    backup_type         TEXT NOT NULL
                        CHECK (backup_type IN ('FULL','INCREMENTAL','MANUAL','RESTORE_TEST')),

    -- 文件信息
    file_name           TEXT NOT NULL
                        CHECK (length(file_name) BETWEEN 1 AND 255),
    storage_path        TEXT NOT NULL
                        CHECK (length(storage_path) BETWEEN 1 AND 2048),
    byte_size           BIGINT NOT NULL CHECK (byte_size >= 0),
    checksum_sha256     TEXT NOT NULL CHECK (length(checksum_sha256) = 64),

    -- 备份范围
    schema_version      TEXT NOT NULL CHECK (length(schema_version) BETWEEN 1 AND 32),
    contains_tables     JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 时间
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ NOT NULL,
    duration_ms         INT NOT NULL CHECK (duration_ms >= 0),

    -- 状态
    status              TEXT NOT NULL DEFAULT 'SUCCESS'
                        CHECK (status IN ('SUCCESS','FAILED','PARTIAL','VERIFIED')),

    -- 验证
    verified_at         TIMESTAMPTZ NULL,
    verified_status     TEXT NULL
                        CHECK (verified_status IS NULL OR verified_status IN ('PASS','FAIL','WARN')),

    -- 备注
    note                TEXT NULL CHECK (note IS NULL OR length(note) <= 4000),

    triggered_by        TEXT NULL CHECK (triggered_by IS NULL OR length(triggered_by) <= 64),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_backup_manifests_started
    ON backup_manifests(started_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_backup_manifests_status
    ON backup_manifests(status, started_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_backup_manifests_set_updated_at
    BEFORE UPDATE ON backup_manifests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE backup_manifests IS 'v1.2 P0：每日 03:00 pg_dump 备份清单（CLAUDE.md §1.4 灾备）';
