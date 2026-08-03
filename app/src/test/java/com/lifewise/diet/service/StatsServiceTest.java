package com.lifewise.diet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lifewise.diet.dto.StatsView;
import com.lifewise.diet.repository.ProfileRepository;
import com.lifewise.diet.repository.StatsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * StatsService 周聚合走物化视图 mv_meal_nutrition_weekly（plan-04-diet §5.4）。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock StatsRepository statsRepository;
    @Mock ProfileRepository profileRepository;

    StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(statsRepository, profileRepository);
    }

    @Test
    @DisplayName("按日聚合：返回 Map<LocalDate, Long cents>")
    void sum_kcal_by_day_in_range() {
        when(statsRepository.sumKcalByDayInRange(1L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7)))
                .thenReturn(Map.of(
                        LocalDate.of(2026, 8, 1), 180000L,
                        LocalDate.of(2026, 8, 2), 195000L,
                        LocalDate.of(2026, 8, 3), 210000L));

        Map<LocalDate, Long> result = service.sumKcalByDayInRange(1L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        assertThat(result).containsEntry(LocalDate.of(2026, 8, 2), 195000L);
    }

    @Test
    @DisplayName("按周聚合：返回 List<WeeklyBucket>（从 mv_meal_nutrition_weekly）")
    void weekly_view_uses_materialized_view() {
        when(statsRepository.weeklyBuckets(1L, LocalDate.of(2026, 8, 1)))
                .thenReturn(List.of(
                        new StatsRepository.WeeklyBucket(
                                LocalDate.of(2026, 8, 3), "LUNCH",
                                5L, new BigDecimal("980.00"),
                                new BigDecimal("42.50"), new BigDecimal("210.00"),
                                new BigDecimal("6.50"))));

        List<StatsView.WeeklyBucket> buckets = service.weekly(1L, LocalDate.of(2026, 8, 1));

        assertThat(buckets).hasSize(1);
        StatsView.WeeklyBucket b = buckets.get(0);
        assertThat(b.weekStart()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(b.mealType()).isEqualTo("LUNCH");
        assertThat(b.totalKcal()).isEqualByComparingTo(new BigDecimal("980.00"));
    }
}