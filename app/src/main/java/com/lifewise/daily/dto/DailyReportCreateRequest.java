package com.lifewise.daily.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifewise.daily.domain.Mood;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * POST body for {@code /api/daily-reports}（plan-02-daily §2.1）。
 *
 * <p>content ≤ 50000（BR-25）；title 1~200；energy_score ∈ [1,5]（V5 CHECK）；
 * timezone ≤ 64（V5 CHECK）。
 */
public record DailyReportCreateRequest(
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate reportDate,
        @Size(max = 64) String timezone,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Mood mood,
        @Min(1) @Max(5) Integer energyScore) {
}
