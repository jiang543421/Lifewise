package com.lifewise.shared.integration.outbox;

/**
 * Outbox dispatcher 未找到订阅 consumer 时抛出（plan-shared-integration §3.3）。
 *
 * <p>语义：事件已通过 EventType 白名单校验，但当前进程无 consumer 订阅该类型。
 * Worker 捕获后触发 retry；如确认不需要，应停止发送该事件而非注册 noop consumer。
 */
public class NoConsumerRegisteredException extends RuntimeException {

    private final String eventType;

    public NoConsumerRegisteredException(String eventType) {
        super("No EventConsumer registered for eventType=" + eventType);
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
