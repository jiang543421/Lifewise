package com.lifewise.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 任务 ↔ 标签 链接（plan-01-task §3 + V3 DDL task_tag_links 表）。
 *
 * <p>复合主键 (task_id, tag_id)；应用层校验单任务 ≤ 5 标签（BR-03）。
 */
@Entity
@Table(name = "task_tag_links")
public class TaskTagLink {

    @EmbeddedId
    private Pk id;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TaskTagLink() {
        // JPA
    }

    public TaskTagLink(Long taskId, Long tagId) {
        this.id = new Pk(taskId, tagId);
        this.createdAt = OffsetDateTime.now();
    }

    public Pk getId() { return id; }
    public Long getTaskId() { return id.taskId; }
    public Long getTagId() { return id.tagId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Embeddable
    public static class Pk implements Serializable {

        @Column(name = "task_id", nullable = false)
        private Long taskId;

        @Column(name = "tag_id", nullable = false)
        private Long tagId;

        public Pk() {
            // JPA
        }

        public Pk(Long taskId, Long tagId) {
            this.taskId = taskId;
            this.tagId = tagId;
        }

        public Long getTaskId() { return taskId; }
        public Long getTagId() { return tagId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(taskId, pk.taskId) && Objects.equals(tagId, pk.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, tagId);
        }
    }
}