package com.lifewise.daily.dto;

import com.lifewise.daily.domain.DailyReportHighlight;
import com.lifewise.daily.domain.HighlightType;

public record HighlightView(
        Long id,
        Long dailyReportId,
        HighlightType highlightType,
        String title,
        String description,
        String referenceType,
        Long referenceId,
        int sortOrder) {

    public static HighlightView from(DailyReportHighlight h) {
        return new HighlightView(h.getId(), h.getDailyReportId(),
                h.getHighlightType(), h.getTitle(), h.getDescription(),
                h.getReferenceType(), h.getReferenceId(), h.getSortOrder());
    }
}
