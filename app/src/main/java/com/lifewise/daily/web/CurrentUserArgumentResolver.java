package com.lifewise.daily.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 把 {@link CurrentUser} 解析为 {@code X-User-Id} 头指定的 Long userId。 */
@Component("dailyCurrentUserArgumentResolver")
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

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
            throw new MissingCurrentUserException("X-User-Id header is required");
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ex) {
            throw new MissingCurrentUserException("X-User-Id header must be numeric");
        }
    }
}