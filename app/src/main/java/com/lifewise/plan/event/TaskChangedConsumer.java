package com.lifewise.plan.event;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.shared.integration.event.EventEnvelope;

/**
 * 共享消费逻辑：被 {@link PlanEventConsumerConfig#TaskChangedForwarder}
 * 包成两个 bean（{@code task.created} / {@code task.updated}）。
 *
 * <p>本类 <b>不</b> 实现 {@code EventConsumer} —— 否则 {@code OutboxDispatcher}
 * 按 {@code List<EventConsumer>} 自动装配时会把本类实例（来自
 * {@code planTaskChangedConsumer} @Bean）也收进去，然后按
 * {@code eventType()} 索引成死 bean（永远派发不到，且 eventType() 抛 UOE）。
 * forwarder 通过 {@code delegate.consume(env)} 委托，consume 方法保留为
 * 公开 API 但不再 override 接口方法。
 *
 * <p>v1.0：仅刷新 plan.last_activity_at（BR-30），不重算进度（新增 task 不影响
 * 完成度，避免对 AI 模块产生过多消费事件）。
 */
public class TaskChangedConsumer {

    private final LastActivityRefresher refresher;

    public TaskChangedConsumer(LastActivityRefresher refresher) {
        this.refresher = refresher;
    }

    /** 委托入口：仅由 {@code TaskChangedForwarder.consume()} 调用。 */
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