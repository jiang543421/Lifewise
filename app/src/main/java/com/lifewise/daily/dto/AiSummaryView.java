package com.lifewise.daily.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifewise.daily.domain.AiSummary;
import com.lifewise.daily.domain.SummaryKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSummaryView(
        Long id,
        Long dailyReportId,
        LocalDate localDate,
        SummaryKind summaryKind,
        String summaryText,
        String modelName,
        String modelVersion,
        String promptVersion,
        Integer tokensUsed,
        OffsetDateTime generatedAt,
        boolean userEdited) {

    public static AiSummaryView from(AiSummary s) {
        return new AiSummaryView(s.getId(), s.getDailyReportId(), s.getLocalDate(),
                s.getSummaryKind(), s.getSummaryText(), s.getModelName(),
                s.getModelVersion(), s.getPromptVersion(), s.getTokensUsed(),
                s.getGeneratedAt(), s.isUserEdited());
    }
}
