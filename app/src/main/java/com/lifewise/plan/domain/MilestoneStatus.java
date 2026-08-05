package com.lifewise.plan.domain;

/**
 * Milestone 状态机（V4 milestones.status CHECK 约束 + BR-14）。
 *
 * <p>状态转移（plan-05-plan §4.2）：
 * <pre>
 * PENDING ──complete──&gt; DONE
 * PENDING ──miss──&gt; MISSED    （定时任务 sweep 标记）
 * DONE     ──reopen──&gt; PENDING
 * PENDING ──cancel──&gt; CANCELLED
 * DONE     ──cancel──&gt; CANCELLED
 * </pre>
 *
 * <p>{@code IN_PROGRESS} 状态在 v1.0 由业务层在「用户编辑里程碑」或
 * 「任务有任一 in-progress 子任务」时显式设置；本模块不主动推进。
 */
public enum MilestoneStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    MISSED,
    CANCELLED
}