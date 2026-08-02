package com.lifewise.task.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 习惯打卡日志（plan-01-task §3 + V3 DDL habit_logs 表）。
 *
 * <p>约束：
 * <ul>
 *   <li>BR-01（V3 标记为 BR-01）：(habit_id, local_date) UNIQUE</li>
 *   <li>BR-05：backfill_for_date ∈ [today-3, today)（应用层校验）</li>
 * </ul>
 */
@Entity
@Table(name = "habit_logs")
public class HabitLog extends BaseEntity {

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "logged_at", nullable = false)
    private OffsetDateTime loggedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private HabitLogSource source;

    @Column(name = "note", length = 1000)
    private String note;

    protected HabitLog() {
        // JPA
    }

    private HabitLog(Long habitId, Long userId, LocalDate localDate,
                     OffsetDateTime loggedAt, HabitLogSource source, String note) {
        this.habitId = habitId;
        this.userId = userId;
        this.localDate = localDate;
        this.loggedAt = loggedAt;
        this.source = source;
        this.note = note;
    }

    public static HabitLog of(Long habitId, Long userId, LocalDate localDate,
                              OffsetDateTime loggedAt, HabitLogSource source, String note) {
        if (habitId == null || userId == null || localDate == null || loggedAt == null) {
            throw new IllegalArgumentException("habitId/userId/localDate/loggedAt required");
        }
        return new HabitLog(habitId, userId, localDate, loggedAt, source, note);
    }

    public Long getHabitId() { return habitId; }
    public Long getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public OffsetDateTime getLoggedAt() { return loggedAt; }
    public HabitLogSource getSource() { return source; }
    public String getNote() { return note; }
}