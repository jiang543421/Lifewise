package com.lifewise.ai.service.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PromptBuilder 单元测试（plan-06-ai §7.4；BR-22）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>系统提示含「不返回敏感字段」</li>
 *   <li>数据上下文注入 userPrompt</li>
 *   <li>超 4096 tokens 触发截断</li>
 *   <li>SHA-256 hash 一致性 + 不存原文</li>
 * </ol>
 */
class PromptBuilderTest {

    PromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PromptBuilder();
    }

    @Test
    @DisplayName("system message contains the privacy notice")
    void build_privacyNotice_isInSystemMessage() {
        PromptResult r = builder.build("DAILY_SUMMARY", List.of(), Map.of());

        assertThat(r.systemMessage()).contains("不要返回敏感字段");
        assertThat(r.systemMessage()).contains("隐私约束");
    }

    @Test
    @DisplayName("data rows are injected into user prompt")
    void build_dataContext_isInUserPrompt() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("title", "buy milk");
        row1.put("status", "DONE");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("title", "send report");
        row2.put("status", "PENDING");

        Map<String, Object> params = Map.of("period_from", "2026-07-01", "period_to", "2026-07-31");

        PromptResult r = builder.build("DAILY_SUMMARY", List.of(row1, row2), params);

        assertThat(r.userPrompt()).contains("【报告类型】DAILY_SUMMARY");
        assertThat(r.userPrompt()).contains("【参数】");
        assertThat(r.userPrompt()).contains("- period_from: 2026-07-01");
        assertThat(r.userPrompt()).contains("- period_to: 2026-07-31");
        assertThat(r.userPrompt()).contains("buy milk");
        assertThat(r.userPrompt()).contains("send report");
        assertThat(r.userPrompt()).contains("共 2 条");
        assertThat(r.truncated()).isFalse();
    }

    @Test
    @DisplayName("truncates user prompt when over 4096 tokens")
    void build_overTokenLimit_truncates() {
        // 构造一个超长 row（每条 ~10000 字符 ≈ 2500 tokens；造 5 条 ≈ 12500 tokens）
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 10_000; i++) big.append('x');
        Map<String, Object> hugeRow = Map.of("blob", big.toString());
        List<Map<String, Object>> rows = List.of(hugeRow, hugeRow, hugeRow, hugeRow, hugeRow);

        PromptResult r = builder.build("DAILY_SUMMARY", rows, Map.of());

        assertThat(r.tokenCount()).isEqualTo(PromptBuilder.MAX_TOKENS);
        assertThat(r.truncated()).isTrue();
        assertThat(r.userPrompt()).contains("[truncated]");
        // 截断后字符数不应超过 MAX_TOKENS * 4 + 余量
        assertThat(r.userPrompt().length()).isLessThanOrEqualTo(
                PromptBuilder.MAX_TOKENS * PromptBuilder.CHARS_PER_TOKEN + 32);
    }

    @Test
    @DisplayName("computes a stable SHA-256 hash for audit (no raw prompt stored)")
    void build_promptHash_isDeterministicAndNotNull() {
        Map<String, Object> row = Map.of("title", "task-1");
        Map<String, Object> params = Map.of("k", "v");

        PromptResult a = builder.build("DAILY_SUMMARY", List.of(row), params);
        PromptResult b = builder.build("DAILY_SUMMARY", List.of(row), params);

        // 同输入 → 同 hash（确定性）
        assertThat(a.promptHash()).isEqualTo(b.promptHash());
        assertThat(a.promptHash()).hasSize(64); // SHA-256 hex
        assertThat(a.promptHash()).matches("[0-9a-f]{64}");

        // 不同输入 → 不同 hash
        PromptResult c = builder.build("WEEKLY_SUMMARY", List.of(row), params);
        assertThat(c.promptHash()).isNotEqualTo(a.promptHash());
    }

    @Test
    @DisplayName("handles empty rows gracefully")
    void build_emptyRows_rendersNoData() {
        PromptResult r = builder.build("DAILY_SUMMARY", List.of(), Map.of());

        assertThat(r.userPrompt()).contains("（无数据）");
        assertThat(r.tokenCount()).isGreaterThan(0); // 系统提示 + 框架也算 token
        assertThat(r.truncated()).isFalse();
    }
}