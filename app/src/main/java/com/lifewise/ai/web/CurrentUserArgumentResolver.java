package com.lifewise.ai.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * v1.0 个人版白名单解析：仅允许 userId=1（CLAUDE.md §7.3.1）。
 *
 * <p>v1.0.3 落地：AI 模块独立 resolver（切断 task.web 依赖），对齐 daily/expense
 * 同款模板（fail-open + ALLOWED_USER_ID=1）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>nginx 强制覆盖 X-User-Id=1（{@code nginx/conf/conf.d/default.conf}），
 *       客户端无法伪造</li>
 *   <li>应用层兜底：non-numeric / 非 1 一律抛
 *       {@link MissingCurrentUserException} → 401</li>
 *   <li>missing / blank header 降级到 userId=1（nginx 故障时的 fail-safe）</li>
 * </ul>
 *
 * <p>演进路径：v1.1+ 切换多用户时删除 {@code ALLOWED_USER_ID} 白名单，改读
 * SecurityContext 中的 JWT principal。
 */
@Component("aiCurrentUserArgumentResolver")
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** v1.0 个人版白名单：仅允许 userId=1。 */
    private static final long ALLOWED_USER_ID = 1L;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader("X-User-Id");
        // missing / blank → fail-open 降级到 userId=1（nginx 故障兜底）
        if (header == null || header.isBlank()) {
            return ALLOWED_USER_ID;
        }
        long userId;
        try {
            userId = Long.parseLong(header.trim());
        } catch (NumberFormatException ex) {
            throw new MissingCurrentUserException("X-User-Id header must be numeric");
        }
        // 白名单校验：非 1 一律拒绝（客户端伪造防御）
        if (userId != ALLOWED_USER_ID) {
            throw new MissingCurrentUserException(
                    "v1.0 single-user mode: only userId=" + ALLOWED_USER_ID + " is allowed");
        }
        return userId;
    }
}