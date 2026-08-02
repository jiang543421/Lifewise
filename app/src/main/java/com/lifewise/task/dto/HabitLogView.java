package com.lifewise.task.dto;

import com.lifewise.task.domain.HabitLog;
import com.lifewise.task.domain.HabitLogSource;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record HabitLogView(Long id, Long habitId, LocalDate localDate, OffsetDateTime loggedAt,
                           HabitLogSource source, String note) {
    public static HabitLogView from(HabitLog log) {
        return new HabitLogView(log.getId(), log.getHabitId(), log.getLocalDate(), log.getLoggedAt(),
                log.getSource(), log.getNote());
    }
}
