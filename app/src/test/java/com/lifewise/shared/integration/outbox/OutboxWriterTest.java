package com.lifewise.shared.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.event.payload.TaskCompletedPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;

/**
 * OutboxWriter 单测（plan-shared-integration §5.1 path B）。
 *
 * <p>v1.0 path B 覆盖：
 * <ul>
 *   <li>append → repository.save()，id=null（DB 回填），attemptCount=0，publishedAt=null</li>
 *   <li>payload 经 ObjectMapper 序列化为 JSON 字符串</li>
 *   <li>payload=null → "{}"</li>
 *   <li>correlationId UUID → String 转换</li>
 *   <li>causationId 不入库</li>
 * </ul>
 */
@DisplayName("OutboxWriter 事件写入")
@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock OutboxEventRepository repository;
    @Mock ObjectMapper objectMapper;

    private OutboxWriter writer;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        writer = new OutboxWriter(repository, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg == null) return "{}";
            return "{\"taskId\":99,\"completedAt\":\"2026-07-31T10:00:00Z\"}";
        });
        // repository.save 返回带 id 的 record（模拟 GeneratedKeyHolder）。
        // 注：should_propagate_serialization_failure_as_runtime 不走 save 路径，用 lenient()。
        lenient().when(repository.save(any(OutboxEventRecord.class))).thenAnswer(inv -> {
            OutboxEventRecord r = inv.getArgument(0);
            return new OutboxEventRecord(
                    1L, r.eventType(), r.eventVersion(), r.occurredAt(),
                    r.userId(), r.aggregateType(), r.aggregateId(),
                    r.correlationId(), r.traceId(), r.payload(),
                    r.publishedAt(), r.attemptCount());
        });
    }

    @Test
    @DisplayName("append(EventEnvelope) → repository.save()，id=null 由 DB 回填")
    void should_append_with_pending_status() {
        EventEnvelope env = envelope();

        OutboxEventRecord returned = writer.append(env);

        ArgumentCaptor<OutboxEventRecord> cap = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(repository).save(cap.capture());

        OutboxEventRecord saved = cap.getValue();
        assertThat(saved.id()).isNull();
        assertThat(saved.eventType()).isEqualTo("task.completed");
        assertThat(saved.publishedAt()).isNull();
        assertThat(saved.attemptCount()).isZero();
        assertThat(returned.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("correlationId UUID → String 入库（DB 列是 TEXT）")
    void should_convert_correlation_id_to_string() {
        UUID corr = UUID.fromString("11111111-2222-3333-4444-555555555555");
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(),
                EventType.MILESTONE_COMPLETED.eventType(),
                2,
                OffsetDateTime.parse("2026-07-31T10:00:00Z"),
                42L,
                "milestone",
                7L,
                corr,
                UUID.randomUUID(),                // causationId — 不应入库
                "trace-xyz",
                Map.of("milestoneId", 7L));

        writer.append(env);

        ArgumentCaptor<OutboxEventRecord> cap = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(repository).save(cap.capture());
        OutboxEventRecord saved = cap.getValue();
        assertThat(saved.correlationId()).isEqualTo(corr.toString());
        assertThat(saved.traceId()).isEqualTo("trace-xyz");
    }

    @Test
    @DisplayName("append 多次：每次都走 repository.save()（无幂等抑制）")
    void should_save_each_append_independently() {
        writer.append(envelope());
        writer.append(envelope());
        writer.append(envelope());

        verify(repository, times(3)).save(any(OutboxEventRecord.class));
    }

    @Test
    @DisplayName("payload 经 ObjectMapper.writeValueAsString 序列化为 JSON（非 toString）")
    void should_serialize_payload_via_object_mapper() throws JsonProcessingException {
        writer.append(envelope());

        ArgumentCaptor<OutboxEventRecord> recCap = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(repository).save(recCap.capture());
        assertThat(recCap.getValue().payload())
                .startsWith("{")
                .as("payload 必须是 JSON 字符串，不是 Map.toString() 的 {k=v}");
        assertThat(recCap.getValue().payload()).doesNotContain("=");
    }

    @Test
    @DisplayName("payload=null 时序列化为 '{}'（PG JSONB NOT NULL 默认）")
    void should_serialize_null_payload_as_empty_object() throws JsonProcessingException {
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1L, "task", 1L,
                UUID.randomUUID(), null, "trace",
                null);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        writer.append(env);

        ArgumentCaptor<OutboxEventRecord> recCap = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(repository).save(recCap.capture());
        assertThat(recCap.getValue().payload()).isEqualTo("{}");
    }

    @Test
    @DisplayName("ObjectMapper 序列化失败 → 包装为 RuntimeException（不静默丢消息）")
    void should_propagate_serialization_failure_as_runtime() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> writer.append(envelope()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("payload");
        verify(repository, times(0)).save(any(OutboxEventRecord.class));
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.TASK_COMPLETED.eventType(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                1042L,
                "task",
                99L,
                UUID.randomUUID(),
                null,
                "trace",
                new TaskCompletedPayload(99L, OffsetDateTime.now(ZoneOffset.UTC)).toMap());
    }
}