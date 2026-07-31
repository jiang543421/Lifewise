package com.lifewise.shared.integration.outbox;

import com.lifewise.shared.integration.event.EventEnvelope;

/**
 * 跨模块事件消费者契约（plan-shared-integration §3.4 + business-architecture §5.4）。
 *
 * <p>实现位于各业务模块（如 daily.TaskCompletedConsumer / plan.MilestoneCompletedConsumer /
 * ai.*Consumer）。consumer 失败抛异常，由 Worker 增加 retry_count 或转死信。
 */
public interface EventConsumer {

    /** 订阅的事件类型（小写点分 snake_case，对齐 EventType.eventType()）。 */
    String eventType();

    /**
     * @param env 完整事件信封（payload 是 Map&lt;String,Object&gt;）
     * @throws RuntimeException 业务处理失败 → Worker 重试；达上限进死信
     */
    void consume(EventEnvelope env);
}
