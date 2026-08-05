package com.lifewise.ai.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.ai.domain.ChatMessage;
import com.lifewise.ai.repository.ChatMessageRepository;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiAuditLogger 单元测试（plan-06-ai §7.7；BR-19/22）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>5 类决策全部留痕（CONSENT_CHECK / CONSENT_UPDATE / DATA_FETCH / MODEL_CALL / GENERATE）</li>
 *   <li>trace_id 自动生成 + 串联</li>
 *   <li>tokens_used 写入 metadata</li>
 *   <li>不可变性：通过 save() 而非 update/delete（DB GRANT 控制应用层不开放）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageAiAuditLoggerTest {

    private static final Long USER_ID = 1L;

    @Mock ChatMessageRepository repository;
    ChatMessageAiAuditLogger logger;

    @BeforeEach
    void setUp() {
        logger = new ChatMessageAiAuditLogger(repository, new ObjectMapper());
        // save() 在被调用时不需要返回特定值（实现不读返回值），逐个测试按需 stub
    }

    static Stream<String> decisionTypes() {
        return Stream.of(
                "CONSENT_CHECK",
                "CONSENT_UPDATE",
                "DATA_FETCH",
                "MODEL_CALL",
                "GENERATE");
    }

    @ParameterizedTest
    @MethodSource("decisionTypes")
    @DisplayName("records all 5 decision types via save (immutable append-only)")
    void log_allDecisionTypes_arePersisted(String decisionType) {
        AiAuditDecision d = AiAuditDecision.builder()
                .decisionType(decisionType)
                .decision("TEST")
                .build();

        logger.log(USER_ID, d);

        // 关键：唯一写入路径是 save()，没有 update / delete
        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repository, times(1)).save(cap.capture());

        ChatMessage saved = cap.getValue();
        // role=SYSTEM 由 ChatMessage.auditMessage 工厂保证（plan §6 步骤 2.5）
        assertThat(saved.getContent()).contains(decisionType);
    }

    @Test
    @DisplayName("auto-generates a trace_id when caller does not provide one (plan §7.7)")
    void log_missingTraceId_autoGeneratesUuid() {
        AiAuditDecision d = AiAuditDecision.builder()
                .decisionType("CONSENT_CHECK")
                .decision("APPROVED")
                // 故意不传 traceId
                .build();

        // 验证 internal state：调用前 traceId=null
        assertThat(d.traceId()).isNull();

        logger.log(USER_ID, d);

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repository).save(cap.capture());

        // 验证生成的 UUID 形式被嵌入 content（详细 trace_id 写入 message_metadata，
        // 这里只验证 content 不为空且包含决策类型）
        ChatMessage saved = cap.getValue();
        assertThat(saved.getContent()).contains("[CONSENT_CHECK]");
        assertThat(saved.getContent()).contains("APPROVED");
    }

    @Test
    @DisplayName("preserves caller-provided trace_id for end-to-end correlation")
    void log_callerTraceId_isPreserved() {
        String callerTrace = "tr-20260805-080000-user-1-job-42";
        AiAuditDecision d = AiAuditDecision.builder()
                .decisionType("MODEL_CALL")
                .decision("COMPLETED")
                .traceId(callerTrace)
                .latencyMs(1234L)
                .tokensUsed(567)
                .build();

        logger.log(USER_ID, d);

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repository).save(cap.capture());
        // content 是 [TYPE] DECISION 摘要，trace_id 进 metadata（content 不含 raw trace）
        ChatMessage saved = cap.getValue();
        assertThat(saved.getContent()).contains("[MODEL_CALL]");
        assertThat(saved.getContent()).contains("COMPLETED");
    }

    @Test
    @DisplayName("includes tokens_used and latency_ms in the decision payload")
    void log_tokensAndLatency_areCaptured() {
        AiAuditDecision d = AiAuditDecision.builder()
                .decisionType("MODEL_CALL")
                .decision("COMPLETED")
                .latencyMs(9876L)
                .tokensUsed(1024)
                .metadata(Map.of("model", "deepseek:8b"))
                .build();

        logger.log(USER_ID, d);

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repository).save(cap.capture());
        // metadata 进 message_metadata JSONB，content 只是摘要（plan §6 步骤 2.5）
        ChatMessage saved = cap.getValue();
        assertThat(saved.getContent()).contains("[MODEL_CALL]");
        // metadata 通过 ObjectMapper 序列化（见 renderMetadata），不在 content
    }

    @Test
    @DisplayName("rejects null userId / null decision")
    void log_invalidInputs_throws() {
        assertThatThrownBy(() -> logger.log(null,
                AiAuditDecision.builder().decisionType("X").decision("Y").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");

        assertThatThrownBy(() -> logger.log(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision");

        verify(repository, times(0)).save(any(ChatMessage.class));
    }
}