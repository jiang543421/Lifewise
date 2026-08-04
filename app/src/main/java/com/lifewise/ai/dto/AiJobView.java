package com.lifewise.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifewise.ai.domain.AiJob;
import com.lifewise.ai.domain.enums.AiJobStatus;
import com.lifewise.ai.domain.enums.AiJobType;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * AI 作业视图（plan §2.2 GET /api/ai/jobs/{id}）。
 *
 * <p>status 同时返回 DB 枚举与 final_status 字段标识
 * DONE/DONE_NO_LLM/DONE_PARTIAL 三态（plan §7.6.1 X3 闭环）。
 */
public record AiJobView(
        Long id,
        @JsonProperty("report_type") String reportType,
        AiJobType jobType,
        AiJobStatus status,
        @JsonProperty("final_status") String finalStatus,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("tokens_used") Integer tokensUsed,
        String error,
        @JsonProperty("period_start") LocalDate periodStart,
        @JsonProperty("period_end") LocalDate periodEnd,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("finished_at") OffsetDateTime finishedAt) {

    public static AiJobView from(AiJob job) {
        return new AiJobView(
                job.getId(),
                job.getJobType().name().toLowerCase(),
                job.getJobType(),
                job.getStatus(),
                job.getStatus().name(),
                job.getModelVersion(),
                job.getTokensUsed(),
                job.getError(),
                job.getPeriodStart(),
                job.getPeriodEnd(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}