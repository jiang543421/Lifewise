package com.lifewise.daily.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.Mood;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** GET 返回的完整视图（包含 highlights + optional summary）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "reportDate", "timezone", "title", "content", "mood",
        "energyScore", "isDraft", "highlights", "summary", "createdAt", "updatedAt"})
public record DailyReportView(
        Long id,
        LocalDate reportDate,
        String timezone,
        String title,
        String content,
        Mood mood,
        Integer energyScore,
        boolean isDraft,
        List<HighlightView> highlights,
        AiSummaryView summary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DailyReportView from(DailyReport r, List<HighlightView> highlights,
                                       AiSummaryView summary) {
        return new DailyReportView(r.getId(), r.getLocalDate(), r.getTimezone(),
                r.getTitle(), r.getContent(), r.getMood(), r.getEnergyScore(),
                r.isDraft(),
                highlights == null ? List.of() : List.copyOf(highlights),
                summary,
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
