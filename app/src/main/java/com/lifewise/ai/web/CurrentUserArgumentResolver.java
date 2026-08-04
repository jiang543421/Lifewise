package com.lifewise.ai.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * AI 模块 CurrentUser 解析器（v1.0 临时白名单 + nginx 三层防御，详见 CLAUDE.md §7.3.1）。
 *
 * <p>v1.0 个人版永远只有一个 user（userId=1）。鉴权由 nginx 在
 * {@code /api/ai/*} 强制覆盖 {@code X-User-Id=1}，客户端传任何值都会被丢弃。
 *
 * <p>本解析器 = nginx + 应用层双层防御：
 * <ol>
 *   <li>缺失 header → fail-safe 降级到 userId=1（nginx 故障时的兜底）</li>
 *   <li>非数字 / 长度超限 / 非正数 → 401 {@code TOKEN_INVALID}</li>
 * </ol>
 *
 * <p>注：v1.0 不强制 userId=1 白名单（允许任意正整数）— 真实场景下 nginx 已覆盖；
 * 未来切到 v1.1+ 多用户时，本类加 {@code if (parsed != 1) throw} 即可收紧。
 */
@Component("aiCurrentUserArgumentResolver")
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final int MAX_USER_ID_LENGTH = 19;
    private static final Long DEFAULT_USER_ID = 1L;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader("X-User-Id");
        if (header == null || header.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return parseUserId(header);
    }

    private Long parseUserId(String header) {
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
        return parsed;
    }
}