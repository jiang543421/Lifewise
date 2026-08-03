package com.lifewise.shared.integration.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SafeMessageSanitizer} 单元测试（commit #8a-1a）。
 *
 * <p>覆盖 6 类场景：安全透传、null/blank 降级、SQL 关键词降级、DB 错误短语降级、
 * 堆栈标记降级、长消息截断。
 */
class SafeMessageSanitizerTest {

    @Test
    void sanitize_safe_message_passes_through() {
        // 安全的中文 + 英文组合，无任何 LEAK_PATTERNS
        assertThat(SafeMessageSanitizer.sanitize("amount must be positive"))
                .isEqualTo("amount must be positive");
        assertThat(SafeMessageSanitizer.sanitize("分类「咖啡」已存在"))
                .isEqualTo("分类「咖啡」已存在");
    }

    @Test
    void sanitize_null_or_blank_returns_fallback() {
        assertThat(SafeMessageSanitizer.sanitize(null))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize(""))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("   "))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
    }

    @Test
    void sanitize_sql_keywords_blocks() {
        // 大小写不敏感检测；命中任一即降级
        assertThat(SafeMessageSanitizer.sanitize("could not execute INSERT ..."))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("Failed: SELECT * FROM users"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("ALTER TABLE foo ..."))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("DROP TABLE expenses"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("DELETE FROM x WHERE ..."))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("TRUNCATE foo"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("CREATE TABLE foo (...)"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
    }

    @Test
    void sanitize_db_error_phrases_blocks() {
        // Hibernate-wrapped SQL 与 PostgreSQL 错误短语
        assertThat(SafeMessageSanitizer.sanitize("duplicate key value violates unique constraint"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("constraint \"uq_expenses_user_date\""))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("new row for relation \"expenses\" violates"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("could not execute statement [INSERT INTO ...]"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
    }

    @Test
    void sanitize_stack_trace_markers_blocks() {
        // Java 堆栈 frame 前缀 + Exception 链
        assertThat(SafeMessageSanitizer.sanitize("Caused by: java.sql.SQLException: ..."))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("oops\n\tat com.foo.Bar.bar(Bar.java:42)"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
        assertThat(SafeMessageSanitizer.sanitize("nested Exception: something"))
                .isEqualTo(SafeMessageSanitizer.FALLBACK);
    }

    @Test
    void sanitize_long_message_truncates() {
        // 300 字符 → 前 200 + "..."（不降级，因无 LEAK_PATTERNS）
        String longMsg = "a".repeat(300);
        String sanitized = SafeMessageSanitizer.sanitize(longMsg);
        assertThat(sanitized)
                .hasSize(SafeMessageSanitizer.MAX_LENGTH + 3)
                .startsWith("a".repeat(SafeMessageSanitizer.MAX_LENGTH))
                .endsWith("...");
    }
}