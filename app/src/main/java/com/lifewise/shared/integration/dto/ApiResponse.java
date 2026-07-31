package com.lifewise.shared.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 统一 API 响应信封（CLAUDE.md §4.5 + plan-shared-integration §2.3）。
 *
 * <p>所有 Controller 必须使用 {@link #ok(Object)} / {@link #error(ErrorEnvelope)} / {@link #paged}
 * 三个工厂方法返回，外层不再有其他 JSON 形状。失败时 {@code data=null} + {@code error=...}；
 * 成功时分页接口带 {@code meta}，非分页接口 {@code meta=null}。
 *
 * <p>字段命名与 CLAUDE.md §4.5 / business-architecture §6 全局响应信封一致：
 * <pre>
 * { "success": true,  "data": ..., "error": null,   "meta": null }
 * { "success": false, "data": null,"error": {...},  "meta": null }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"success", "data", "error", "meta"})
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorEnvelope error,
        PageMeta meta) {

    /** 成功非分页响应：{@code success=true}，{@code data} 任意（含 null），{@code meta=null}。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /** 失败响应：{@code success=false}，{@code data=null}，{@code meta=null}。 */
    public static <T> ApiResponse<T> error(ErrorEnvelope error) {
        if (error == null) {
            throw new NullPointerException("error envelope must not be null; use ApiResponse.ok(null) instead");
        }
        return new ApiResponse<>(false, null, error, null);
    }

    /** 成功分页响应：{@code success=true}，{@code data + meta} 同时承载。 */
    public static <T> ApiResponse<T> paged(T data, PageMeta meta) {
        if (meta == null) {
            throw new NullPointerException("page meta must not be null; use ApiResponse.ok(data) instead");
        }
        return new ApiResponse<>(true, data, null, meta);
    }
}
