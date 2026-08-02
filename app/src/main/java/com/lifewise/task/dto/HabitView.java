package com.lifewise.task.dto;

import com.lifewise.task.domain.Habit;
import com.lifewise.task.domain.HabitFrequency;
import java.time.OffsetDateTime;

public record HabitView(Long id, String title, String description, HabitFrequency frequency,
                        int targetPerPeriod, boolean archived, OffsetDateTime archivedAt,
                        int currentStreak, int longestStreak) {
    public static HabitView of(Habit habit, int currentStreak, int longestStreak) {
        return new HabitView(habit.getId(), habit.getTitle(), habit.getDescription(),
                habit.getFrequency(), habit.getTargetPerPeriod(), habit.isArchived(),
                habit.getArchivedAt(), currentStreak, longestStreak);
    }
}
