package com.lifewise.diet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealItem;
import com.lifewise.diet.domain.MealType;
import com.lifewise.diet.dto.MealCreateRequest;
import com.lifewise.diet.dto.MealItemRequest;
import com.lifewise.diet.dto.MealView;
import com.lifewise.diet.event.payload.MealCreatedPayload;
import com.lifewise.diet.repository.FoodRepository;
import com.lifewise.diet.repository.MealRepository;
import com.lifewise.diet.service.exception.FoodNotFoundException;
import com.lifewise.diet.service.exception.InvalidMealException;
import com.lifewise.diet.service.exception.MealNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MealService CRUD + outbox (plan-04-diet section 5.1 + 5.6).
 */
@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock MealRepository mealRepository;
    @Mock FoodRepository foodRepository;
    @Mock OutboxWriter outboxWriter;
    @Mock NutritionCalculator nutritionCalculator;

    MealService service;
    Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new MealService(mealRepository, foodRepository,
                outboxWriter, nutritionCalculator, clock);
    }

    private Food riceFixture() {
        Food rice = Food.system("White rice", "STAPLE", 130d, 2.7d, 28.0d, 0.3d);
        rice.setIdInternal(11L);
        return rice;
    }

    private Food eggFixture() {
        Food egg = Food.system("Egg", "PROTEIN", 155d, 13.0d, 1.1d, 11.0d);
        egg.setIdInternal(12L);
        return egg;
    }

    @Test
    @DisplayName("create aggregates kcal per item, meal total = sum")
    void create_aggregates_kcal_per_item() {
        when(foodRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(riceFixture()));
        when(foodRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(eggFixture()));
        when(nutritionCalculator.computeKcalSnapshot(any(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            // 100g rice = 130 kcal; 50g egg = 77.5 kcal
            return amount.multiply(new BigDecimal("1.30"));
        });
        when(nutritionCalculator.computeProteinSnapshot(any(), any())).thenReturn(BigDecimal.ONE);
        when(nutritionCalculator.computeCarbSnapshot(any(), any())).thenReturn(BigDecimal.ONE);
        when(nutritionCalculator.computeFatSnapshot(any(), any())).thenReturn(BigDecimal.ONE);
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> {
            Meal m = inv.getArgument(0);
            m.setIdInternal(101L);
            return m;
        });

        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "Asia/Shanghai", "noon",
                List.of(
                        new MealItemRequest(11L, new BigDecimal("100.00"), null),
                        new MealItemRequest(12L, new BigDecimal("50.00"), null)));
        MealView view = service.create(1L, req);

        assertThat(view.id()).isEqualTo(101L);
        assertThat(view.items()).hasSize(2);
        assertThat(view.totalKcal()).isEqualByComparingTo(new BigDecimal("195.00"));
    }

    @Test
    @DisplayName("create with empty items throws InvalidMealException")
    void create_rejects_empty_items() {
        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null, List.of());

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(InvalidMealException.class);
        verify(mealRepository, never()).save(any());
    }

    @Test
    @DisplayName("create with null type throws InvalidMealException")
    void create_rejects_invalid_type() {
        MealCreateRequest req = new MealCreateRequest(
                null, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(11L, new BigDecimal("100.00"), null)));

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(InvalidMealException.class);
    }

    @Test
    @DisplayName("create with missing foodId throws FoodNotFoundException")
    void create_rejects_missing_food() {
        when(foodRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());
        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(999L, new BigDecimal("100.00"), null)));

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(FoodNotFoundException.class);
        verify(mealRepository, never()).save(any());
    }

    @Test
    @DisplayName("create with amount_g=0 throws InvalidAmountException")
    void create_rejects_non_positive_amount_g() {
        when(foodRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(riceFixture()));
        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(11L, BigDecimal.ZERO, null)));

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("create emits outbox meal.created event")
    void create_emits_meal_created_event() {
        when(foodRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(riceFixture()));
        when(nutritionCalculator.computeKcalSnapshot(any(), any()))
                .thenReturn(new BigDecimal("130.00"));
        when(nutritionCalculator.computeProteinSnapshot(any(), any())).thenReturn(BigDecimal.ONE);
        when(nutritionCalculator.computeCarbSnapshot(any(), any())).thenReturn(BigDecimal.ONE);
        when(nutritionCalculator.computeFatSnapshot(any(), any())).thenReturn(BigDecimal.ONE);
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> {
            Meal m = inv.getArgument(0);
            m.setIdInternal(101L);
            return m;
        });

        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(11L, new BigDecimal("100.00"), null)));
        service.create(1L, req);

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("meal.created");
        assertThat(env.getValue().userId()).isEqualTo(1L);
        assertThat(env.getValue().aggregateId()).isEqualTo(101L);
        MealCreatedPayload payload = MealCreatedPayload.fromMap(env.getValue().payload());
        assertThat(payload.mealId()).isEqualTo(101L);
        assertThat(payload.userId()).isEqualTo(1L);
        assertThat(payload.mealType()).isEqualTo("LUNCH");
    }

    @Test
    @DisplayName("getOwned: cross-user access throws MealNotFoundException (no info leak)")
    void get_owned_rejects_cross_user() {
        Meal existing = Meal.create(99L, LocalDate.of(2026, 8, 3), "UTC", MealType.LUNCH, null);
        existing.setIdInternal(50L);
        when(mealRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getOwned(1L, 50L))
                .isInstanceOf(MealNotFoundException.class);
    }

    @Test
    @DisplayName("softDelete: 仅置 meal.deletedAt，items 行不动（restore 可完整还原）")
    void soft_delete_only_marks_meal_deleted_at() {
        Meal existing = Meal.create(1L, LocalDate.of(2026, 8, 3), "UTC", MealType.LUNCH, null);
        existing.setIdInternal(50L);
        when(mealRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(existing));
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(1L, 50L);

        assertThat(existing.getDeletedAt()).isNotNull();
    }

    /**
     * Bug A lock：33.33g × 99.99kcal 触发非整数 cents
     * （33.329667 kcal → 3332.9667 cents）。
     * 在 longValueExact() 之前必须 setScale(0, HALF_UP) 抹零，否则 400 抛错。
     * 这里用真实 NutritionCalculator（不 mock）走完整链路。
     */
    @Test
    @DisplayName("create with realistic decimals computes totalKcalCents via HALF_UP rounding (Bug A lock)")
    void create_realistic_decimals_rounds_cents_via_half_up() {
        NutritionCalculator realCalc = new NutritionCalculator();
        MealService realService = new MealService(mealRepository, foodRepository,
                outboxWriter, realCalc, clock);
        Food decimalFood = Food.system("Decimal food", "STAPLE", 99.99d, 0d, 0d, 0d);
        decimalFood.setIdInternal(11L);
        when(foodRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(decimalFood));
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> {
            Meal m = inv.getArgument(0);
            m.setIdInternal(101L);
            return m;
        });

        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(11L, new BigDecimal("33.33"), null)));
        MealView view = realService.create(1L, req);

        // 33.33 * 99.99 / 100 = 33.329667 → scale=2 HALF_UP = 33.33
        // toCents(33.33) = 3333
        assertThat(view.totalKcal()).isEqualByComparingTo(new BigDecimal("33.33"));
    }
}