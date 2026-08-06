package com.lifewise.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.ai.domain.AiJob;
import com.lifewise.ai.domain.enums.AiJobStatus;
import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.repository.AiJobRepository;
import com.lifewise.ai.service.audit.AiAuditLogger;
import com.lifewise.ai.service.exception.OllamaUnavailableException;
import com.lifewise.ai.service.ollama.GenerationResult;
import com.lifewise.ai.service.ollama.OllamaClient;
import com.lifewise.ai.service.ollama.PromptBuilder;
import com.lifewise.ai.service.ollama.PromptResult;
import com.lifewise.ai.service.scope.ScopedDataFetcher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
 * AiJobProcessor 单元测试（plan-06-ai §7.6 + §7.6.1 X3 三态）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>DONE — Ollama OK + 数据齐 → markDone + outbox final_status=DONE</li>
 *   <li>DONE_NO_LLM — Ollama 红色态 → markDoneNoLlm + outbox final_status=DONE_NO_LLM</li>
 *   <li>DONE_PARTIAL — fetch 失败 → markDonePartial + outbox final_status=DONE_PARTIAL</li>
 *   <li>FAILED — 真正异常（catch-all） → markFailed + 不发 outbox</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AiJobProcessorTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_ID = 10L;
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    @Mock AiJobService jobService;
    @Mock AiReportService reportService;
    @Mock ScopedDataFetcher dataFetcher;
    @Mock PromptBuilder promptBuilder;
    @Mock OllamaClient ollamaClient;
    @Mock AiAuditLogger auditLogger;
    @Mock OutboxWriter outboxWriter;
    @Mock AiJobRepository jobRepository;

    AiJobProcessor processor;

    @BeforeEach
    void setUp() {
        // Fixed clock
        AtomicLong now = new AtomicLong(Instant.parse("2026-08-05T08:00:00Z").toEpochMilli());
        Clock clock = new Clock() {
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            public long millis() { return now.get(); }
        };

        processor = new AiJobProcessor(
                jobService, reportService, dataFetcher, promptBuilder,
                ollamaClient, auditLogger, outboxWriter, new ObjectMapper(), clock);
    }

    @Test
    @DisplayName("Ollama OK + data fetched -> DONE + outbox final_status=DONE")
    void pipeline_ollamaOk_emitsDone() {
        AiJob job = newPendingJob(JOB_ID, USER_ID, AiJobType.DAILY_SUMMARY);
        when(jobService.findById(eq(JOB_ID), any())).thenReturn(Optional.of(job));
        when(jobService.loadOrThrow(eq(JOB_ID), eq(USER_ID))).thenReturn(job);
        when(dataFetcher.fetch(eq(USER_ID), eq(AiJobType.DAILY_SUMMARY),
                eq("tasks"), any(), eq(FROM), eq(TO)))
                .thenReturn(List.of(Map.of("title", "buy milk")));
        when(promptBuilder.build(eq("DAILY_SUMMARY"), any(), any()))
                .thenReturn(new PromptResult("system", "user", "hash-123", 100, false));
        when(ollamaClient.generate(anyString()))
                .thenReturn(new GenerationResult("# daily report", 1234L, 567));

        processor.runPipeline(JOB_ID);

        // 终态 = DONE
        verify(jobService).markDone(eq(JOB_ID), eq(USER_ID), eq("deepseek:8b"), eq(567), anyString());
        verify(jobService, never()).markDoneNoLlm(anyLong(), anyLong(), anyString());
        verify(jobService, never()).markDonePartial(anyLong(), anyLong(), anyString());

        // outbox 发出 final_status=DONE
        ArgumentCaptor<EventEnvelope> envCap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(envCap.capture());
        assertThat(envCap.getValue().eventType()).isEqualTo("ai.job.completed");
        assertThat(envCap.getValue().payload()).containsEntry("final_status", "DONE");
        assertThat(envCap.getValue().payload()).containsEntry("latency_ms", 1234L);
    }

    @Test
    @DisplayName("Ollama unavailable -> DONE_NO_LLM + outbox final_status=DONE_NO_LLM")
    void pipeline_ollamaUnavailable_emitsDoneNoLlm() {
        AiJob job = newPendingJob(JOB_ID, USER_ID, AiJobType.WEEKLY_SUMMARY);
        when(jobService.findById(eq(JOB_ID), any())).thenReturn(Optional.of(job));
        when(jobService.loadOrThrow(eq(JOB_ID), eq(USER_ID))).thenReturn(job);
        when(dataFetcher.fetch(anyLong(), any(), anyString(), any(), any(), any()))
                .thenReturn(List.of(Map.of("title", "task-1")));
        when(promptBuilder.build(anyString(), any(), any()))
                .thenReturn(new PromptResult("system", "user", "hash-456", 50, false));
        when(ollamaClient.generate(anyString()))
                .thenThrow(new OllamaUnavailableException("deepseek:8b timeout"));

        processor.runPipeline(JOB_ID);

        verify(jobService).markDoneNoLlm(eq(JOB_ID), eq(USER_ID), anyString());
        verify(jobService, never()).markDone(anyLong(), anyLong(), anyString(), any(), anyString());
        verify(jobService, never()).markDonePartial(anyLong(), anyLong(), anyString());

        ArgumentCaptor<EventEnvelope> envCap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(envCap.capture());
        assertThat(envCap.getValue().payload()).containsEntry("final_status", "DONE_NO_LLM");
    }

    @Test
    @DisplayName("data fetch throws -> DONE_PARTIAL + outbox final_status=DONE_PARTIAL")
    void pipeline_fetchFailed_emitsDonePartial() {
        AiJob job = newPendingJob(JOB_ID, USER_ID, AiJobType.DAILY_SUMMARY);
        when(jobService.findById(eq(JOB_ID), any())).thenReturn(Optional.of(job));
        when(jobService.loadOrThrow(eq(JOB_ID), eq(USER_ID))).thenReturn(job);
        when(dataFetcher.fetch(anyLong(), any(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        // pipeline 在 fetch 失败后应能继续调用 ollama（partial 模式）
        when(promptBuilder.build(anyString(), any(), any()))
                .thenReturn(new PromptResult("system", "user", "hash-789", 30, false));
        when(ollamaClient.generate(anyString()))
                .thenReturn(new GenerationResult("# partial report", 800L, 100));

        processor.runPipeline(JOB_ID);

        verify(jobService).markDonePartial(eq(JOB_ID), eq(USER_ID), anyString());
        verify(jobService, never()).markDoneNoLlm(anyLong(), anyLong(), anyString());

        ArgumentCaptor<EventEnvelope> envCap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(envCap.capture());
        assertThat(envCap.getValue().payload()).containsEntry("final_status", "DONE_PARTIAL");
    }

    @Test
    @DisplayName("processAsync catch-all: unexpected exception -> FAILED + no outbox")
    void processAsync_unexpectedError_marksFailed() {
        AiJob job = newPendingJob(JOB_ID, USER_ID, AiJobType.DAILY_SUMMARY);
        when(jobService.findById(eq(JOB_ID), any())).thenReturn(Optional.of(job));
        when(jobService.loadOrThrow(eq(JOB_ID), eq(USER_ID))).thenReturn(job);
        when(dataFetcher.fetch(anyLong(), any(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));
        when(promptBuilder.build(anyString(), any(), any()))
                .thenThrow(new RuntimeException("prompt builder crashed"));

        // runPipeline 内的 catch 不到（不在 processAsync 的 catch 内），
        // 但 processAsync 的兜底 catch 会处理
        processor.processAsync(JOB_ID);

        // 关键：不发 outbox（FAILED 不算 "completed"）
        verify(outboxWriter, never()).append(any(EventEnvelope.class));
    }

    /** 测试用：构造带 id 的 PENDING job。 */
    private static AiJob newPendingJob(Long id, Long userId, AiJobType type) {
        AiJob job = AiJob.createPending(userId, type, OffsetDateTime.now(), FROM, TO);
        try {
            Field f = AiJob.class.getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(job, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return job;
    }
}