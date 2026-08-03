package com.lifewise.task.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 把 {@link CurrentUser} 解析为 {@code X-User-Id} 头指定的 Long userId。
 *
 * <p><b>防御层（Phase A）</b>：格式校验（numeric + 长度 ≤ 19 + 严格正整数）。
 * 拒绝任何缺失 / 非数字 / 长度超过 Long.MAX_VALUE 字符数（19）/ 非正整数的输入，
 * 由 {@link com.lifewise.task.controller.TaskGlobalExceptionHandler} 映射为
 * {@code 401 TOKEN_INVALID} envelope。
 *
 * <p><b>同步契约</b>：本类实现必须与
 * {@code com.lifewise.expense.web.CurrentUserArgumentResolver.parseUserId}
 * 保持完全一致（错误消息、异常类型、校验顺序）。后续抽取见 plan-03 §12。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** Long.MAX_VALUE 字符数（"9223372036854775807".length()）。 */
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
     * 解析 {@code X-User-Id} 头为 Long。校验链：缺失 → 长度超限 → 非数字 → 非正数。
     * 任意校验失败抛 {@link MissingCurrentUserException} → 401 TOKEN_INVALID。
     *
     * <p>必须与 {@code com.lifewise.expense.web.CurrentUserArgumentResolver.parseUserId}
     * 保持完全一致（错误消息、异常类型、校验顺序）。后续抽取见 plan-03 §12。
     *
     * <p><b>已知限制</b>：{@link MissingCurrentUserException} 也用于非法格式
     * （非数字 / 长度超限 / 非正数），源于历史命名约定。
     * Phase B issue C3-nginx 落地后，可统一重命名为 {@code InvalidCurrentUserException}，
     * 同步更新 envelope 错误码链路上的所有 handler / 测试 / 文档。
     */
    private Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            throw new MissingCurrentUserException("X-User-Id header is required");
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
        return parsed;
    }
}
