package com.lifewise.plan.event;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.EventConsumer;

/**
 * 共享消费逻辑：被 {@link PlanEventConsumerConfig#TaskChangedForwarder}
 * 包成两个 bean（{@code task.created} / {@code task.updated}）。
 *
 * <p>本类不是 {@code @Component} —— 避免被 {@code OutboxDispatcher} 按
 * {@code eventType()} 索引成死 bean（{@link #eventType()} 返回
 * "task.created|task.updated" 不匹配任何 EventType，永远不会被派发）。
 *
 * <p>v1.0：仅刷新 plan.last_activity_at（BR-30），不重算进度（新增 task 不影响
 * 完成度，避免对 AI 模块产生过多消费事件）。
 */
public class TaskChangedConsumer implements EventConsumer {

    private final LastActivityRefresher refresher;

    public TaskChangedConsumer(LastActivityRefresher refresher) {
        this.refresher = refresher;
    }

    @Override
    public String eventType() {
        // 永远不应被 dispatcher 直接调用 —— 见类 javadoc
        throw new UnsupportedOperationException(
                "TaskChangedConsumer must be wrapped by PlanEventConsumerConfig#TaskChangedForwarder");
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