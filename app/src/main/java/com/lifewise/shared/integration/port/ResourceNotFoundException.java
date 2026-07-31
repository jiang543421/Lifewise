package com.lifewise.shared.integration.port;

import java.util.Objects;

/**
 * 跨模块只读端口找不到资源时的统一异常（plan-shared-integration §2.2）。
 *
 * <p>用法：
 * <ul>
 *   <li>{@code new ResourceNotFoundException("task", 99L)} — 用户对自有资源查不到</li>
 *   <li>{@code new ResourceNotFoundException("task", 99L, true)} — 跨用户枚举攻击场景</li>
 *   <li>{@code new ResourceNotFoundException("tag", "tag 不存在", null)} — by-name 场景</li>
 * </ul>
 *
 * <p>语义约束：{@code message} 必须包含 {@code resourceType} 与资源标识（ID 或 override）。
 * 上层 handler 把 {@code forUser=true} 映射为审计告警但不暴露存在性信息。
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Long resourceId;
    private final Boolean forUser;

    public ResourceNotFoundException(String resourceType, Long resourceId) {
        this(resourceType, defaultMessage(resourceType, resourceId), resourceId, null);
    }

    public ResourceNotFoundException(String resourceType, Long resourceId, boolean forUser) {
        this(resourceType, forUserMessage(resourceType, resourceId), resourceId, forUser);
    }

    public ResourceNotFoundException(String resourceType, String messageOverride, Long resourceId) {
        this(resourceType, messageOverride, resourceId, null);
    }

    private ResourceNotFoundException(
            String resourceType, String message, Long resourceId, Boolean forUser) {
        super(message);
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.resourceId = resourceId;
        this.forUser = forUser;
    }

    public String resourceType() {
        return resourceType;
    }

    public Long resourceId() {
        return resourceId;
    }

    /** null = 默认 NOT_FOUND；true = 跨用户访问（审计）；false 保留。 */
    public Boolean forUser() {
        return forUser;
    }

    private static String defaultMessage(String type, Long id) {
        return id == null ? type + " not found" : type + " not found: id=" + id;
    }

    private static String forUserMessage(String type, Long id) {
        return id == null
                ? type + " CROSS_USER_ACCESS"
                : type + " CROSS_USER_ACCESS: id=" + id;
    }
}
