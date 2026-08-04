package com.lifewise.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifewise.ai.domain.AiReport;
import com.lifewise.ai.domain.enums.ContentFormat;
import com.lifewise.ai.domain.enums.ReportKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 报告视图（plan §2.1 GET /api/ai/reports）。
 *
 * <p>{@code referencedEntityIds} 契约：plan §2.1 承诺是数组。V8 DDL 当前未建独立列
 * （参见 review-notes-v8-schema-gap.md §1.6 / V47 待决），DTO 层返回 {@code List.of()}
 * 占位；V47 落地后由 AiReportService 装配实际引用。
 */
public record AiReportView(
        Long id,
        @JsonProperty("job_id") Long jobId,
        ReportKind reportKind,
        @JsonProperty("content_format") ContentFormat contentFormat,
        String title,
        String content,
        @JsonProperty("referenced_entity_ids") List<Long> referencedEntityIds,
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
                List.of(),  // V47 Flyway 落地前兜底；后续由 AiReportService 填充
                report.getPeriodStart(),
                report.getPeriodEnd(),
                report.getFeedbackCount(),
                report.getHelpfulCount(),
                report.getCreatedAt());
    }
}