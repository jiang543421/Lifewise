package com.lifewise.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建分类请求（plan-03-expense §3.2）。
 */
public record CategoryCreateRequest(
        @NotBlank
        @Size(min = 1, max = 20)
        String name,
        @Size(max = 50)
        String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be #RRGGBB")
        String color,
        Long parentId,
        Integer sortOrder) {
}