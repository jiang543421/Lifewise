package com.lifewise.task.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 任务实体（plan-01-task §3 + V3 DDL tasks 表）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-01：title length 1~200</li>
 *   <li>BR-02：description ≤ 10000</li>
 *   <li>BR-27：parent_id IS NULL OR parent_id &lt;&gt; id（DB CHECK）</li>
 * </ul>
 *
 * <p>自循环子任务最多一层（V3 注释），应用层校验。
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 10000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TaskPriority priority;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "parent_id")
    private Long parentId;

    protected Task() {
        // JPA
    }

    private Task(Long userId, String title, String description,
                 TaskStatus status, TaskPriority priority,
                 OffsetDateTime dueAt, Long parentId) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dueAt = dueAt;
        this.parentId = parentId;
    }

    /** 工厂方法：创建 OPEN 任务（应用层默认状态）。 */
    public static Task create(Long userId, String title, String description,
                              TaskPriority priority, OffsetDateTime dueAt, Long parentId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (priority == null) {
            priority = TaskPriority.NORMAL;
        }
        return new Task(userId, title.trim(), description,
                TaskStatus.OPEN, priority, dueAt, parentId);
    }

    /** 标记为完成：状态置 DONE + 写入 completedAt。 */
    public void markCompleted(OffsetDateTime when) {
        if (status == TaskStatus.DONE) {
            throw new IllegalStateException("task already DONE");
        }
        this.status = TaskStatus.DONE;
        this.completedAt = when;
    }

    /** 重新打开：状态置 OPEN + 清空 completedAt。 */
    public void reopen() {
        if (status == TaskStatus.OPEN) {
            throw new IllegalStateException("task already OPEN");
        }
        this.status = TaskStatus.OPEN;
        this.completedAt = null;
    }

    /** 应用层更新字段（title / description / priority / dueAt）。 */
    public void applyUpdate(String title, String description,
                            TaskPriority priority, OffsetDateTime dueAt) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description;
        }
        if (priority != null) {
            this.priority = priority;
        }
        this.dueAt = dueAt;
    }

    /** 单独更新父任务 ID（已由 service 层做自循环与同 user 校验）。 */
    public void applyParent(Long parentId) {
        this.parentId = parentId;
    }

    /** 切换状态（含 IN_PROGRESS）。 */
    public void transitionTo(TaskStatus target) {
        this.status = target;
    }

    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Long getParentId() { return parentId; }
}