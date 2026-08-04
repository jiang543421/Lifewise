-- ============================================================
-- V42__add_chat_messages_message_metadata.sql
-- 补 chat_messages 缺 message_metadata JSONB 列
-- 关联：plan-06-ai.md §3 数据模型；BR-19/22 审计结构化载荷
-- review 来源：code-review Finding #4（reviewer 新发现，V8 DDL 与 plan §3 不一致）
-- 业务架构引用：business-architecture §6.7 流程 7 audit row 应带 trace_id/decision_type/latency_ms/tokens_used
-- ============================================================

-- 兼容分区表：父表 + 现有子表都要 ALTER（PG 11+ 分区表 ADD COLUMN 自动传播子表，
-- 但显式注释避免误读）
ALTER TABLE chat_messages ADD COLUMN message_metadata JSONB NULL;

-- message_refs 与 message_metadata 关系：
--   message_refs       = 引用片段（命中哪些源 aggregateId；UI 卡片可点）
--   message_metadata   = 系统审计上下文（trace_id、decision_type、latency_ms、tokens_used）

COMMENT ON COLUMN chat_messages.message_metadata IS
    'v1.2 P0：BR-19/22 审计结构化载荷（role=SYSTEM 行必填，含 trace_id/decision_type/latency_ms/tokens_used）';