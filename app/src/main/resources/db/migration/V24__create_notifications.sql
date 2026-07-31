-- ============================================================
-- V24__create_notifications.sql
-- v1.2 P0：通知模块（notification_requests / notification_deliveries）
-- 关联：data-model-v1.2-amendment.md §3.2 通知扩展
-- 业务架构引用：technical-architecture §7.4 推送配额
-- ============================================================

-- 24.1 notification_requests —— 通知请求
CREATE TABLE notification_requests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 通知类型
    kind                TEXT NOT NULL
                        CHECK (kind IN (
                            'TASK_DUE','TASK_OVERDUE','HABIT_REMINDER',
                            'BUDGET_THRESHOLD','PLAN_MILESTONE_DUE',
                            'AI_REPORT_READY','SYSTEM'
                        )),

    -- 渠道
    channel             TEXT NOT NULL
                        CHECK (channel IN ('PUSH','EMAIL','IN_APP')),

    -- 标题/正文
    title               TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    body                TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),

    -- 关联实体
    reference_type      TEXT NULL CHECK (reference_type IS NULL OR length(reference_type) <= 32),
    reference_id        BIGINT NULL,

    -- 状态
    status              TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SENDING','SENT','FAILED','CANCELLED')),

    -- 发送策略
    scheduled_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ NULL,

    -- 失败
    retry_count         INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    error               TEXT NULL CHECK (error IS NULL OR length(error) <= 4000),

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_requests_user_status
    ON notification_requests(user_id, status, scheduled_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_notification_requests_pending
    ON notification_requests(scheduled_at)
    WHERE status = 'PENDING';

CREATE TRIGGER trg_notification_requests_set_updated_at
    BEFORE UPDATE ON notification_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE notification_requests IS 'v1.2 P0：通知请求（Outbox 消费方投射）';

-- 24.2 notification_deliveries —— 投递明细
CREATE TABLE notification_deliveries (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    notification_id     BIGINT NOT NULL REFERENCES notification_requests(id) ON DELETE CASCADE,

    -- 投递目标
    push_subscription_id BIGINT NULL REFERENCES push_subscriptions(id) ON DELETE SET NULL,

    -- 投递状态
    delivery_status     TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (delivery_status IN ('PENDING','DELIVERED','FAILED','EXPIRED','UNSUBSCRIBED')),

    -- 平台返回
    response_code       INT NULL CHECK (response_code IS NULL OR (response_code BETWEEN 100 AND 599)),
    response_body       TEXT NULL CHECK (response_body IS NULL OR length(response_body) <= 4000),

    delivered_at        TIMESTAMPTZ NULL,
    failed_at           TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_deliveries_notification
    ON notification_deliveries(notification_id);

CREATE INDEX idx_notification_deliveries_pending
    ON notification_deliveries(created_at)
    WHERE delivery_status IN ('PENDING','FAILED');

CREATE TRIGGER trg_notification_deliveries_set_updated_at
    BEFORE UPDATE ON notification_deliveries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE notification_deliveries IS 'v1.2 P0：投递明细（每订阅一次记录）';
