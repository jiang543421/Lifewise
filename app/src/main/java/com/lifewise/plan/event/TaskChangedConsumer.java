package com.lifewise.plan.event;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.EventConsumer;
import org.springframework.stereotype.Component;

/**
 * 订阅 {@code task.created} + {@code task.updated}（plan-05-plan §7）。
 *
 * <p>v1.0：仅刷新 plan.last_activity_at（BR-30），不重算进度
 * （新增 task 不影响完成度，避免对 AI 模块产生过多消费事件）。
 */
@Component
public class TaskChangedConsumer implements EventConsumer {

    private final LastActivityRefresher refresher;

    public TaskChangedConsumer(LastActivityRefresher refresher) {
        this.refresher = refresher;
    }

    @Override
    public String eventType() {
        // 由 Spring 注册为双事件源：见 PlanEventConsumerConfig
        return "task.created|task.updated";
    }

    @Override
    public void consume(EventEnvelope env) {
        Long taskId = env.aggregateId();
        Long planId = extractPlanId(env);
        if (taskId != null && planId != null) {
            refresher.refreshForTask(taskId, planId);
        }
    }

    private static Long extractPlanId(EventEnvelope env) {
        Object v = env.payload() == null ? null : env.payload().get("planId");
        return v instanceof Number n ? n.longValue() : null;
    }
}