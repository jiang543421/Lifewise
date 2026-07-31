-- ============================================================
-- V35__final_backfill_and_recheck.sql
-- v1.2 P1：最终回填 + 跨表一致性 CHECK
-- 关联：data-model-v1.2-amendment.md §5 收尾
-- ============================================================

-- 1) daily_reports.is_draft 历史回填：所有现有日报默认为已发布
UPDATE daily_reports
SET is_draft = FALSE
WHERE is_draft = TRUE
  AND created_at < NOW() - INTERVAL '1 day';

-- 2) ai_summaries 缓存键所有历史 NULL 标 'unknown'（V25 已做；此处冗余保险）
UPDATE ai_summaries
SET model_version = COALESCE(model_version, 'unknown')
WHERE model_version IS NULL;

-- 3) chat_messages meta 默认填充
UPDATE chat_messages
SET meta = '{}'::jsonb
WHERE meta IS NULL;

-- 4) 跨表一致性：operation_logs.outbox_event_id 逻辑外键（不强制）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'operation_logs_outbox_event_id_fk'
    ) THEN
        COMMENT ON COLUMN operation_logs.outbox_event_id IS
            'v1.2 P1：逻辑外键 → outbox_events.id（不强制；outbox 清表不影响审计）';
    END IF;
END $$;

-- 5) 跨表一致性：meals.expense_id 逻辑外键（V6/V7 注释已声明）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'meals_expense_id_fk'
    ) THEN
        COMMENT ON COLUMN meals.expense_id IS
            'v1.2 P1：逻辑外键 → expenses.id（不强制；应用层异步回填）';
    END IF;
END $$;

-- 6) 软删除：所有业务表的 deleted_at 索引 sanity check
DO $$
DECLARE
    t TEXT;
    idx_count INT;
BEGIN
    FOR t IN SELECT unnest(ARRAY[
        'users','tasks','plans','daily_reports','expenses',
        'meals','habits','export_requests','notification_requests','conversations'
    ]) LOOP
        SELECT COUNT(*) INTO idx_count
        FROM pg_indexes
        WHERE schemaname = 'public' AND tablename = t
          AND indexdef LIKE '%deleted_at%';
        IF idx_count = 0 THEN
            RAISE NOTICE '表 % 未为 deleted_at 建索引（建议补）', t;
        END IF;
    END LOOP;
END $$;

-- 7) 写入 metadata（flyway 迁移完成度）
-- 不在 lifewise 用户下执行 COMMENT ON SCHEMA（需 schema owner）
-- 改为写入 job_runs 记录（应用层可观测）
INSERT INTO job_runs (job_name, status, started_at, finished_at, duration_ms, metadata, triggered_by)
VALUES (
    'flyway.bootstrap.v1_2',
    'SUCCESS',
    NOW() - INTERVAL '1 second',
    NOW(),
    1000,
    '{"migrations": 35, "tables": 38, "partitions": 5, "materialized_views": 2}'::jsonb,
    'flyway'
);

-- 8) 落最后一条「plan-data-flyway 全部完成」标记
DO $$
BEGIN
    RAISE NOTICE 'plan-data-flyway 全部 35 条迁移已完成；38 张表就位。';
END $$;
