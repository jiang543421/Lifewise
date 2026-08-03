package com.lifewise.expense.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 消费分类（plan-03-expense §2.2 + V6/V37 expense_categories DDL）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-23：name 在用户内唯一 / 系统级全局唯一（partial unique index）</li>
 *   <li>BR-24：{@code is_user_default = TRUE} 的分类不可改/删（partial unique + 应用层守卫）</li>
 *   <li>BR-20：删除分类前先把账目迁移到「其他」</li>
 * </ul>
 *
 * <p>{@code user_id IS NULL} 表示系统默认分类（V14 种子），可见但不可改/删。
 */
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "icon")
    private String icon;

    @Column(name = "color")
    private String color;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @Column(name = "is_user_default", nullable = false)
    private boolean userDefault;

    protected ExpenseCategory() {
        // JPA
    }

    private ExpenseCategory(Long userId, String name, String icon, String color,
                             Long parentId, int sortOrder,
                             boolean archived, boolean userDefault) {
        this.userId = userId;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.archived = archived;
        this.userDefault = userDefault;
    }

    /** 用户创建自定义分类。 */
    public static ExpenseCategory createUserCategory(Long userId, String name, String icon,
                                                     String color, Long parentId, int sortOrder) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required for user category");
        }
        validateName(name);
        return new ExpenseCategory(userId, name, icon, color, parentId, sortOrder, false, false);
    }

    /** 系统默认分类（{@code is_user_default = TRUE}，BR-24 保护）。 */
    public static ExpenseCategory createUserDefault(Long userId, String name, String icon,
                                                    String color, int sortOrder) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required for user default");
        }
        validateName(name);
        return new ExpenseCategory(userId, name, icon, color, null, sortOrder, false, true);
    }

    /** 系统种子分类（{@code user_id = NULL}）。 */
    public static ExpenseCategory createSystem(String name, String icon, String color,
                                               Long parentId, int sortOrder) {
        validateName(name);
        return new ExpenseCategory(null, name, icon, color, parentId, sortOrder, false, false);
    }

    /** 应用层更新（name/icon/color/sortOrder）。BR-24：默认分类不可改。 */
    public void applyUpdate(String name, String icon, String color, int sortOrder) {
        if (userDefault) {
            throw new IllegalStateException("default category is immutable");
        }
        if (name != null && !name.isBlank()) {
            validateName(name);
            this.name = name.trim();
        }
        if (icon != null) {
            this.icon = icon;
        }
        if (color != null) {
            this.color = color;
        }
        this.sortOrder = sortOrder;
    }

    /** 归档（BR-24：默认分类不可归档）。 */
    public void archive() {
        if (userDefault) {
            throw new IllegalStateException("default category cannot be archived");
        }
        this.archived = true;
    }

    /** 解除归档。 */
    public void unarchive() {
        this.archived = false;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > 20) {
            throw new IllegalArgumentException("name must be <= 20 chars");
        }
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public Long getParentId() { return parentId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isArchived() { return archived; }
    public boolean isUserDefault() { return userDefault; }
    public boolean isSystem() { return userId == null; }
    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }
}