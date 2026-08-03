package com.lifewise.daily.dto;

import com.lifewise.daily.domain.Mood;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PUT body for {@code /api/daily-reports/{id}}（plan-02-daily §2.1）。
 *
 * <p>NOTE：不允许修改 reportDate（plan §5.1 daily_update_should_preserve_report_date）。
 */
public record DailyReportUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Mood mood,
        @Min(1) @Max(5) Integer energyScore,
        Boolean publish) {
}
