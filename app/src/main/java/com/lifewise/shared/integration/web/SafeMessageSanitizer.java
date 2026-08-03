package com.lifewise.shared.integration.web;

/**
 * 把 service 层 exception message 转为 envelope-safe 字符串
 * （plan-03 review MEDIUM-HIGH：防御 envelope 透传 SQL/堆栈）。
 *
 * <p>设计原则（CLAUDE.md §7.5 + business-architecture §6.8）：
 * <ul>
 *   <li>不抛错（不替换成 unknown 让前端无法调试）</li>
 *   <li>检测 SQL 关键词 / DB 错误短语 / Java 堆栈标记，命中即降级为通用 message</li>
 *   <li>短消息（≤ 200 字符）视为可信透传，长消息截断</li>
 *   <li>纯函数，无 Spring 依赖，可单测</li>
 * </ul>
 *
 * <p>使用方：{@code GlobalExceptionHandler.envelope()} 工具方法统一调用，
 * 替换原 {@code ex.getMessage()} 透传。
 *
 * @since commit #8a-1a（plan-03 review MEDIUM-HIGH envelope leak）
 */
public final class SafeMessageSanitizer {

    /** 触发降级的关键词（大小写不敏感）；命中任一即视为 leak。 */
    private static final String[] LEAK_PATTERNS = {
            "INSERT ", "UPDATE ", "DELETE FROM", "SELECT ", "ALTER ",
            "DROP ", "TRUNCATE ", "CREATE TABLE",
            "duplicate key", "constraint \"", "violates",
            "could not execute statement", "Caused by:",
            "\tat ",   // stack frame prefix
            "Exception:"
    };

    /** 透传长度上限。超过则截断 + 后缀 "..."。 */
    static final int MAX_LENGTH = 200;

    /** 降级 fallback 文案。 */
    static final String FALLBACK = "request failed";

    private SafeMessageSanitizer() {}

    /**
     * 返回 envelope-safe message。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code raw == null/blank} → {@link #FALLBACK}</li>
     *   <li>raw 含 {@link #LEAK_PATTERNS} 任一（大小写不敏感）→ {@link #FALLBACK}</li>
     *   <li>raw 长度 > {@link #MAX_LENGTH} → 前 {@link #MAX_LENGTH} 字符 + "..."</li>
     *   <li>否则 → 原样返回</li>
     * </ul>
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return FALLBACK;
        }
        String upper = raw.toUpperCase();
        for (String pattern : LEAK_PATTERNS) {
            if (upper.contains(pattern.toUpperCase())) {
                return FALLBACK;
            }
        }
        if (raw.length() > MAX_LENGTH) {
            return raw.substring(0, MAX_LENGTH) + "...";
        }
        return raw;
    }
}