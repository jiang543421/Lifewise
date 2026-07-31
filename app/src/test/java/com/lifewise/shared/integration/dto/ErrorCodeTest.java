package com.lifewise.shared.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ErrorCode 枚举单测。
 *
 * <p>覆盖业务架构 §3.6 + technical-architecture §3.6 锁定的稳定错误码。
 * 新增枚举值不会被覆盖测试拒绝（forward-compat）。
 */
@DisplayName("ErrorCode 稳定错误码")
class ErrorCodeTest {

    @Test
    @DisplayName("技术架构 §3.6 列出的稳定错误码全部存在")
    void should_contain_stable_error_codes() {
        assertThat(ErrorCode.valueOf("INVALID_INPUT")).isNotNull();
        assertThat(ErrorCode.valueOf("NOT_FOUND")).isNotNull();
        assertThat(ErrorCode.valueOf("CROSS_USER_ACCESS")).isNotNull();
        assertThat(ErrorCode.valueOf("VERSION_CONFLICT")).isNotNull();
        assertThat(ErrorCode.valueOf("RATE_LIMITED")).isNotNull();
        assertThat(ErrorCode.valueOf("AI_UNAVAILABLE")).isNotNull();
        assertThat(ErrorCode.valueOf("PUSH_DELIVERED_INAPP")).isNotNull();
        assertThat(ErrorCode.valueOf("INTERNAL_ERROR")).isNotNull();
    }

    @Test
    @DisplayName("错误码为大写 snake_case 字符串（API 信封约定）")
    void error_codes_use_upper_snake_case() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.name())
                    .as("ErrorCode %s 必须全大写下划线", code.name())
                    .matches("[A-Z][A-Z0-9_]*");
        }
    }

    @Test
    @DisplayName("枚举值数量随业务稳定增长（>= 8：technical-architecture §3.6 基线）")
    void at_least_eight_error_codes() {
        assertThat(ErrorCode.values().length).isGreaterThanOrEqualTo(8);
    }
}
