package com.lifewise.expense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lifewise.expense.domain.ExpenseCategory;

/**
 * 分类视图（plan-03-expense §3.2）。
 *
 * <p>{@code @JsonNaming(SnakeCaseStrategy)} 让 wire JSON 走 snake_case（与 API 风格一致）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CategoryView(
        Long id,
        @JsonProperty("user_id") Long userId,
        String name,
        String icon,
        String color,
        @JsonProperty("parent_id") Long parentId,
        @JsonProperty("sort_order") int sortOrder,
        @JsonProperty("is_archived") boolean archived,
        @JsonProperty("is_user_default") boolean userDefault,
        @JsonProperty("is_system") boolean system) {

    public static CategoryView from(ExpenseCategory c) {
        return new CategoryView(
                c.getId(),
                c.getUserId(),
                c.getName(),
                c.getIcon(),
                c.getColor(),
                c.getParentId(),
                c.getSortOrder(),
                c.isArchived(),
                c.isUserDefault(),
                c.isSystem());
    }
}