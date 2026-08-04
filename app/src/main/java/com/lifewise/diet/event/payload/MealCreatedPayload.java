package com.lifewise.diet.event.payload;

import java.time.LocalDate;
import java.util.Map;

/**
 * Outbox 事件 payload：{@code meal.created}（plan-04-diet §4 + §5.6）。
 *
 * <p>消费方：AI 模块月度营养聚合增量。
 */
public record MealCreatedPayload(
        Long mealId,
        Long userId,
        String mealType,
        LocalDate localDate,
        Long totalKcalCents,
        String timezone) {

    public Map<String, Object> toMap() {
        return Map.of(
                "mealId", mealId,
                "userId", userId,
                "mealType", mealType,
                "localDate", localDate.toString(),
                "totalKcalCents", totalKcalCents,
                "timezone", timezone);
    }

    @SuppressWarnings("unchecked")
    public static MealCreatedPayload fromMap(Map<String, Object> map) {
        return new MealCreatedPayload(
                ((Number) map.get("mealId")).longValue(),
                ((Number) map.get("userId")).longValue(),
                (String) map.get("mealType"),
                LocalDate.parse((String) map.get("localDate")),
                ((Number) map.get("totalKcalCents")).longValue(),
                (String) map.get("timezone"));
    }
}