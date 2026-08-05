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
 * Milestone 实体（V4 milestones DDL + BR-14 / BR-29）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-14：状态机 PENDING → IN_PROGRESS → DONE / MISSED；DONE 仅可 reopen 回 PENDING</li>
 *   <li>BR-29：due_at + time_zone 时区快照，避免后期时区变更影响原计划</li>
 * </ul>
 *
 * <p>不在模块边界外暴露 {@code applyUpdate}，由 {@link com.lifewise.plan.service.MilestoneService}
 * 统一编排。
 */
@Entity
@Table(name = "milestones")
public class Milestone extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MilestoneStatus status;

    /** BR-29 due_at + time_zone 时区快照。 */
    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "time_zone")
    private String timeZone;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected Milestone() {
        // JPA
    }

    private Milestone(Long planId, Long userId, String title, String description,
                      OffsetDateTime dueAt, String timeZone, Integer sortOrder) {
        this.planId = planId;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.timeZone = timeZone;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.status = MilestoneStatus.PENDING;
    }

    /**
     * 工厂：新建 Milestone，默认状态 PENDING。
     *
     * @param dueAt BR-29：必须 + 时区快照一起传入；若传 null 则视为无截止
     * @param timeZone BR-29：IANA 时区名（如 {@code Asia/Shanghai}）；传 null 时默认 UTC
     */
    public static Milestone create(Long planId, Long userId, String title, String description,
                                   OffsetDateTime dueAt, String timeZone, Integer sortOrder) {
        if (planId == null) {
            throw new IllegalArgumentException("planId required");
        }
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
        if (timeZone != null && timeZone.length() > 64) {
            throw new IllegalArgumentException("timeZone must be <= 64 chars");
        }
        return new Milestone(planId, userId, title.trim(), description,
                dueAt, timeZone == null ? "UTC" : timeZone, sortOrder);
    }

    /**
     * BR-14：标记完成。已 DONE 抛 {@link com.lifewise.plan.service.exception.MilestoneAlreadyDoneException}。
     */
    public void complete(OffsetDateTime when) {
        if (status == MilestoneStatus.DONE) {
            throw new com.lifewise.plan.service.exception.MilestoneAlreadyDoneException(getId());
        }
        this.status = MilestoneStatus.DONE;
        this.completedAt = when;
    }

    /**
     * BR-14：从 DONE 状态 reopen 回 PENDING。仅 DONE 可 reopen。
     */
    public void reopen() {
        if (status != MilestoneStatus.DONE) {
            throw new com.lifewise.plan.service.exception.MilestoneNotDoneException(getId());
        }
        this.status = MilestoneStatus.PENDING;
        this.completedAt = null;
    }

    /** 定时任务 sweep 调用：标记过期未完成为 MISSED。 */
    public void markMissed() {
        if (status == MilestoneStatus.DONE || status == MilestoneStatus.CANCELLED) {
            return;
        }
        this.status = MilestoneStatus.MISSED;
    }

    /** 取消（用户主动）。 */
    public void cancel() {
        this.status = MilestoneStatus.CANCELLED;
    }

    /**
     * BR-14：done-only-readonly。已 DONE 的里程碑不允许修改 title/due_at/sort_order 等。
     */
    public void applyUpdate(String title, String description, OffsetDateTime dueAt,
                            String timeZone, Integer sortOrder) {
        if (status == MilestoneStatus.DONE) {
            throw new com.lifewise.plan.service.exception.MilestoneDoneReadOnlyException(getId());
        }
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
        if (dueAt != null) {
            this.dueAt = dueAt;
        }
        if (timeZone != null) {
            if (timeZone.length() > 64) {
                throw new IllegalArgumentException("timeZone must be <= 64 chars");
            }
            this.timeZone = timeZone;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    public Long getPlanId() { return planId; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public MilestoneStatus getStatus() { return status; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public String getTimeZone() { return timeZone; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Integer getSortOrder() { return sortOrder; }
}