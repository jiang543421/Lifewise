package com.lifewise.task.dto;

import com.lifewise.task.domain.HabitFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HabitCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull HabitFrequency frequency,
        @Min(1) @Max(7) int targetPerPeriod) {
}
