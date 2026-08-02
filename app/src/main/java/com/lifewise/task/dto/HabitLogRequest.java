package com.lifewise.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifewise.task.domain.HabitLogSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HabitLogRequest(
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate localDate,
        HabitLogSource source,
        @Size(max = 1000) String note) {
    public HabitLogSource sourceOrDefault() { return source == null ? HabitLogSource.NORMAL : source; }
}
