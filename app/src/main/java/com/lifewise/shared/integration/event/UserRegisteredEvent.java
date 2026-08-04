package com.lifewise.shared.integration.event;

/**
 * 用户注册 Spring 应用事件（plan-03-expense BR-24 + AuthService.register 触发）。
 *
 * <p>走 Spring {@code ApplicationEventPublisher}，与 {@code auth.user.registered}
 * outbox 事件是两个独立通道：
 * <ul>
 *   <li>本事件：进程内同步，{@code CategorySeedService} 在同一事务内预置「其他」分类</li>
 *   <li>outbox 事件：跨模块异步，OutboxWorker 投递下游订阅者（v1.1 接入）</li>
 * </ul>
 *
 * <p>v1.0 单用户场景下，注册事务要求 seed 同步完成（用户首次记账需可立即归类），
 * 因此走 Spring ApplicationEvent 而非 outbox 异步链路。
 *
 * <p>仅承载最小字段 {@code userId}，避免 shared 模块依赖 auth payload record。
 */
public record UserRegisteredEvent(Long userId) {
}
