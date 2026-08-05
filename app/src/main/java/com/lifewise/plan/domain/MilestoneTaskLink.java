package com.lifewise.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Milestone ↔ Task 关联（V4 milestone_task_links；plan-05-plan §2.3）。
 *
 * <p>业务架构 §4.3 约束：plan → task 是跨域引用（N:M），plan 模块不能写 task，
 * 但允许在此关联表写入 task_id（task 模块自身维护 task 实体完整性）。
 *
 * <p>复合主键 {@code (milestone_id, task_id)} — 删除任务或里程碑时 ON DELETE CASCADE。
 */
@Entity
@Table(name = "milestone_task_links")
@IdClass(MilestoneTaskLink.PK.class)
public class MilestoneTaskLink {

    @Id
    @Column(name = "milestone_id", nullable = false, updatable = false)
    private Long milestoneId;

    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected MilestoneTaskLink() {
        // JPA
    }

    public MilestoneTaskLink(Long milestoneId, Long taskId, OffsetDateTime createdAt) {
        this.milestoneId = milestoneId;
        this.taskId = taskId;
        this.createdAt = createdAt;
    }

    public Long getMilestoneId() { return milestoneId; }
    public Long getTaskId() { return taskId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** 复合主键。 */
    public static class PK implements Serializable {
        private Long milestoneId;
        private Long taskId;

        public PK() {}
        public PK(Long milestoneId, Long taskId) {
            this.milestoneId = milestoneId;
            this.taskId = taskId;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(milestoneId, pk.milestoneId)
                && Objects.equals(taskId, pk.taskId);
        }

        @Override public int hashCode() {
            return Objects.hash(milestoneId, taskId);
        }
    }
}