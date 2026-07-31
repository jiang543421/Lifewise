package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.MealSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Meal 模块对外只读端口（plan-shared-integration §2.2）。
 *
 * <p>实现由 meal 模块在 {@code com.lifewise.meal.port.out.MealReadPortAdapter} 提供。
 */
public interface MealReadPort {

    Optional<MealSnapshot> findById(Long userId, Long mealId);

    List<MealSnapshot> findInRange(Long userId, LocalDate from, LocalDate to);
}
