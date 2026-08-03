package com.lifewise.diet.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealType;
import com.lifewise.diet.repository.MealRepository;
import com.lifewise.diet.repository.StatsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MealReadPortAdapter cross-module read-only contract.
 */
@ExtendWith(MockitoExtension.class)
class MealReadPortAdapterTest {

    @Mock MealRepository mealRepository;
    @Mock StatsRepository statsRepository;

    MealReadPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MealReadPortAdapter(mealRepository, statsRepository);
    }

    @Test
    @DisplayName("findById returns snapshot when user owns meal")
    void find_by_id_returns_snapshot() {
        Meal meal = Meal.create(1L, LocalDate.of(2026, 8, 3), "UTC", MealType.LUNCH, null);
        meal.setIdInternal(99L);
        meal.setTotalKcalCents(13000L);
        when(mealRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(meal));

        var snapshot = adapter.findById(1L, 99L);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().calories()).isEqualByComparingTo(new java.math.BigDecimal("130.00"));
        assertThat(snapshot.get().mealType()).isEqualTo("LUNCH");
    }

    @Test
    @DisplayName("findById returns empty when meal owned by another user")
    void find_by_id_rejects_cross_user() {
        Meal meal = Meal.create(2L, LocalDate.of(2026, 8, 3), "UTC", MealType.LUNCH, null);
        meal.setIdInternal(99L);
        when(mealRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(meal));

        var snapshot = adapter.findById(1L, 99L);

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("sumKcalCentsByDayInRange returns Map<LocalDate,Long cents>")
    void sum_kcal_cents_by_day() {
        when(statsRepository.sumKcalCentsByDayInRange(1L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)))
                .thenReturn(Map.of(
                        LocalDate.of(2026, 8, 1), 50000L,
                        LocalDate.of(2026, 8, 2), 60000L));

        Map<LocalDate, Long> result = adapter.sumKcalCentsByDayInRange(1L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        assertThat(result).hasSize(2);
        assertThat(result.get(LocalDate.of(2026, 8, 2))).isEqualTo(60000L);
    }
}