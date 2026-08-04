package com.lifewise.ai.domain.enums;

/**
 * AI 作业状态机（V8 + V31 ai_jobs.status CHECK 约束）。
 *
 * <p>状态流转：
 * <pre>
 * PENDING ──▶ RUNNING ──▶ DONE
 *                  │
 *                  ├─▶ RUNNING_DEGRADED ──▶ DONE_PARTIAL   （源数据缺失）
 *                  │
 *                  └─▶ DONE_NO_LLM                          （Ollama 不可用 / 用户未同意）
 *     │
 *     └─▶ FAILED / CANCELLED
 * </pre>
 *
 * <p>X3 闭环：plan-06-ai §2.4 + §6 — DONE / DONE_NO_LLM / DONE_PARTIAL 三态均触发
 * OutboxWriter 写 {@code ai.job.completed}，避免 Ollama 红色态或源数据缺失时通知丢失。
 */
public enum AiJobStatus {
    PENDING,
    PENDING_PARTIAL,
    RUNNING,
    RUNNING_DEGRADED,
    DONE,
    DONE_PARTIAL,
    DONE_NO_LLM,
    FAILED,
    CANCELLED;

    /** 是否终态（不再转换）。 */
    public boolean isTerminal() {
        return this == DONE || this == DONE_PARTIAL || this == DONE_NO_LLM
                || this == FAILED || this == CANCELLED;
    }
}