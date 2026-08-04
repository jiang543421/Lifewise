package com.lifewise.daily.dto;

import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.Mood;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 列表轻量行（不展开 highlights / summary）。 */
public record DailyReportListItem(
        Long id,
        LocalDate reportDate,
        String title,
        Mood mood,
        Integer energyScore,
        boolean isDraft,
        OffsetDateTime createdAt) {

    public static DailyReportListItem from(DailyReport r) {
        return new DailyReportListItem(r.getId(), r.getLocalDate(), r.getTitle(),
                r.getMood(), r.getEnergyScore(), r.isDraft(), r.getCreatedAt());
    }
}
