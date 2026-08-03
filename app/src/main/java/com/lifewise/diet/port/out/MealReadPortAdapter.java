package com.lifewise.diet.port.out;

import com.lifewise.diet.repository.MealRepository;
import com.lifewise.diet.repository.StatsRepository;
import com.lifewise.shared.integration.port.MealReadPort;
import com.lifewise.shared.integration.port.snapshot.MealSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 适配 {@link MealReadPort}（plan-04-diet §2.2 + §5.6）。
 *
 * <p>对外只暴露只读快照，避免 daily 域直接 import diet 实体。
 */
@Component
public class MealReadPortAdapter implements MealReadPort {

    private final MealRepository mealRepository;
    private final StatsRepository statsRepository;

    public MealReadPortAdapter(MealRepository mealRepository,
                               StatsRepository statsRepository) {
        this.mealRepository = mealRepository;
        this.statsRepository = statsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MealSnapshot> findById(Long userId, Long mealId) {
        return mealRepository.findByIdAndDeletedAtIsNull(mealId)
                .filter(meal -> meal.getUserId().equals(userId))
                .map(meal -> toSnapshot(meal));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MealSnapshot> findInRange(Long userId, LocalDate from, LocalDate to) {
        return mealRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByLocalDateAsc(
                        userId, from, to)
                .stream()
                .map(MealReadPortAdapter::toSnapshot)
                .toList();
    }

    /** diet 模块额外暴露：按日 cents Map（不在 port 接口中，stat 模块专用）。 */
    @Transactional(readOnly = true)
    public Map<LocalDate, Long> sumKcalCentsByDayInRange(Long userId, LocalDate from, LocalDate to) {
        return statsRepository.sumKcalCentsByDayInRange(userId, from, to);
    }

    private static MealSnapshot toSnapshot(com.lifewise.diet.domain.Meal meal) {
        long cents = meal.getTotalKcalCents() == null ? 0L : meal.getTotalKcalCents();
        return new MealSnapshot(
                meal.getId(),
                meal.getUserId(),
                meal.getMealType().name(),
                meal.getLocalDate(),
                BigDecimal.valueOf(cents).divide(new BigDecimal(100)));
    }
}