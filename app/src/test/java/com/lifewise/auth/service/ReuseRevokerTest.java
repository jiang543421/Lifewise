package com.lifewise.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.auth.domain.RefreshToken;
import com.lifewise.auth.repository.RefreshTokenRepository;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * ReuseRevoker 单测（plan-auth review H1 修复后补充）。
 *
 * <p>验证：
 * <ol>
 *   <li>委托给 {@link RefreshTokenRepository#findAllByUserIdAndFamilyId} 并 revoke 所有活跃 token</li>
 *   <li>委托给 {@link OutboxWriter#append} 写入 {@code auth.token.reuse_detected}</li>
 *   <li>使用 REQUIRES_NEW 事务模板开启新事务</li>
 * </ol>
 */
@DisplayName("ReuseRevoker 独立事务：family 撤销 + outbox audit")
@ExtendWith(MockitoExtension.class)
class ReuseRevokerTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-07-31T10:00:00Z");

    @Mock private RefreshTokenRepository repository;
    @Mock private OutboxWriter outboxWriter;
    @Mock private PlatformTransactionManager txManager;
    @Mock private TransactionStatus txStatus;

    private ReuseRevoker revoker;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(txStatus);
        revoker = new ReuseRevoker(repository, outboxWriter, txManager);
    }

    @Test
    @DisplayName("revokeAndAudit → revoke family 全部活跃 token + 写 outbox 事件")
    void should_revoke_family_and_emit_outbox_event() {
        UUID familyId = UUID.randomUUID();
        Long userId = 42L;
        String hash = "row-hash";
        RefreshToken reused = RefreshToken.issue(
                userId, familyId, hash,
                NOW.plusDays(30),
                null, "1.2.3.4");

        when(repository.findAllByUserIdAndFamilyId(userId, familyId))
                .thenReturn(List.of(reused));

        revoker.revokeAndAudit(reused, familyId, NOW);

        assertThat(reused.revokedAt()).isNotNull();
        verify(repository, times(1)).save(reused);
        verify(outboxWriter, times(1)).append(any());
        verify(txManager, times(1)).commit(txStatus);
        verify(txManager, times(0)).rollback(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("已 revoked 的 token 跳过 revoke（不重复写 DB）")
    void should_skip_already_revoked_tokens() {
        UUID familyId = UUID.randomUUID();
        Long userId = 42L;
        RefreshToken alreadyRevoked = RefreshToken.issue(
                userId, familyId, "h1", NOW.plusDays(30), null, null);
        alreadyRevoked.revoke(NOW.minusSeconds(60));

        when(repository.findAllByUserIdAndFamilyId(userId, familyId))
                .thenReturn(List.of(alreadyRevoked));

        revoker.revokeAndAudit(alreadyRevoked, familyId, NOW);

        verify(repository, times(0)).save(any());
        verify(outboxWriter, times(1)).append(any());
    }

    @Test
    @DisplayName("使用 REQUIRES_NEW 传播级别开启新事务")
    void should_use_propagation_requires_new() {
        UUID familyId = UUID.randomUUID();
        RefreshToken reused = RefreshToken.issue(
                1L, familyId, "h", NOW.plusDays(30), null, null);
        when(repository.findAllByUserIdAndFamilyId(any(), any())).thenReturn(List.of());

        revoker.revokeAndAudit(reused, familyId, NOW);

        org.mockito.ArgumentCaptor<TransactionDefinition> defCap =
                org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(txManager).getTransaction(defCap.capture());
        assertThat(defCap.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
}