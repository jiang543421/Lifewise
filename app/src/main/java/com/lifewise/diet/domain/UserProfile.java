package com.lifewise.diet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 用户身体参数 + 营养目标（plan-04-diet §5.5 + user_profiles V40 扩展）。
 *
 * <p>主键为 {@code user_id}（与 {@code users.id} 1:1），不复用 {@code BaseEntity} 的 IDENTITY
 * 主键 —— 否则 Hibernate 报"两个 @Id"。审计字段直接持有（V40 扩展列）。
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "age")
    private Integer age;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 8)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 16)
    private ActivityLevel activityLevel;

    @Column(name = "daily_kcal_target")
    private Integer dailyKcalTarget;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserProfile() {
        // JPA
    }

    private UserProfile(Long userId, BigDecimal heightCm, BigDecimal weightKg,
                        Integer age, Gender gender, ActivityLevel activityLevel,
                        Integer dailyKcalTarget) {
        this.userId = userId;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.age = age;
        this.gender = gender;
        this.activityLevel = activityLevel;
        this.dailyKcalTarget = dailyKcalTarget;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 工厂：service 层从 ProfileRequest 构造。 */
    public static UserProfile create(Long userId, BigDecimal heightCm, BigDecimal weightKg,
                                     Integer age, Gender gender, ActivityLevel activityLevel,
                                     Integer dailyKcalTarget) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return new UserProfile(userId, heightCm, weightKg, age, gender,
                activityLevel, dailyKcalTarget);
    }

    public void applyUpdate(BigDecimal heightCm, BigDecimal weightKg, Integer age,
                            Gender gender, ActivityLevel activityLevel) {
        if (heightCm != null) {
            this.heightCm = heightCm;
        }
        if (weightKg != null) {
            this.weightKg = weightKg;
        }
        if (age != null) {
            this.age = age;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (activityLevel != null) {
            this.activityLevel = activityLevel;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public void setDailyKcalTarget(Integer target) {
        this.dailyKcalTarget = target;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getUserId() { return userId; }
    public Long getId() { return userId; }
    public BigDecimal getHeightCm() { return heightCm; }
    public BigDecimal getWeightKg() { return weightKg; }
    public Integer getAge() { return age; }
    public Gender getGender() { return gender; }
    public ActivityLevel getActivityLevel() { return activityLevel; }
    public Integer getDailyKcalTarget() { return dailyKcalTarget; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** 测试用：注入 userId。 */
    public void setIdInternal(Long id) {
        this.userId = id;
    }
}