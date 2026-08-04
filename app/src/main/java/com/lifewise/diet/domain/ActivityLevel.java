package com.lifewise.diet.domain;

import java.math.BigDecimal;

/** 活动量分级（plan-04-diet §5.5；系数对齐 Mifflin-St Jeor 标准）。 */
public enum ActivityLevel {
    SEDENTARY(new BigDecimal("1.20")),
    LIGHT(new BigDecimal("1.375")),
    MODERATE(new BigDecimal("1.55")),
    ACTIVE(new BigDecimal("1.725")),
    VERY_ACTIVE(new BigDecimal("1.90"));

    private final BigDecimal coefficient;

    ActivityLevel(BigDecimal coefficient) {
        this.coefficient = coefficient;
    }

    /** TDEE 系数（TDEE = BMR * coefficient）。 */
    public BigDecimal coefficient() {
        return coefficient;
    }
}