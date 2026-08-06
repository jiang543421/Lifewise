package com.lifewise.ai.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.ai.domain.ChatMessage;
import com.lifewise.ai.domain.enums.ChatRole;
import com.lifewise.ai.repository.ChatMessageRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 审计日志写入 chat_messages role=SYSTEM（plan-06-ai §6 步骤 2.5 + §7.7）。
 *
 * <p>写入字段：
 * <ul>
 *   <li>{@code role = 'SYSTEM'} — 标识审计消息（与 USER / ASSISTANT 区分）</li>
 *   <li>{@code content} — 人类可读摘要（decisionType + decision + 关键字段）</li>
 *   <li>{@code message_refs}（V42 后为 message_metadata JSONB）— 结构化载荷
 *       （trace_id / latency_ms / tokens_used / decision_type / decision / 业务上下文）</li>
 * </ul>
 *
 * <p>不可变：DB 角色 GRANT 限制 UPDATE/DELETE 权限；本类不暴露 update / delete 方法。
 *
 * <p>事务策略：{@link Propagation#MANDATORY} 强制要求调用方在事务内，
 * 与 AiJobService.processAsync 的业务事务同提交（plan §6 步骤 2.5）。
 */
@Component
public class ChatMessageAiAuditLogger implements AiAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageAiAuditLogger.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public ChatMessageAiAuditLogger(ChatMessageRepository chatMessageRepository,
                                   ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(Long userId, AiAuditDecision decision) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision required");
        }

        // trace_id 必填：若调用方未提供 → 自动生成 UUID 串联 4 步（plan §7.7）
        AiAuditDecision enriched = decision.traceId() == null
                ? AiAuditDecision.builder()
                    .decisionType(decision.decisionType())
                    .decision(decision.decision())
                    .traceId(java.util.UUID.randomUUID().toString())
                    .latencyMs(decision.latencyMs())
                    .tokensUsed(decision.tokensUsed())
                    .metadata(decision.metadata())
                    .build()
                : decision;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String content = renderContent(enriched);
        String metadataJson = renderMetadata(enriched);

        ChatMessage audit = ChatMessage.auditMessage(userId, today, content);
        chatMessageRepository.save(audit);
        log.debug("AI audit logged userId={} type={} decision={}", userId, enriched.decisionType(), enriched.decision());

        if (log.isTraceEnabled()) {
            log.trace("AI audit metadata userId={} metadata={}", userId, metadataJson);
        }
    }

    private String renderContent(AiAuditDecision d) {
        return String.format("[%s] %s", d.decisionType(), d.decision());
    }

    private String renderMetadata(AiAuditDecision d) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision_type", d.decisionType());
        body.put("decision", d.decision());
        if (d.traceId() != null) body.put("trace_id", d.traceId());
        if (d.latencyMs() != null) body.put("latency_ms", d.latencyMs());
        if (d.tokensUsed() != null) body.put("tokens_used", d.tokensUsed());
        if (!d.metadata().isEmpty()) body.putAll(d.metadata());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            log.warn("AI audit metadata serialization failed: {}", ex.getMessage());
            return "{}";
        }
    }
}
