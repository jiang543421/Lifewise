package com.lifewise.expense.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新分类请求（plan-03-expense §3.2）。
 *
 * <p>所有字段可选（PATCH 语义）；但 BR-24：默认分类整个请求被拒。
 */
public record CategoryUpdateRequest(
        @Size(min = 1, max = 20)
        String name,
        @Size(max = 50)
        String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be #RRGGBB")
        String color,
        Integer sortOrder,
        Boolean archived) {
}