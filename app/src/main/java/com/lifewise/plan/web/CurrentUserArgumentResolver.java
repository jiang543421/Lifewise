package com.lifewise.plan.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * X-User-Id 头解析（CLAUDE.md §7.3.1 v1.0 白名单方案模板）。
 *
 * <p>v1.0 个人版永远只有 userId=1；本 resolver 复制自 daily 模块首个落地版本（fail-open +
 * {@code ALLOWED_USER_ID=1}）。后续模块接入 @CurrentUser 时复用本模板。
 */
@Component("planCurrentUserArgumentResolver")
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** v1.0 个人版固定 userId（CLAUDE.md §7.3.1）。 */
    private static final long ALLOWED_USER_ID = 1L;

    /** Long.MAX_VALUE 字符数。 */
    private static final int MAX_USER_ID_LENGTH = 19;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return parseUserId(webRequest.getHeader("X-User-Id"));
    }

    /**
     * fail-open 解析链：缺失 → 默认 1（nginx 故障兜底）；非白名单 → 401。
     *
     * <p>缺失头时降级到 ALLOWED_USER_ID=1；非数字 / 长度超限 / 非白名单均抛 MissingCurrentUserException。
     */
    private Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            return ALLOWED_USER_ID;
        }
        String trimmed = header.trim();
        if (trimmed.length() > MAX_USER_ID_LENGTH) {
            throw new MissingCurrentUserException("X-User-Id exceeds max length (19)");
        }
        long parsed;
        try {
            parsed = Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            throw new MissingCurrentUserException("X-User-Id header must be numeric");
        }
        if (parsed <= 0) {
            throw new MissingCurrentUserException("X-User-Id must be positive");
        }
        if (parsed != ALLOWED_USER_ID) {
            throw new MissingCurrentUserException(
                    "v1.0 plan module only allows X-User-Id=" + ALLOWED_USER_ID);
        }
        return parsed;
    }
}