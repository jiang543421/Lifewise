package com.lifewise.common;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuditListener} @PrePersist / @PreUpdate 回调单测。
 *
 * <p>{@code setCreatedAtInternal} / {@code setUpdatedAtInternal} 是 package-private setter，
 * Mockito 5.x 默认开启 mock-maker-inline，可直接 mock concrete class + package-private 方法。
 *
 * <p>本测试不依赖 Spring Context / JPA EntityManager，仅验证回调方法的合约：
 * 传入 BaseEntity → 调对应 setter → 不动 createdAt（已存在时）/ deletedAt。
 */
@DisplayName("AuditListener 生命周期回调")
@ExtendWith(MockitoExtension.class)
class AuditListenerTest {

    @Mock private BaseEntity entity;

    private AuditListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditListener();
    }

    // --- @PrePersist ---

    @Test
    @DisplayName("onPrePersist → createdAt 为 null 时写入 now + updatedAt 写入 now")
    void prePersist_should_set_both_timestamps_when_createdAt_is_null() {
        when(entity.getCreatedAt()).thenReturn(null);

        listener.onPrePersist(entity);

        verify(entity).setCreatedAtInternal(nonNullOffsetDateTime());
        verify(entity).setUpdatedAtInternal(nonNullOffsetDateTime());
    }

    @Test
    @DisplayName("onPrePersist → createdAt 已存在时不覆盖（保持原值）；updatedAt 仍更新为 now")
    void prePersist_should_preserve_existing_createdAt_but_still_update_updatedAt() {
        OffsetDateTime existingCreated = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        when(entity.getCreatedAt()).thenReturn(existingCreated);

        listener.onPrePersist(entity);

        verify(entity, never()).setCreatedAtInternal(nonNullOffsetDateTime());
        verify(entity).setUpdatedAtInternal(nonNullOffsetDateTime());
    }

    // --- @PreUpdate ---

    @Test
    @DisplayName("onPreUpdate → 只更新 updatedAt，不动 createdAt")
    void preUpdate_should_only_refresh_updatedAt() {
        listener.onPreUpdate(entity);

        verify(entity).setUpdatedAtInternal(nonNullOffsetDateTime());
        verify(entity, never()).setCreatedAtInternal(nonNullOffsetDateTime());
    }

    @Test
    @DisplayName("同一 listener 实例可多次调用（无状态）")
    void listener_should_be_stateless_across_calls() {
        when(entity.getCreatedAt()).thenReturn(null);

        listener.onPrePersist(entity);
        listener.onPreUpdate(entity);

        verify(entity, times(1)).setCreatedAtInternal(nonNullOffsetDateTime());
        verify(entity, times(2)).setUpdatedAtInternal(nonNullOffsetDateTime());
    }

    /**
     * 任意非空 OffsetDateTime — Mockito ArgumentMatcher 仅影响 verify 能否匹配，
     * 实际调用次数与值精度无关（避免时钟依赖的脆弱断言）。
     */
    private static OffsetDateTime nonNullOffsetDateTime() {
        return org.mockito.ArgumentMatchers.notNull(OffsetDateTime.class);
    }
}