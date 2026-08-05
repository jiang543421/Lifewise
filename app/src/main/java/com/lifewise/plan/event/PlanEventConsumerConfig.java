package com.lifewise.plan.event;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.EventConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * plan 模块事件消费者注册（plan-05-plan §7）。
 *
 * <p>{@code TaskChangedConsumer} 同时订阅 {@code task.created} + {@code task.updated}，
 * 因此包成两个 {@link TaskChangedForwarder} bean。
 *
 * <p><b>关键约束</b>：{@link TaskChangedConsumer} 本身**不能**注册为 bean。
 * {@code OutboxDispatcher} 构造时注入 {@code List<EventConsumer>}（即全部 EventConsumer
 * bean）并对每个调用 {@code eventType()}，而 TaskChangedConsumer#eventType() 是故意抛
 * {@code UnsupportedOperationException} 的 —— 只要它是 bean，上下文就起不来。
 * 所以 delegate 一律在 forwarder 内部 new，不经过容器。
 */
@Configuration
public class PlanEventConsumerConfig {

    @Bean("planTaskChangedCreatedConsumer")
    public EventConsumer taskChangedCreatedConsumer(LastActivityRefresher refresher) {
        return new TaskChangedForwarder("task.created", new TaskChangedConsumer(refresher));
    }

    @Bean("planTaskChangedUpdatedConsumer")
    public EventConsumer taskChangedUpdatedConsumer(LastActivityRefresher refresher) {
        return new TaskChangedForwarder("task.updated", new TaskChangedConsumer(refresher));
    }

    /** 简单转发器：固定 eventType + 委托 consume。 */
    static final class TaskChangedForwarder implements EventConsumer {
        private final String type;
        private final TaskChangedConsumer delegate;

        TaskChangedForwarder(String type, TaskChangedConsumer delegate) {
            this.type = type;
            this.delegate = delegate;
        }

        @Override public String eventType() { return type; }
        @Override public void consume(EventEnvelope env) { delegate.consume(env); }
    }
}