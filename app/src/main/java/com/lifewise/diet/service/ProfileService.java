package com.lifewise.diet.service;

import com.lifewise.diet.domain.ActivityLevel;
import com.lifewise.diet.domain.Gender;
import com.lifewise.diet.domain.UserProfile;
import com.lifewise.diet.dto.ProfileRequest;
import com.lifewise.diet.dto.ProfileView;
import com.lifewise.diet.repository.ProfileRepository;
import com.lifewise.diet.service.exception.InvalidProfileInputException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户身体参数 + 营养目标（plan-04-diet §5.5）。
 *
 * <p>Mifflin-St Jeor BMR 公式：
 * <ul>
 *   <li>男：BMR = 10*kg + 6.25*cm - 5*age + 5</li>
 *   <li>女：BMR = 10*kg + 6.25*cm - 5*age - 161</li>
 * </ul>
 * TDEE = BMR * activity coefficient。
 */
@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProfileView get(Long userId) {
        return repository.findByUserId(userId)
                .map(ProfileView::from)
                .orElseGet(() -> ProfileView.empty(userId));
    }

    @Transactional
    public ProfileView upsert(Long userId, ProfileRequest req) {
        validate(req);
        UserProfile profile = repository.findByUserId(userId)
                .orElseGet(() -> UserProfile.create(userId, req.heightCm(), req.weightKg(),
                        req.age(), req.gender(), req.activityLevel(), null));
        profile.applyUpdate(req.heightCm(), req.weightKg(), req.age(),
                req.gender(), req.activityLevel());
        // 用户显式指定 dailyKcalTarget 时保留；否则按 BMR 重算
        if (req.dailyKcalTarget() != null) {
            profile.setDailyKcalTarget(req.dailyKcalTarget());
        } else {
            profile.setDailyKcalTarget(computeTarget(req.weightKg(), req.heightCm(),
                    req.age(), req.gender(), req.activityLevel()));
        }
        profile = repository.save(profile);
        return ProfileView.from(profile);
    }

    @Transactional
    public ProfileView recomputeTarget(Long userId) {
        UserProfile profile = repository.findByUserId(userId)
                .orElseThrow(() -> new InvalidProfileInputException(
                        "profile not set: userId=" + userId));
        int target = computeTarget(profile.getWeightKg(), profile.getHeightCm(),
                profile.getAge(), profile.getGender(), profile.getActivityLevel());
        profile.setDailyKcalTarget(target);
        profile = repository.save(profile);
        return ProfileView.from(profile);
    }

    private void validate(ProfileRequest req) {
        if (req.heightCm() == null || req.heightCm().signum() <= 0) {
            throw new InvalidProfileInputException("heightCm must be > 0");
        }
        if (req.weightKg() == null || req.weightKg().signum() <= 0) {
            throw new InvalidProfileInputException("weightKg must be > 0");
        }
        if (req.age() == null || req.age() < 10 || req.age() > 120) {
            throw new InvalidProfileInputException("age must be in [10, 120]");
        }
        if (req.gender() == null) {
            throw new InvalidProfileInputException("gender must not be null");
        }
        if (req.activityLevel() == null) {
            throw new InvalidProfileInputException("activityLevel must not be null");
        }
        if (req.dailyKcalTarget() != null
                && (req.dailyKcalTarget() < 500 || req.dailyKcalTarget() > 10000)) {
            throw new InvalidProfileInputException(
                    "dailyKcalTarget must be in [500, 10000] when specified");
        }
    }

    /** Mifflin-St Jeor + activity coefficient。 */
    int computeTarget(BigDecimal weightKg, BigDecimal heightCm, Integer age,
                      Gender gender, ActivityLevel level) {
        BigDecimal bmr = computeBmr(weightKg, heightCm, age, gender);
        BigDecimal tdee = bmr.multiply(level.coefficient());
        return tdee.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    BigDecimal computeBmr(BigDecimal weightKg, BigDecimal heightCm, Integer age, Gender gender) {
        BigDecimal bmr = weightKg.multiply(BigDecimal.TEN)
                .add(heightCm.multiply(new BigDecimal("6.25")))
                .subtract(BigDecimal.valueOf(age).multiply(BigDecimal.valueOf(5)));
        if (gender == Gender.MALE) {
            bmr = bmr.add(BigDecimal.valueOf(5));
        } else {
            bmr = bmr.subtract(BigDecimal.valueOf(161));
        }
        return bmr;
    }
}