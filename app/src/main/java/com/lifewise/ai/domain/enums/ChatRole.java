package com.lifewise.ai.domain.enums;

/**
 * 对话消息角色（V8 chat_messages.role CHECK 约束）。
 *
 * <p>不可变 append-only：SYSTEM 角色用于审计消息（plan-06-ai §6 步骤 2.5），
 * 由 DB 角色 GRANT 限制 UPDATE/DELETE 权限实现不可变。
 */
public enum ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}