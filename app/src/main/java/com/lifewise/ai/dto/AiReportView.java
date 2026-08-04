package com.lifewise.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifewise.ai.domain.AiReport;
import com.lifewise.ai.domain.enums.ContentFormat;
import com.lifewise.ai.domain.enums.ReportKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** AI 报告视图（plan §2.1 GET /api/ai/reports）。 */
public record AiReportView(
        Long id,
        @JsonProperty("job_id") Long jobId,
        ReportKind reportKind,
        @JsonProperty("content_format") ContentFormat contentFormat,
        String title,
        String content,
        @JsonProperty("referenced_entity_ids") String referencedEntityIdsJson,
        @JsonProperty("period_start") LocalDate periodStart,
        @JsonProperty("period_end") LocalDate periodEnd,
        @JsonProperty("feedback_count") Integer feedbackCount,
        @JsonProperty("helpful_count") Integer helpfulCount,
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    public static AiReportView from(AiReport report) {
        return new AiReportView(
                report.getId(),
                report.getJobId(),
                report.getReportKind(),
                report.getContentFormat(),
                report.getTitle(),
                report.getContent(),
                "[]",  // plan 提 referenced_entity_ids；V8 实际未独立列；存空数组保契约
                report.getPeriodStart(),
                report.getPeriodEnd(),
                report.getFeedbackCount(),
                report.getHelpfulCount(),
                report.getCreatedAt());
    }
}