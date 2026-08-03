package com.lifewise.diet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.diet.domain.ActivityLevel;
import com.lifewise.diet.domain.Gender;
import com.lifewise.diet.domain.UserProfile;
import com.lifewise.diet.dto.ProfileRequest;
import com.lifewise.diet.dto.ProfileView;
import com.lifewise.diet.repository.ProfileRepository;
import com.lifewise.diet.service.exception.InvalidProfileInputException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ProfileService BMR/TDEE + 手动覆盖 (plan-04-diet §5.5).
 *
 * <p>Mifflin-St Jeor:
 * <ul>
 *   <li>male:   BMR = 10*kg + 6.25*cm - 5*age + 5</li>
 *   <li>female: BMR = 10*kg + 6.25*cm - 5*age - 161</li>
 * </ul>
 * TDEE = BMR * activity coefficient.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock ProfileRepository repository;

    ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(repository);
    }

    @Test
    @DisplayName("get when profile missing returns empty ProfileView (timezone=UTC)")
    void get_returns_empty_when_profile_missing() {
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());

        ProfileView view = service.get(1L);

        assertThat(view.userId()).isEqualTo(1L);
        assertThat(view.heightCm()).isNull();
        assertThat(view.dailyKcalTarget()).isNull();
        assertThat(view.activityLevel()).isNull();
    }

    @Test
    @DisplayName("upsert with no dailyKcalTarget auto-computes TDEE = BMR * 1.2")
    void put_computes_target_when_not_specified() {
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        ProfileRequest req = new ProfileRequest(
                new BigDecimal("175.0"), new BigDecimal("70.0"),
                30, Gender.MALE, ActivityLevel.SEDENTARY, null);

        ProfileView view = service.upsert(1L, req);

        // BMR(male 30y 175cm 70kg) = 10*70 + 6.25*175 - 5*30 + 5 = 1648.75
        // TDEE = 1648.75 * 1.2 = 1978.5 -> 1979
        assertThat(view.dailyKcalTarget()).isEqualTo(1979);
        verify(repository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("upsert with explicit dailyKcalTarget=2000 preserves user value")
    void put_preserves_manual_target() {
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        ProfileRequest req = new ProfileRequest(
                new BigDecimal("175.0"), new BigDecimal("70.0"),
                30, Gender.MALE, ActivityLevel.MODERATE, 2000);

        ProfileView view = service.upsert(1L, req);

        assertThat(view.dailyKcalTarget()).isEqualTo(2000);
    }

    @Test
    @DisplayName("recompute overwrites manual target with BMR * coefficient")
    void recompute_overrides_manual_target() {
        UserProfile existing = UserProfile.create(1L, new BigDecimal("175.0"),
                new BigDecimal("70.0"), 30, Gender.MALE, ActivityLevel.ACTIVE, 2200);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileView view = service.recomputeTarget(1L);

        // TDEE ACTIVE = 1.725 * BMR = 1.725 * 1648.75 = 2844.09 -> 2844
        // wait: ACTIVE coefficient is 1.725, not 1.55
        // recalc: 1648.75 * 1.725 = 2844.09375 -> 2844
        assertThat(view.dailyKcalTarget()).isEqualTo(2844);
        verify(repository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("invalid heightCm=0 throws InvalidProfileInputException")
    void put_rejects_invalid_dimensions() {
        ProfileRequest bad = new ProfileRequest(
                new BigDecimal("0"), new BigDecimal("70.0"),
                30, Gender.MALE, ActivityLevel.SEDENTARY, null);

        assertThatThrownBy(() -> service.upsert(1L, bad))
                .isInstanceOf(InvalidProfileInputException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("female 25y 165cm 55kg MODERATE TDEE ≈ 2008")
    void female_bmr_tdee() {
        when(repository.findByUserId(2L)).thenReturn(Optional.empty());
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        ProfileRequest req = new ProfileRequest(
                new BigDecimal("165.0"), new BigDecimal("55.0"),
                25, Gender.FEMALE, ActivityLevel.MODERATE, null);

        ProfileView view = service.upsert(2L, req);

        // BMR(female 25y 165cm 55kg) = 10*55 + 6.25*165 - 5*25 - 161 = 1295.25
        // TDEE MODERATE = 1.55 * 1295.25 = 2007.6375 -> 2008
        assertThat(view.dailyKcalTarget()).isEqualTo(2008);
    }
}