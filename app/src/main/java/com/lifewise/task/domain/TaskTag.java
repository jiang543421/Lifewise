package com.lifewise.task.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 任务标签（plan-01-task §3 + V3 DDL task_tags 表）。
 *
 * <p>约束：(user_id, name) UNIQUE，name length 1~50，
 * color 可选，须匹配 {@code ^#[0-9A-Fa-f]{6}$}（V3 CHECK）。
 */
@Entity
@Table(name = "task_tags")
public class TaskTag extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "color")
    private String color;

    protected TaskTag() {
        // JPA
    }

    private TaskTag(Long userId, String name, String color) {
        this.userId = userId;
        this.name = name;
        this.color = color;
    }

    public static TaskTag create(Long userId, String name, String color) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return new TaskTag(userId, name.trim(), color);
    }

    public void rename(String name, String color) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        this.color = color;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getColor() { return color; }
}