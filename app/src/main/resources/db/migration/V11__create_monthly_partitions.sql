-- ============================================================
-- V11__create_monthly_partitions.sql
-- 5 张分区表按月 RANGE 分区（CLAUDE.md §测试要求）
-- 关联：data-model-design-v1.1.1 §4.4 分区策略
-- 涵盖：daily_reports / expenses / meals / chat_messages / outbox_events
-- 策略：默认 2024-01 ~ 2027-12 共 48 块分区；超出走 DEFAULT 分区
-- ============================================================

DO $$
DECLARE
    table_name TEXT;
    start_date DATE := DATE '2024-01-01';
    end_date   DATE := DATE '2028-01-01';
    cur_month  DATE;
    next_month DATE;
    suffix     TEXT;
BEGIN
    FOR table_name IN
        SELECT unnest(ARRAY['daily_reports','expenses','meals','chat_messages','outbox_events'])
    LOOP
        cur_month := start_date;
        WHILE cur_month < end_date LOOP
            next_month := cur_month + INTERVAL '1 month';
            suffix := to_char(cur_month, 'YYYYMM');

            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS %I_%s PARTITION OF %I
                   FOR VALUES FROM (%L) TO (%L)',
                table_name, suffix, table_name, cur_month, next_month
            );

            cur_month := next_month;
        END LOOP;

        -- DEFAULT 分区（超出范围的写入；监控路径告警）
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I_default PARTITION OF %I DEFAULT',
            table_name, table_name
        );
    END LOOP;
END $$;

-- 验证分区已建（保险；不通过则整体回滚）
DO $$
DECLARE
    t TEXT;
    cnt INT;
BEGIN
    FOR t IN SELECT unnest(ARRAY['daily_reports','expenses','meals','chat_messages','outbox_events']) LOOP
        SELECT COUNT(*) INTO cnt
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhparent
        WHERE c.relname = t;
        IF cnt < 49 THEN
            RAISE EXCEPTION '分区数不足：% 仅 % 个分区（预期 >= 49）', t, cnt;
        END IF;
    END LOOP;
END $$;

COMMENT ON TABLE daily_reports  IS '日报（按月 RANGE 分区 2024-01~2027-12 + DEFAULT）';
COMMENT ON TABLE expenses       IS '消费（按月 RANGE 分区 2024-01~2027-12 + DEFAULT）';
COMMENT ON TABLE meals          IS '餐次（按月 RANGE 分区 2024-01~2027-12 + DEFAULT）';
COMMENT ON TABLE chat_messages  IS '对话消息（按月 RANGE 分区 2024-01~2027-12 + DEFAULT）';
COMMENT ON TABLE outbox_events  IS 'Outbox（按月 RANGE 分区 2024-01~2027-12 + DEFAULT）';
