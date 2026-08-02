package com.lifewise.task.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 习惯实体（plan-01-task §3 + V3 DDL habits 表）。
 *
 * <p>字段对齐：
 * <ul>
 *   <li>{@code frequency} — DAILY / WEEKLY（V3 CHECK）</li>
 *   <li>{@code targetPerPeriod} — 1~7（V3 CHECK，DAILY/WEEKLY 目标次数）</li>
 *   <li>{@code isArchived} — 软停用标志（不删除，仅归档）</li>
 * </ul>
 */
@Entity
@Table(name = "habits")
public class Habit extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private HabitFrequency frequency;

    @Column(name = "target_per_period", nullable = false)
    private int targetPerPeriod;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    protected Habit() {
        // JPA
    }

    private Habit(Long userId, String title, String description,
                  HabitFrequency frequency, int targetPerPeriod) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.frequency = frequency;
        this.targetPerPeriod = targetPerPeriod;
        this.archived = false;
    }

    public static Habit create(Long userId, String title, String description,
                               HabitFrequency frequency, int targetPerPeriod) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (frequency == null) {
            throw new IllegalArgumentException("frequency must be DAILY or WEEKLY");
        }
        if (targetPerPeriod < 1 || targetPerPeriod > 7) {
            throw new IllegalArgumentException("targetPerPeriod must be 1..7");
        }
        return new Habit(userId, title.trim(), description, frequency, targetPerPeriod);
    }

    public void update(String title, String description,
                       HabitFrequency frequency, int targetPerPeriod) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description;
        }
        if (frequency != null) {
            this.frequency = frequency;
        }
        if (targetPerPeriod >= 1 && targetPerPeriod <= 7) {
            this.targetPerPeriod = targetPerPeriod;
        }
    }

    public void archive(OffsetDateTime when) {
        this.archived = true;
        this.archivedAt = when;
    }

    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public HabitFrequency getFrequency() { return frequency; }
    public int getTargetPerPeriod() { return targetPerPeriod; }
    public boolean isArchived() { return archived; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
}