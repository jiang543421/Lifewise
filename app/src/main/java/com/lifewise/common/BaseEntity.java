package com.lifewise.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.OffsetDateTime;

/**
 * 业务实体基类（不可变 createdAt / updatedAt + 软删除字段）。
 *
 * <p>所有 6 模块的 JPA 实体继承本类即可获得：
 * <ul>
 *   <li>{@code id} — BIGINT IDENTITY 主键（CLAUDE.md §0）</li>
 *   <li>{@code createdAt} — 创建时间，INSERT 时由 {@link AuditListener} 写入</li>
 *   <li>{@code updatedAt} — 更新时间，INSERT 和 UPDATE 时由监听器维护</li>
 *   <li>{@code deletedAt} — 软删除标记（CLAUDE.md §不变量 5）</li>
 * </ul>
 *
 * <p>模块边界（CLAUDE.md §1.2 / business-architecture §4）：跨模块只允许通过
 * 对方模块的 Service / Repository 引用 ID，不允许直接修改对方实体。
 */
@MappedSuperclass
@EntityListeners(AuditListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected BaseEntity() {
        // JPA no-arg constructor
    }

    // ----------- Package-private setters for AuditListener (JPA only) -----------

    void setCreatedAtInternal(OffsetDateTime when) {
        this.createdAt = when;
    }

    void setUpdatedAtInternal(OffsetDateTime when) {
        this.updatedAt = when;
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    /** 软删除：标记 deletedAt = now（保留 30 天） */
    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    /** 复活：清空 deletedAt */
    public void restore() {
        this.deletedAt = null;
    }

    /** 是否已软删除 */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
