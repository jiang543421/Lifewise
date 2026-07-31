package com.lifewise.shared.integration.event;

/**
 * Outbox 事件枚举（plan-shared-integration §4 + data-model-v1.2-amendment §3.35 V33）。
 *
 * <p>25 条事件名（小写点分 snake_case）与 PG
 * {@code outbox_events.event_type} 的 CHECK 约束一一对应（plan-data-flyway.md §3.35）。
 * 移除或重命名为破坏性变更（如需新增，旧事件保留 + 接入新事件）。
 *
 * <p>命名规则：
 * <ul>
 *   <li>{@code enum.name()} = UPPER_SNAKE_CASE（Java 枚举常量约定）</li>
 *   <li>{@link #eventType()} = 小写点分 snake_case（DB wire 字符串）</li>
 * </ul>
 */
public enum EventType {
    // ---- task 与 habit ----
    TASK_COMPLETED("task.completed"),
    TASK_REOPENED("task.reopened"),
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    HABIT_LOGGED("habit.logged"),

    // ---- plan / milestone ----
    PLAN_CREATED("plan.created"),
    MILESTONE_CREATED("milestone.created"),
    MILESTONE_UPDATED("milestone.updated"),
    MILESTONE_COMPLETED("milestone.completed"),
    MILESTONE_MISSED("milestone.missed"),

    // ---- daily_report / ai.summary ----
    DAILY_REPORT_CREATED("daily_report.created"),
    DAILY_REPORT_UPDATED("daily_report.updated"),
    AI_SUMMARY_GENERATED("ai.summary.generated"),

    // ---- meal / expense / budget ----
    MEAL_CREATED("meal.created"),
    EXPENSE_CREATED("expense.created"),
    BUDGET_THRESHOLD("budget.threshold"),

    // ---- ai ----
    AI_JOB_COMPLETED("ai.job.completed"),
    AI_REPORT_FEEDBACK("ai.report.feedback"),

    // ---- export ----
    EXPORT_COMPLETED("export.completed"),
    EXPORT_FAILED("export.failed"),

    // ---- notification ----
    NOTIFICATION_REQUESTED("notification.requested"),

    // ---- auth (V33 新增 4 条，plan-shared-integration §4 + X7) ----
    AUTH_USER_REGISTERED("auth.user.registered"),
    AUTH_USER_LOGGED_IN("auth.user.logged_in"),
    AUTH_USER_PASSWORD_RESET_REQUESTED("auth.user.password_reset_requested"),
    AUTH_TOKEN_REUSE_DETECTED("auth.token.reuse_detected");

    private final String eventType;

    EventType(String eventType) {
        this.eventType = eventType;
    }

    /** Wire 事件名（与 outbox_events.event_type CHECK 白名单对齐）。 */
    public String eventType() {
        return eventType;
    }
}
