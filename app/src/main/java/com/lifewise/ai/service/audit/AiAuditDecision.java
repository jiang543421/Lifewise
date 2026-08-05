package com.lifewise.ai.service.audit;

import java.util.Collections;
import java.util.Map;

/**
 * AI 审计决策记录（plan-06-ai §7.7；BR-19/22）。
 *
 * <p>不可变值类型，4 类决策必留痕：
 * <ul>
 *   <li>CONSENT_CHECK — ai_consent 校验（APPROVED / DENIED）</li>
 *   <li>CONSENT_UPDATE — POST /api/ai/consent（GRANTED / REVOKED）</li>
 *   <li>DATA_FETCH — ScopedDataFetcher 拉取源数据（SUCCESS / PARTIAL / FAILED）</li>
 *   <li>MODEL_CALL — OllamaClient.generate 调用（STARTED / COMPLETED / FAILED / TIMEOUT）</li>
 *   <li>GENERATE — 报告生成结果（SUCCESS / DONE_NO_LLM / DONE_PARTIAL / FAILED）</li>
 * </ul>
 *
 * <p>由 {@link AiAuditLogger#log(Long, AiAuditDecision)} 写入 chat_messages
 * 的 role=SYSTEM 行 + message_metadata JSONB。
 */
public record AiAuditDecision(
        String decisionType,
        String decision,
        String traceId,
        Long latencyMs,
        Integer tokensUsed,
        Map<String, Object> metadata) {

    public AiAuditDecision {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String decisionType;
        private String decision;
        private String traceId;
        private Long latencyMs;
        private Integer tokensUsed;
        private Map<String, Object> metadata = Map.of();

        public Builder decisionType(String v) { this.decisionType = v; return this; }
        public Builder decision(String v) { this.decision = v; return this; }
        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder latencyMs(Long v) { this.latencyMs = v; return this; }
        public Builder tokensUsed(Integer v) { this.tokensUsed = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public AiAuditDecision build() {
            return new AiAuditDecision(decisionType, decision, traceId, latencyMs, tokensUsed, metadata);
        }
    }
}
