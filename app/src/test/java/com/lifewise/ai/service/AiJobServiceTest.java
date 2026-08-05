package com.lifewise.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.ai.domain.AiJob;
import com.lifewise.ai.domain.enums.AiJobStatus;
import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.repository.AiJobRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiJobService 单元测试（plan-06-ai §7.6）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>createJob → PENDING + id 返回</li>
 *   <li>幂等性：同 (user, type, period) 已存在 → 复用旧 id</li>
 *   <li>createJob 参数校验</li>
 *   <li>状态机迁移（markRunning / markDone / markFailed）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AiJobServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    @Mock AiJobRepository repository;
    AiJobService service;
    AtomicLong nowMillis;

    @BeforeEach
    void setUp() {
        nowMillis = new AtomicLong(Instant.parse("2026-08-05T08:00:00Z").toEpochMilli());
        Clock clock = new Clock() {
            @Override public Instant instant() { return Instant.ofEpochMilli(nowMillis.get()); }
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            public long millis() { return nowMillis.get(); }
        };
        service = new AiJobService(repository, clock);
    }

    @Test
    @DisplayName("createJob persists a PENDING job and returns its id")
    void createJob_persistsPending() {
        when(repository.findActiveByUserTypePeriod(any(), any(), any(), any())).thenReturn(List.of());
        when(repository.save(any(AiJob.class))).thenAnswer(inv -> {
            AiJob j = inv.getArgument(0);
            setId(j, 42L);
            return j;
        });

        Long jobId = service.createJob(USER_ID, AiJobType.DAILY_SUMMARY, FROM, TO);

        assertThat(jobId).isEqualTo(42L);
        ArgumentCaptor<AiJob> cap = ArgumentCaptor.forClass(AiJob.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AiJobStatus.PENDING);
        assertThat(cap.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(cap.getValue().getJobType()).isEqualTo(AiJobType.DAILY_SUMMARY);
        assertThat(cap.getValue().getPeriodStart()).isEqualTo(FROM);
        assertThat(cap.getValue().getPeriodEnd()).isEqualTo(TO);
    }

    @Test
    @DisplayName("createJob is idempotent: reuses existing active job for same user/type/period")
    void createJob_idempotent_reusesExistingId() {
        AiJob existing = AiJob.createPending(USER_ID, AiJobType.DAILY_SUMMARY,
                OffsetDateTime.now(), FROM, TO);
        setId(existing, 99L);
        when(repository.findActiveByUserTypePeriod(any(), any(), any(), any()))
                .thenReturn(List.of(existing));

        Long jobId = service.createJob(USER_ID, AiJobType.DAILY_SUMMARY, FROM, TO);

        assertThat(jobId).isEqualTo(99L);
        // 关键：不创建新作业
        verify(repository, never()).save(any(AiJob.class));
    }

    @Test
    @DisplayName("createJob validates inputs")
    void createJob_invalidInputs_throws() {
        assertThatThrownBy(() -> service.createJob(null, AiJobType.DAILY_SUMMARY, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> service.createJob(USER_ID, null, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobType");
        assertThatThrownBy(() -> service.createJob(USER_ID, AiJobType.DAILY_SUMMARY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createJob(USER_ID, AiJobType.DAILY_SUMMARY, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodEnd");
        verify(repository, never()).save(any(AiJob.class));
    }

    @Test
    @DisplayName("state transitions delegate to AiJob entity and persist via repository.save")
    void markRunning_persistsTransition() {
        AiJob job = AiJob.createPending(USER_ID, AiJobType.DAILY_SUMMARY,
                OffsetDateTime.now(), FROM, TO);
        setId(job, 10L);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(10L, USER_ID))
                .thenReturn(Optional.of(job));
        when(repository.save(any(AiJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markRunning(10L, USER_ID);

        ArgumentCaptor<AiJob> cap = ArgumentCaptor.forClass(AiJob.class);
        verify(repository, times(1)).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AiJobStatus.RUNNING);
        assertThat(cap.getValue().getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed persists FAILED status with error message")
    void markFailed_persistsError() {
        AiJob job = AiJob.createPending(USER_ID, AiJobType.DAILY_SUMMARY,
                OffsetDateTime.now(), FROM, TO);
        setId(job, 11L);
        when(repository.findByIdAndUserIdAndDeletedAtIsNull(11L, USER_ID))
                .thenReturn(Optional.of(job));
        when(repository.save(any(AiJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markRunning(11L, USER_ID);
        service.markFailed(11L, USER_ID, "ollama timeout");

        ArgumentCaptor<AiJob> cap = ArgumentCaptor.forClass(AiJob.class);
        verify(repository, times(2)).save(cap.capture());
        AiJob saved = cap.getAllValues().get(1);
        assertThat(saved.getStatus()).isEqualTo(AiJobStatus.FAILED);
        assertThat(saved.getError()).isEqualTo("ollama timeout");
    }

    /** 测试用：通过反射注入 id（BaseEntity 的 id setter 通常不可见）。 */
    private static void setId(AiJob job, Long id) {
        try {
            Field f = job.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(job, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}