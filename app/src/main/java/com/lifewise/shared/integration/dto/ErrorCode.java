package com.lifewise.shared.integration.dto;

/**
 * 稳定错误码（business-architecture §3.6 + technical-architecture §3.6）。
 *
 * <p>错误码作为 API 信封的一部分向外暴露，移除或重命名会被前端视为破坏性变更。
 * 新增错误码自由；旧错误码只能增加别名桥接，**禁止删除或改名**。
 *
 * <p>错误码命名约束：
 * <ul>
 *   <li>大写 snake_case（{@code [A-Z][A-Z0-9_]*}）</li>
 *   <li>领域前缀便于排查（{@code TASK_*} / {@code EXPENSE_*} 等）</li>
 *   <li>HTTP 同名错误码仅 4xx/5xx 等少数情况下与 HTTP status 对齐</li>
 * </ul>
 */
public enum ErrorCode {
    // ---- 通用 / 业务架构 §3.6 基线 ----
    INVALID_INPUT,
    NOT_FOUND,
    CROSS_USER_ACCESS,
    VERSION_CONFLICT,
    RATE_LIMITED,
    AI_UNAVAILABLE,
    PUSH_DELIVERED_INAPP,
    INTERNAL_ERROR,

    // ---- 任务与习惯 ----
    TASK_NOT_FOUND,
    TASK_INVALID_STATUS_TRANSITION,
    HABIT_OUT_OF_BACKFILL_WINDOW,

    // ---- 计划 ----
    PLAN_NOT_FOUND,
    MILESTONE_NOT_FOUND,
    PLAN_END_BEFORE_START,
    PLAN_ALREADY_ABANDONED,
    MILESTONE_DONE_READONLY,
    MILESTONE_ALREADY_DONE,
    MILESTONE_NOT_DONE,
    CROSS_MODULE_TASK_NOT_FOUND,

    // ---- 日报 ----
    DAILY_REPORT_NOT_FOUND,
    DAILY_REPORT_VERSION_CONFLICT,

    // ---- 消费与预算 ----
    EXPENSE_INVALID_AMOUNT,
    BUDGET_ALREADY_EXISTS,
    EXPENSE_NOT_FOUND,
    CATEGORY_NOT_FOUND,
    CATEGORY_NAME_EXISTS,
    CATEGORY_PROTECTED,
    CATEGORY_HAS_BUDGET,
    BUDGET_NOT_FOUND,
    DATA_CONFLICT,                 // plan-03 review H5：兜底唯一约束违反等（漏网到 service 层业务异常）

    // ---- 饮食与营养 ----
    MEAL_INVALID_TIME_WINDOW,
    MEAL_NOT_FOUND,
    FOOD_NOT_FOUND,

    // ---- AI ----
    AI_JOB_NOT_FOUND,

    // ---- 通知 ----
    NOTIFICATION_PUSH_NOT_SUBSCRIBED,

    // ---- 导出 ----
    EXPORT_REQUEST_NOT_FOUND,
    EXPORT_ARTIFACT_EXPIRED,

    // ---- Outbox ----
    OUTBOX_DISPATCH_FAILED,

    // ---- Auth (plan-auth §2.1 鉴权闭环 8 端点错误码) ----
    EMAIL_EXISTS,
    WEAK_PASSWORD,
    INVALID_CREDENTIALS,
    USER_LOCKED,
    TOKEN_INVALID,
    TOKEN_REUSED,
    TOKEN_EXPIRED
}
