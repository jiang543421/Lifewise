package com.lifewise.plan.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Plan 实体（V4 plans DDL + business-architecture §6 + plan-05-plan §4.1）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-15：endDate &gt;= startDate（service 层校验）</li>
 *   <li>BR-30：last_activity_at 由 outbox 消费方刷新（{@link #touchActivity}）</li>
 * </ul>
 */
@Entity
@Table(name = "plans")
public class Plan extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    /** 业务类型标签（STUDY / WORK / HEALTH 等）；不做枚举，保留扩展。 */
    @Column(name = "type", nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_end_date")
    private LocalDate targetEndDate;

    /** BR-30 由 outbox 消费方更新；不通过 service 直写。 */
    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    protected Plan() {
        // JPA
    }

    private Plan(Long userId, String title, String description, String type,
                 LocalDate startDate, LocalDate targetEndDate) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.type = type;
        this.startDate = startDate;
        this.targetEndDate = targetEndDate;
        this.status = PlanStatus.ACTIVE;
    }

    /**
     * 工厂：新建 Plan，默认状态 ACTIVE。
     *
     * @param type  业务类型标签（STUDY / WORK / HEALTH 等）
     * @throws IllegalArgumentException 标题空 / start 在 end 之后
     */
    public static Plan create(Long userId, String title, String description, String type,
                              LocalDate startDate, LocalDate targetEndDate) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title required");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title must be <= 200 chars");
        }
        if (description != null && description.length() > 5000) {
            throw new IllegalArgumentException("description must be <= 5000 chars");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type required");
        }
        if (startDate != null && targetEndDate != null
                && targetEndDate.isBefore(startDate)) {
            throw new com.lifewise.plan.service.exception.EndBeforeStartException(
                    startDate, targetEndDate);
        }
        return new Plan(userId, title.trim(), description, type.trim(),
                startDate, targetEndDate);
    }

    /** 应用层更新：仅更新可变字段；status 保留 ACTIVE。 */
    public void applyUpdate(String title, String description, String type,
                            LocalDate startDate, LocalDate targetEndDate) {
        if (title != null && !title.isBlank()) {
            if (title.length() > 200) {
                throw new IllegalArgumentException("title must be <= 200 chars");
            }
            this.title = title.trim();
        }
        if (description != null) {
            if (description.length() > 5000) {
                throw new IllegalArgumentException("description must be <= 5000 chars");
            }
            this.description = description;
        }
        if (type != null && !type.isBlank()) {
            this.type = type.trim();
        }
        if (startDate != null && targetEndDate != null
                && targetEndDate.isBefore(startDate)) {
            throw new com.lifewise.plan.service.exception.EndBeforeStartException(
                    startDate, targetEndDate);
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (targetEndDate != null) {
            this.targetEndDate = targetEndDate;
        }
    }

    /** BR-30 last_activity_at 由 outbox 消费方刷新。 */
    public void touchActivity(OffsetDateTime when) {
        this.lastActivityAt = when;
    }

    /** 用户主动放弃：状态切换为 CANCELLED（区别于 ARCHIVED）。 */
    public void abandon() {
        this.status = PlanStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return status == PlanStatus.CANCELLED;
    }

    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
    }

    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public PlanStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getTargetEndDate() { return targetEndDate; }
    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
}