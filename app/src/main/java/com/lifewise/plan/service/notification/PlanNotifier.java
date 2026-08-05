package com.lifewise.plan.service.notification;

import com.lifewise.plan.domain.Plan;

/**
 * 计划通知抽象（plan-05-plan §5.7）。
 *
 * <p>v1.0：默认实现 {@link NoopPlanNotifier}（仅 log，不推送）。
 * v1.1 接入 push 模块时替换为 {@code PushPlanNotifier}（遵循 plan-shared-integration §5）。
 */
public interface PlanNotifier {
    void notifyStale(Plan plan);
}