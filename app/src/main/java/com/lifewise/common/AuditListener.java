package com.lifewise.common;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.OffsetDateTime;

/**
 * 审计监听器：自动维护 createdAt / updatedAt。
 *
 * <p>监听 {@link BaseEntity}，在 INSERT 和 UPDATE 前回调：
 * <ul>
 *   <li>INSERT：createdAt = updatedAt = now()</li>
 *   <li>UPDATE：updatedAt = now()</li>
 * </ul>
 *
 * <p>PostgreSQL 端亦有 {@code set_updated_at()} 触发器做兜底（V1）；任何
 * 直接绕过 Hibernate 的写路径（如 psql / 批量 ETL）仍能保证时间戳正确。
 */
public class AuditListener {

    @PrePersist
    public void onPrePersist(BaseEntity entity) {
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAtInternal(now);
        }
        entity.setUpdatedAtInternal(now);
    }

    @PreUpdate
    public void onPreUpdate(BaseEntity entity) {
        entity.setUpdatedAtInternal(OffsetDateTime.now());
    }
}
