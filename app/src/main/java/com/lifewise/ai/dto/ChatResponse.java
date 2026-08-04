package com.lifewise.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI 问答响应（plan §2.3）。
 *
 * @param source       "RULE" 或 "LLM"，标识双路径
 * @param answerMd     答案 Markdown
 * @param evidenceRefs 引用来源 ID 列表
 * @param sql          LLM 路径下生成的 SQL（可空；RULE 路径为 null）
 * @param conversationId 会话 ID（V25 nullable；ad-hoc 问询时为 null）
 */
public record ChatResponse(
        @JsonProperty("answer_md") String answerMd,
        String source,
        @JsonProperty("evidence_refs") List<Long> evidenceRefs,
        String sql,
        @JsonProperty("conversation_id") Long conversationId) {
}