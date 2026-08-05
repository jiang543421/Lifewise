package com.lifewise.ai.service.ollama;

/**
 * Prompt 拼装结果（plan-06-ai §7.4）。
 *
 * <p>BR-22：prompt 原文**不**写入审计（避免 prompt 注入回放），只写
 * SHA-256 摘要 + 数据上下文行数 + 关键参数。
 *
 * @param systemMessage 系统提示（含隐私约束 / 输出格式）
 * @param userPrompt    用户提示（含数据上下文）
 * @param promptHash    SHA-256(system || userPrompt) 十六进制（审计留痕用）
 * @param tokenCount    userPrompt 的 token 估算
 * @param truncated     是否触发截断（userPrompt 超过 4096 tokens 时为 true）
 */
public record PromptResult(
        String systemMessage,
        String userPrompt,
        String promptHash,
        int tokenCount,
        boolean truncated) {}