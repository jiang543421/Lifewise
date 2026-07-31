-- ============================================================
-- V26__create_conversations.sql
-- v1.2 P0：对话（conversations）
-- 关联：data-model-v1.2-amendment.md §2.3 conversations
-- 业务架构引用：PRD-06 §3 对话持久化
-- ============================================================

CREATE TABLE conversations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 会话主题（首条消息自动生成；用户可改名）
    title               TEXT NULL CHECK (title IS NULL OR length(title) BETWEEN 1 AND 200),

    -- 关联上下文（可选；如本会话是关于某计划复盘）
    context_type        TEXT NULL CHECK (context_type IS NULL OR length(context_type) <= 32),
    context_id          BIGINT NULL,

    -- 状态
    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','ARCHIVED','DELETED')),

    -- 计数聚合（应用层维护）
    message_count       INT NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    last_message_at     TIMESTAMPTZ NULL,

    -- 软删除
    deleted_at          TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user_status
    ON conversations(user_id, status, last_message_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_conversations_user_context
    ON conversations(user_id, context_type, context_id)
    WHERE context_id IS NOT NULL;

CREATE TRIGGER trg_conversations_set_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE conversations IS 'v1.2 P0：对话会话（应用层绑定 chat_messages.conversation_id）';
