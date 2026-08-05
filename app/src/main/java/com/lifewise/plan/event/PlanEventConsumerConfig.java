package com.lifewise.plan.event;

import com.lifewise.plan.service.LastActivityRefresher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.EventConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * plan 模块事件消费者注册（plan-05-plan §7）。
 *
 * <p>{@code TaskChangedConsumer} 单实例同时订阅 {@code task.created} + {@code task.updated}，
 * 因此注册为两个 bean（共享同一实例）。本 config 同时负责装配
 * {@link TaskChangedConsumer} 本身 —— 因为它不是 {@code @Component}（见类 javadoc）。
 */
@Configuration
public class PlanEventConsumerConfig {

    @Bean
    public TaskChangedConsumer planTaskChangedConsumer(LastActivityRefresher refresher) {
        return new TaskChangedConsumer(refresher);
    }

    @Bean("planTaskChangedCreatedConsumer")
    public EventConsumer taskChangedCreatedConsumer(TaskChangedConsumer consumer) {
        return new TaskChangedForwarder("task.created", consumer);
    }

    @Bean("planTaskChangedUpdatedConsumer")
    public EventConsumer taskChangedUpdatedConsumer(TaskChangedConsumer consumer) {
        return new TaskChangedForwarder("task.updated", consumer);
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