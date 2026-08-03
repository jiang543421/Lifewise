package com.lifewise.diet.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 {@link CurrentUser} 注解。
 *
 * <p>v1.0 单一用户白名单（CLAUDE.md §7.3.1）：仅允许 userId=1。
 * nginx 在 {@code /api/} 下强制覆盖 X-User-Id=1，缺少头时 fail-open
 * 降级为 1；非白名单值直接抛 {@link MissingCurrentUserException}。
 *
 * <p>模板来源：{@code com.lifewise.daily.web.CurrentUserArgumentResolver}。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** v1.0 个人版唯一允许的 userId。 */
    public static final Long ALLOWED_USER_ID = 1L;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String header = request == null ? null : request.getHeader("X-User-Id");
        if (header == null || header.isBlank()) {
            // fail-open：nginx 故障兜底
            return ALLOWED_USER_ID;
        }
        try {
            Long parsed = Long.parseLong(header.trim());
            if (!ALLOWED_USER_ID.equals(parsed)) {
                throw new MissingCurrentUserException(
                        "X-User-Id whitelist violation: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new MissingCurrentUserException(
                    "X-User-Id must be a number, got: " + header);
        }
    }
}