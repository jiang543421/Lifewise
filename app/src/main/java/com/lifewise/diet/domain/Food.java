package com.lifewise.diet.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 食物实体（plan-04-diet §3 + V7 §9.1 foods）。
 *
 * <p>{@code userId == null} 表示系统预置食物（不可改/删）。
 *
 * <p>BR-13：营养字段 {@code >= 0}。
 */
@Entity
@Table(name = "foods")
public class Food extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "kcal_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal kcalPer100g;

    @Column(name = "protein_g_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinGPer100g;

    @Column(name = "fat_g_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatGPer100g;

    @Column(name = "carb_g_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbGPer100g;

    @Column(name = "default_unit_g", precision = 8, scale = 2)
    private BigDecimal defaultUnitG;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private FoodSource source;

    /** 中文别名（PG JSONB），用于 GIN 索引模糊搜索。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aliases", columnDefinition = "jsonb")
    private List<String> aliases;

    protected Food() {
        // JPA
    }

    private Food(Long userId, String name, BigDecimal kcalPer100g,
                 BigDecimal proteinGPer100g, BigDecimal fatGPer100g,
                 BigDecimal carbGPer100g, BigDecimal defaultUnitG,
                 FoodSource source, List<String> aliases) {
        this.userId = userId;
        this.name = name;
        this.kcalPer100g = kcalPer100g;
        this.proteinGPer100g = proteinGPer100g;
        this.fatGPer100g = fatGPer100g;
        this.carbGPer100g = carbGPer100g;
        this.defaultUnitG = defaultUnitG;
        this.source = source;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    /** 工厂：系统默认食物（userId=null，不可改/删）。 */
    public static Food system(String name, String category,
                              double kcal, double protein, double carb, double fat) {
        return new Food(null, name, bd(kcal), bd(protein), bd(fat), bd(carb), null,
                FoodSource.SYSTEM, List.of());
    }

    /** 工厂：用户自定义食物。 */
    public static Food user(Long userId, String name, String category,
                            double kcal, double protein, double carb, double fat) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null for user food");
        }
        return new Food(userId, name, bd(kcal), bd(protein), bd(fat), bd(carb), null,
                FoodSource.USER, List.of());
    }

    /** 工厂：通用（带 aliases），仅用于服务层从 FoodCreateRequest 构造。 */
    public static Food create(Long userId, String name, List<String> aliases,
                              String category, BigDecimal kcalPer100g,
                              BigDecimal proteinGPer100g, BigDecimal fatGPer100g,
                              BigDecimal carbGPer100g) {
        return new Food(userId, name, kcalPer100g, proteinGPer100g, fatGPer100g,
                carbGPer100g, null, userId == null ? FoodSource.SYSTEM : FoodSource.USER,
                aliases);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    public boolean isSystem() {
        return userId == null;
    }

    public void applyUpdate(String name, List<String> aliases, String category,
                            BigDecimal kcalPer100g, BigDecimal proteinGPer100g,
                            BigDecimal fatGPer100g, BigDecimal carbGPer100g) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (aliases != null) {
            this.aliases = List.copyOf(aliases);
        }
        if (kcalPer100g != null) {
            this.kcalPer100g = kcalPer100g;
        }
        if (proteinGPer100g != null) {
            this.proteinGPer100g = proteinGPer100g;
        }
        if (fatGPer100g != null) {
            this.fatGPer100g = fatGPer100g;
        }
        if (carbGPer100g != null) {
            this.carbGPer100g = carbGPer100g;
        }
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public BigDecimal getKcalPer100g() { return kcalPer100g; }
    public BigDecimal getProteinGPer100g() { return proteinGPer100g; }
    public BigDecimal getFatGPer100g() { return fatGPer100g; }
    public BigDecimal getCarbGPer100g() { return carbGPer100g; }
    public BigDecimal getDefaultUnitG() { return defaultUnitG; }
    public FoodSource getSource() { return source; }
    public List<String> getAliases() { return aliases; }

    /** 仅用于反序列化的别名 setter（Hibernate JSON 映射需要）；不开放给 service。 */
    @SuppressWarnings("unused")
    void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}