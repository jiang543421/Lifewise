package com.lifewise.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * AI 报告生成请求（plan §2.1）。
 *
 * @param reportType 前端友好名（daily_summary / weekly_summary / plan_review /
 *                   task_advice / monthly_summary 等）— 服务层映射到 {@link com.lifewise.ai.domain.enums.AiJobType}
 * @param periodFrom 报告数据周期起点（必填）
 * @param periodTo   报告数据周期终点（必填；≥ periodFrom）
 * @param params     可选附加参数（结构化 JSON）
 */
public record AiJobRequest(
        @NotNull String reportType,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo,
        @JsonProperty("params") String paramsJson) {
}