package com.lifewise.shared.integration.port.snapshot;

import java.time.LocalDate;

/**
 * 日报只读快照（plan-shared-integration §2.2）。
 *
 * <p>对齐 PG {@code daily_report} 表（plan-data-flyway V23）。
 *
 * <p><b>字段语义契约</b>（plan-02-daily §4 端口边界）：
 * <ul>
 *   <li>{@code id} / {@code userId} / {@code reportDate} —— 1:1 对应 daily_reports 列</li>
 *   <li>{@code mood} —— {@code Mood} 枚举 {@code name()} 字符串（如 {@code "GREAT"} / {@code "BAD"}），
 *       与数值化 {@code energy_score}（{@code DailyReadPort#averageMoodInRange}）<b>不同维度</b>；
 *       消费方若需数值请单独调 averageMoodInRange，不要尝试把此字段解析成数字。</li>
 *   <li>{@code summary} —— <b>内容截取</b>（{@code content} 前 120 字符，{@code …} 结尾），
 *       <b>不是 AI 摘要</b>。AI 真摘要在 ai 模块通过 {@code AiSummaryView} 单独获取。
 *       命名贴近"日报概览"语义，消费方切勿当作 ai.summary.generated 的结果使用。</li>
 * </ul>
 */
public record DailySnapshot(
        Long id,
        Long userId,
        LocalDate reportDate,
        String mood,
        String summary) {
}
