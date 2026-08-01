package com.lifewise.auth.service;

import com.lifewise.auth.domain.RefreshToken;
import com.lifewise.auth.event.payload.TokenReuseDetectedPayload;
import com.lifewise.auth.repository.RefreshTokenRepository;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * plan-auth review H1：refresh token reuse 时，family 撤销与 outbox 事件
 * 必须在 REQUIRES_NEW 独立事务中提交 —— 即使外层 {@code JwtRefreshServiceImpl.rotate()}
 * 因为 {@link com.lifewise.auth.domain.exception.TokenReusedException} 回滚，
 * audit 事件依然落库（CLAUDE.md §7.5 错误信息 + plan-auth §5.2 reuse 必须留痕）。
 *
 * <p>独立 bean（不是 JwtRefreshServiceImpl 的私有方法）以确保 Spring AOP /
 * PlatformTransactionManager 拦截。self-invocation 下 {@code @Transactional}
 * 不生效，故使用 {@link TransactionTemplate} 编程式事务，避免被忽略。
 *
 * <p>事务边界（强制 REQUIRES_NEW）：
 * <ul>
 *   <li>{@link RefreshTokenRepository#findAllByUserIdAndFamilyId} + 遍历 revoke</li>
 *   <li>{@link OutboxWriter#append}（Propagation.MANDATORY：要求已开事务，
 *       新事务满足此约束，事件与 family 撤销同事务提交）</li>
 * </ul>
 */
@Service
public class ReuseRevoker {

    private final RefreshTokenRepository repository;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate txTemplate;

    public ReuseRevoker(
            RefreshTokenRepository repository,
            OutboxWriter outboxWriter,
            PlatformTransactionManager txManager) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
        // REQUIRES_NEW：与外层 rotate() 事务隔离，外层 rollback 不影响 audit 事件
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.txTemplate = new TransactionTemplate(txManager, def);
    }

    /**
     * 撤销该 row 所属 family 全部活跃 refresh token，并写 {@code auth.token.reuse_detected}
     * outbox 事件。两者在同一 REQUIRES_NEW 事务中提交。
     *
     * @param row       已 usedAt 的 row（reuse 触发证据）
     * @param familyId  该 row 的 familyId
     * @param when      撤销/事件时间戳（来自外层调用方的 Clock）
     */
    public void revokeAndAudit(RefreshToken row, UUID familyId, OffsetDateTime when) {
        txTemplate.executeWithoutResult(status -> {
            // 1) 撤销 family 内所有活跃 refresh token
            repository.findAllByUserIdAndFamilyId(row.userId(), familyId).forEach(rt -> {
                if (rt.revokedAt() == null) {
                    rt.revoke(when);
                    repository.save(rt);
                }
            });
            // 2) outbox 事件：MANDATORY 在新事务里允许
            outboxWriter.append(new EventEnvelope(
                    UUID.randomUUID(),
                    EventType.AUTH_TOKEN_REUSE_DETECTED.eventType(),
                    1,
                    when,
                    row.userId(),
                    "refresh_token",
                    row.getId(),
                    null,
                    null,
                    null,
                    new TokenReuseDetectedPayload(
                            row.userId(),
                            familyId.toString(),
                            row.ipAddress(),
                            when).toMap()));
        });
    }
}