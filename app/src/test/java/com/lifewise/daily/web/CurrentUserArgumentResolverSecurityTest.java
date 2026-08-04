package com.lifewise.daily.web;

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
 * <p>覆盖 6 个分支：missing / valid / 5 个非法 userId（参数化）/ 非数字 / blank /
 * 前后空白 trim。
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
    void header_user_1_is_allowed() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "1");
        Object result = resolver.resolveArgument(null, null,
                new ServletWebRequest(req), null);
        assertThat(result).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100", "9999", "9223372036854775807"})
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