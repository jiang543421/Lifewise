package com.lifewise.ai.service.ollama;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Prompt 拼装器（plan-06-ai §7.4；BR-22）。
 *
 * <p><b>关键约束</b>：
 * <ol>
 *   <li>系统提示必须包含「不返回敏感字段」声明（plan §2.2 隐私约束）</li>
 *   <li>数据上下文由 {@link com.lifewise.ai.service.scope.ScopedDataFetcher} 取数后传入，
 *       本类只负责渲染（不查数据库）</li>
 *   <li>超 4096 tokens → 截断并标记 {@code truncated=true}</li>
 *   <li>审计只写 SHA-256(prompt)，不存原文（避免 prompt 注入回放）</li>
 * </ol>
 *
 * <p><b>设计取舍</b>：token 估算用字符数 / 4（中英文混合的粗估；deepseek:8b
 * tokenizer 在 v1.1 接续时再切真实计数）。
 */
@Component
public class PromptBuilder {

    /**
     * 4096 tokens 是 deepseek:8b 8k context 留 50% 给输出 + system prompt 的上限。
     */
    static final int MAX_TOKENS = 4096;

    /** 每 4 个字符估算 1 token（中文 / emoji 略低估，但不会超 4096 即可）。 */
    static final int CHARS_PER_TOKEN = 4;

    private static final String PRIVACY_NOTICE =
            "你是一个本地生活的智能助手。\n"
          + "【隐私约束 - 必须遵守】\n"
          + "1. 不要返回敏感字段（密码 / token / 邮箱 / 手机号 / 证件号等）。\n"
          + "2. 只基于下方提供的「数据上下文」作答，不要编造未提供的事实。\n"
          + "3. 输出 Markdown 格式。\n";

    public PromptResult build(String reportType,
                              List<Map<String, Object>> rows,
                              Map<String, Object> params) {
        if (reportType == null || reportType.isBlank()) {
            throw new IllegalArgumentException("reportType required");
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows required");
        }

        String userPrompt = renderUserPrompt(reportType, rows, params);
        boolean truncated = false;
        int tokens = estimateTokens(userPrompt);
        if (tokens > MAX_TOKENS) {
            userPrompt = truncate(userPrompt, MAX_TOKENS);
            tokens = MAX_TOKENS;
            truncated = true;
        }

        String hash = sha256(PRIVACY_NOTICE + userPrompt);
        return new PromptResult(PRIVACY_NOTICE, userPrompt, hash, tokens, truncated);
    }

    private String renderUserPrompt(String reportType,
                                    List<Map<String, Object>> rows,
                                    Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("【报告类型】").append(reportType).append('\n');
        if (params != null && !params.isEmpty()) {
            sb.append("【参数】\n");
            params.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        }
        sb.append("【数据上下文】（共 ").append(rows.size()).append(" 条）\n");
        if (rows.isEmpty()) {
            sb.append("（无数据）\n");
        } else {
            int idx = 1;
            for (Map<String, Object> row : rows) {
                sb.append(idx++).append(". ");
                row.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
                sb.append('\n');
            }
        }
        sb.append("请基于以上数据生成报告。");
        return sb.toString();
    }

    int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    private String truncate(String text, int maxTokens) {
        int maxChars = maxTokens * CHARS_PER_TOKEN;
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[truncated]";
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}