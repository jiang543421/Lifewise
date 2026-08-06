package com.lifewise.ai.service.ollama;

/**
 * Ollama 生成结果（plan-06-ai §7.5）。
 *
 * @param content    模型输出（Markdown 文本）
 * @param latencyMs  从第 1 次尝试到最终响应的累计耗时
 * @param tokensUsed 估算 token 数（响应 JSON 的 {@code eval_count}；解析失败时为 0）
 */
public record GenerationResult(String content, long latencyMs, long tokensUsed) {}