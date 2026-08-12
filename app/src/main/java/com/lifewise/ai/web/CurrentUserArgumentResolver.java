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

    /** Long.MAX_VALUE 字符数（"9223372036854775807".length()）— 防止 header 长度攻击。 */
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
     * 校验链：缺失 → 默认 ALLOWED_USER_ID（fail-safe）；
     * 长度超限 / 非数字 / 非正数 / 非白名单 → 抛 {@link MissingCurrentUserException} → 401。
     *
     * <p>对齐 {@code com.lifewise.expense.web.CurrentUserArgumentResolver}
     * 的同步契约（CLAUDE.md §7.3.1），保证 6 模块 resolver 行为一致。
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
                    "v1.0 single-user mode: only userId=" + ALLOWED_USER_ID + " is allowed");
        }
        return parsed;
    }
}