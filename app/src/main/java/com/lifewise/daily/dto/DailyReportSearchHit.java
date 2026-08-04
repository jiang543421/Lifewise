package com.lifewise.daily.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDate;

/** tsvector 全文检索单条命中：id + 日期 + 高亮片段 + 相关度分。 */
@JsonPropertyOrder({"report_id", "report_date", "snippet", "score"})
public record DailyReportSearchHit(
        Long reportId,
        LocalDate reportDate,
        String snippet,
        double score) {
}
