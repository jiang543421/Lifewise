package com.lifewise.shared.integration.outbox;

/**
 * Outbox 收到不在 {@code EventType} 白名单内的事件名时抛出（plan-shared-integration §3.3）。
 *
 * <p>防御性异常：理论上 DB CHECK 约束已保证不会出现，但 dispatcher 在消费端二次校验。
 */
public class UnknownEventTypeException extends RuntimeException {

    private final String eventType;

    public UnknownEventTypeException(String eventType) {
        super("Unknown eventType (not in EventType whitelist): " + eventType);
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
