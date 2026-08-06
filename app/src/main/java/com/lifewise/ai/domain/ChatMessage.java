package com.lifewise.ai.domain;

import com.lifewise.ai.domain.enums.ChatRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 对话消息（V8 chat_messages DDL — 按月分区）。
 *
 * <p>BR-26（BR-19/22 通过 DB GRANT）：role=SYSTEM 审计消息不可变（应用层 INSERT only，
 * DB 角色限制 UPDATE/DELETE 权限）。
 *
 * <p>复合主键：(id, local_date) — 与 V8 分区表对齐。
 */
@Entity
@Table(name = "chat_messages")
@IdClass(ChatMessage.ChatMessageId.class)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** V25 nullable：单条 ad-hoc 问询允许不归属会话。 */
    @Column(name = "conversation_id")
    private Long conversationId;

    @Id
    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ChatRole role;

    @Column(name = "content", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_refs", nullable = false, columnDefinition = "jsonb")
    private String messageRefsJson = "[]";

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ChatMessage() {
        // JPA
    }

    private ChatMessage(Long userId, LocalDate localDate, ChatRole role, String content) {
        this.userId = userId;
        this.localDate = localDate;
        this.role = role;
        this.content = content;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ChatMessage userMessage(Long userId, LocalDate localDate, String content) {
        validate(userId, content);
        return new ChatMessage(userId, localDate, ChatRole.USER, content);
    }

    public static ChatMessage assistantMessage(Long userId, LocalDate localDate, String content) {
        validate(userId, content);
        return new ChatMessage(userId, localDate, ChatRole.ASSISTANT, content);
    }

    /**
     * 审计消息（plan §6 步骤 2.5 + §7.7）：role=SYSTEM 写 chat_messages 留痕，
     * DB 角色 GRANT UPDATE/DELETE 拒绝保证不可变。
     */
    public static ChatMessage auditMessage(Long userId, LocalDate localDate, String content) {
        validate(userId, content);
        return new ChatMessage(userId, localDate, ChatRole.SYSTEM, content);
    }

    private static void validate(Long userId, String content) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        if (content.length() > 50_000) {
            throw new IllegalArgumentException("content exceeds 50000 chars");
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getConversationId() { return conversationId; }
    public LocalDate getLocalDate() { return localDate; }
    public ChatRole getRole() { return role; }
    public String getContent() { return content; }
    public String getMessageRefsJson() { return messageRefsJson; }
    public Long getJobId() { return jobId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }

    /** 复合主键（与 V8 chat_messages 一致）。 */
    public static class ChatMessageId implements Serializable {
        private Long id;
        private LocalDate localDate;

        public ChatMessageId() {}
        public ChatMessageId(Long id, LocalDate localDate) {
            this.id = id;
            this.localDate = localDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChatMessageId that)) return false;
            return Objects.equals(id, that.id) && Objects.equals(localDate, that.localDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, localDate);
        }
    }
}