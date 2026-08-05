package com.lifewise.plan.event;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.plan.service.ProgressEvaluator;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.EventConsumer;
import org.springframework.stereotype.Component;

/** 订阅 {@code task.reopened}（plan-05-plan §7）— Task 重新打开时刷新 plan 进度与 last_activity_at。 */
@Component
public class TaskReopenedConsumer implements EventConsumer {

    private final ProgressEvaluator progressEvaluator;
    private final LastActivityRefresher refresher;

    public TaskReopenedConsumer(ProgressEvaluator progressEvaluator,
                                LastActivityRefresher refresher) {
        this.progressEvaluator = progressEvaluator;
        this.refresher = refresher;
    }

    @Override
    public String eventType() {
        return EventType.TASK_REOPENED.eventType();
    }

    @Override
    public void consume(EventEnvelope env) {
        Long taskId = env.aggregateId();
        Long userId = env.userId();
        Long planId = extractPlanId(env);
        if (planId != null && userId != null) {
            progressEvaluator.compute(userId, planId);
        }
        if (taskId != null && planId != null) {
            refresher.refreshForTask(taskId, planId);
        }
    }

    private static Long extractPlanId(EventEnvelope env) {
        Object v = env.payload() == null ? null : env.payload().get("planId");
        return v instanceof Number n ? n.longValue() : null;
    }
}