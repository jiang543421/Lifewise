package com.lifewise.ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * v1.0 白名单 + fail-safe 行为的回归守护（CLAUDE.md §7.3.1）。
 *
 * <p>覆盖 6 分支：missing / blank / valid / 5 个非法 userId（参数化）/ 非数字。
 *
 * <p>对齐 {@code com.lifewise.expense.web.CurrentUserArgumentResolverSecurityTest}
 * 与 task / daily 版本，保证 4 模块 resolver 行为一致。
 *
 * <p>v1.0.3 审计发现：AI 模块 4 个 controller 测试用 {@code @MockBean}
 * 注入 resolver，resolver 自身从未被实例化 → Jacoco 0% 覆盖。本测试
 * 直接 {@code new CurrentUserArgumentResolver()} 触达 5 个 nc 分支。
 */
class CurrentUserArgumentResolverSecurityTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @Test
    void missing_header_defaults_to_user_1_fail_safe() {
        Object result = resolver.resolveArgument(null, null,
                new ServletWebRequest(new MockHttpServletRequest()), null);
        assertThat(result).isEqualTo(1L);
    }

    @Test
    void blank_header_defaults_to_user_1_fail_safe() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "   ");
        Object result = resolver.resolveArgument(null, null,
                new ServletWebRequest(req), null);
        assertThat(result).isEqualTo(1L);
    }

    @Test
    void header_user_1_is_allowed() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "1");
        Object result = resolver.resolveArgument(null, null,
                new ServletWebRequest(req), null);
        assertThat(result).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2", "100", "9223372036854775807"})
    void header_invalid_user_id_is_rejected(String userId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", userId);
        assertThatThrownBy(() -> resolver.resolveArgument(null, null,
                new ServletWebRequest(req), null))
                .isInstanceOf(MissingCurrentUserException.class);
    }

    @Test
    void header_non_numeric_is_rejected() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "abc");
        assertThatThrownBy(() -> resolver.resolveArgument(null, null,
                new ServletWebRequest(req), null))
                .isInstanceOf(MissingCurrentUserException.class);
    }

    @Test
    void header_with_whitespace_is_trimmed_to_user_1() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "  1  ");
        Object result = resolver.resolveArgument(null, null,
                new ServletWebRequest(req), null);
        assertThat(result).isEqualTo(1L);
    }
}