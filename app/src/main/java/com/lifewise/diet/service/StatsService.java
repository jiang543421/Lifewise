package com.lifewise.diet.service;

import com.lifewise.diet.dto.StatsView;
import com.lifewise.diet.repository.ProfileRepository;
import com.lifewise.diet.repository.StatsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 营养统计（plan-04-diet §2.3 + §5.4）。
 *
 * <p>按日聚合走 meal_items 实时计算；按周聚合走物化视图 mv_meal_nutrition_weekly。
 */
@Service
public class StatsService {

    private final StatsRepository statsRepository;
    private final ProfileRepository profileRepository;

    public StatsService(StatsRepository statsRepository, ProfileRepository profileRepository) {
        this.statsRepository = statsRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public StatsView stats(Long userId, LocalDate from, LocalDate to, String granularity) {
        Map<LocalDate, Long> byDayCents = statsRepository.sumKcalByDayInRange(userId, from, to);
        Map<LocalDate, BigDecimal> byDay = byDayCents.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue()).divide(new BigDecimal(100))));
        Integer target = profileRepository.findByUserId(userId)
                .map(p -> p.getDailyKcalTarget())
                .orElse(null);
        return new StatsView(byDay, null, target);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> sumKcalByDayInRange(Long userId, LocalDate from, LocalDate to) {
        return statsRepository.sumKcalByDayInRange(userId, from, to);
    }

    @Transactional(readOnly = true)
    public List<StatsView.WeeklyBucket> weekly(Long userId, LocalDate weekStart) {
        return statsRepository.weeklyBuckets(userId, weekStart).stream()
                .map(b -> new StatsView.WeeklyBucket(
                        b.weekStart(), b.mealType(), b.mealCount(),
                        b.totalKcal(), b.totalProteinG(), b.totalCarbG(), b.totalFatG()))
                .toList();
    }
}