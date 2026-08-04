package com.lifewise.diet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifewise.diet.domain.ActivityLevel;
import com.lifewise.diet.domain.Gender;
import com.lifewise.diet.domain.UserProfile;
import java.math.BigDecimal;

/** GET /api/meals/profile 视图。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileView(
        Long userId,
        BigDecimal heightCm,
        BigDecimal weightKg,
        Integer age,
        Gender gender,
        ActivityLevel activityLevel,
        Integer dailyKcalTarget) {

    public static ProfileView from(UserProfile profile) {
        return new ProfileView(profile.getUserId(), profile.getHeightCm(),
                profile.getWeightKg(), profile.getAge(), profile.getGender(),
                profile.getActivityLevel(), profile.getDailyKcalTarget());
    }

    public static ProfileView empty(Long userId) {
        return new ProfileView(userId, null, null, null, null, null, null);
    }
}