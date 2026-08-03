package com.lifewise.diet.service;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealItem;
import com.lifewise.diet.domain.MealType;
import com.lifewise.diet.dto.MealCreateRequest;
import com.lifewise.diet.dto.MealItemRequest;
import com.lifewise.diet.dto.MealListItem;
import com.lifewise.diet.dto.MealView;
import com.lifewise.diet.event.payload.MealCreatedPayload;
import com.lifewise.diet.repository.FoodRepository;
import com.lifewise.diet.repository.MealRepository;
import com.lifewise.diet.service.exception.FoodNotFoundException;
import com.lifewise.diet.service.exception.InvalidMealException;
import com.lifewise.diet.service.exception.MealNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 餐次 CRUD + 营养聚合 + outbox（plan-04-diet §5.1 + §5.6）。 */
@Service
public class MealService {

    private final MealRepository mealRepository;
    private final FoodRepository foodRepository;
    private final OutboxWriter outboxWriter;
    private final NutritionCalculator calculator;
    private final Clock clock;

    public MealService(MealRepository mealRepository,
                       FoodRepository foodRepository,
                       OutboxWriter outboxWriter,
                       NutritionCalculator calculator,
                       Clock clock) {
        this.mealRepository = mealRepository;
        this.foodRepository = foodRepository;
        this.outboxWriter = outboxWriter;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional
    public MealView create(Long userId, MealCreateRequest req) {
        if (req.type() == null) {
            throw new InvalidMealException("type must not be null");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new InvalidMealException("items must not be empty");
        }
        if (req.localDate() == null) {
            throw new InvalidMealException("localDate must not be null");
        }

        Meal meal = Meal.create(userId, req.localDate(), req.timezone(), req.type(), req.note());
        BigDecimal totalKcal = BigDecimal.ZERO;
        for (MealItemRequest itemReq : req.items()) {
            Food food = foodRepository.findByIdAndDeletedAtIsNull(itemReq.foodId())
                    .orElseThrow(() -> new FoodNotFoundException(itemReq.foodId()));
            BigDecimal kcal = calculator.computeKcalSnapshot(itemReq.amountG(), food);
            BigDecimal protein = calculator.computeProteinSnapshot(itemReq.amountG(), food);
            BigDecimal fat = calculator.computeFatSnapshot(itemReq.amountG(), food);
            BigDecimal carb = calculator.computeCarbSnapshot(itemReq.amountG(), food);
            MealItem item = MealItem.of(meal, food.getId(), itemReq.amountG(),
                    kcal, protein, fat, carb);
            meal.addItem(item);
            totalKcal = totalKcal.add(kcal);
        }
        // cents 截断 + 溢出双重防护（review §M_new）。
        meal.setTotalKcalCents(toCents(totalKcal));
        meal = mealRepository.save(meal);
        // 持久化 items（Meal 是聚合根，但 MealItem 与 meal 关系通过 JoinColumn 触发 Hibernate 自动级联
        // 写入的场景需显式 save；这里用 meal.items 由 Hibernate dirty-checking 写入）
        appendCreatedEvent(userId, meal);
        return buildView(meal);
    }

    @Transactional(readOnly = true)
    public MealView getOwned(Long userId, Long mealId) {
        Meal meal = loadOwned(userId, mealId);
        return buildView(meal);
    }

    @Transactional(readOnly = true)
    public List<MealListItem> list(Long userId, LocalDate from, LocalDate to,
                                   MealType type, Pageable pageable) {
        return mealRepository.search(userId, from, to, type, pageable)
                .map(MealListItem::from)
                .toList();
    }

    @Transactional
    public MealView update(Long userId, Long mealId, MealCreateRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new InvalidMealException("items must not be empty");
        }
        Meal meal = loadOwned(userId, mealId);
        // 不可变字段：type/localDate/timezone 变更 = 改分区 + 复合 FK 迁移，v1.0 拒绝。
        // 客户端改日期应"删了重建"。详见 review §H1+M2。
        if (req.type() != null && req.type() != meal.getMealType()) {
            throw new InvalidMealException("type change not allowed; delete and recreate");
        }
        if (req.localDate() != null && !req.localDate().equals(meal.getLocalDate())) {
            throw new InvalidMealException("localDate change not allowed; delete and recreate");
        }
        if (req.timezone() != null && !req.timezone().equals(meal.getTimezone())) {
            throw new InvalidMealException("timezone change not allowed; delete and recreate");
        }
        // 替换 items：靠 orphanRemoval 清旧、cascade 写入新。不显式 delete，
        // 避免与 cascade 双删产生不可逆数据丢失（review §H2）。
        meal.clearItems();

        BigDecimal totalKcal = BigDecimal.ZERO;
        for (MealItemRequest itemReq : req.items()) {
            Food food = foodRepository.findByIdAndDeletedAtIsNull(itemReq.foodId())
                    .orElseThrow(() -> new FoodNotFoundException(itemReq.foodId()));
            BigDecimal kcal = calculator.computeKcalSnapshot(itemReq.amountG(), food);
            BigDecimal protein = calculator.computeProteinSnapshot(itemReq.amountG(), food);
            BigDecimal fat = calculator.computeFatSnapshot(itemReq.amountG(), food);
            BigDecimal carb = calculator.computeCarbSnapshot(itemReq.amountG(), food);
            MealItem item = MealItem.of(meal, food.getId(), itemReq.amountG(),
                    kcal, protein, fat, carb);
            meal.addItem(item);
            totalKcal = totalKcal.add(kcal);
        }
        // cents 截断 + 溢出双重防护（review §M_new）。
        meal.setTotalKcalCents(toCents(totalKcal));
        if (req.note() != null) {
            meal.setNote(req.note());
        }
        meal = mealRepository.save(meal);
        return buildView(meal);
    }

    /** kcal → cents 严格转换。抛出 ArithmeticException 表示溢出 / 异常小数。 */
    private static long toCents(BigDecimal totalKcal) {
        try {
            return totalKcal.multiply(new BigDecimal("100")).longValueExact();
        } catch (ArithmeticException ex) {
            throw new InvalidMealException("totalKcal overflow or non-integer cents: " + totalKcal);
        }
    }

    @Transactional
    public void softDelete(Long userId, Long mealId) {
        Meal meal = loadOwned(userId, mealId);
        // 软删 meal 后，meal_items 由 meals.deleted_at IS NULL 过滤统一屏蔽
        // （V13 / StatsRepository / MealRepository 全部已带此条件）。
        // items 行不动，restore() 自动完整还原 —— 零数据丢失。
        meal.softDelete();
        mealRepository.save(meal);
    }

    @Transactional
    public MealView restore(Long userId, Long mealId) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new MealNotFoundException(mealId));
        if (!meal.getUserId().equals(userId)) {
            throw new MealNotFoundException(mealId);
        }
        meal.restore();
        meal = mealRepository.save(meal);
        return buildView(meal);
    }

    private Meal loadOwned(Long userId, Long mealId) {
        Meal meal = mealRepository.findByIdAndDeletedAtIsNull(mealId)
                .orElseThrow(() -> new MealNotFoundException(mealId));
        if (!meal.getUserId().equals(userId)) {
            throw new MealNotFoundException(mealId);
        }
        return meal;
    }

    private MealView buildView(Meal meal) {
        // 食物索引：按 meal.items 的 foodId 一次性查
        Map<Long, Food> foodIndex = new HashMap<>();
        List<Long> foodIds = meal.getItems().stream()
                .map(MealItem::getFoodId).distinct().toList();
        for (Long foodId : foodIds) {
            foodRepository.findByIdAndDeletedAtIsNull(foodId).ifPresent(f -> foodIndex.put(foodId, f));
        }
        return MealView.from(meal, foodIndex);
    }

    private void appendCreatedEvent(Long userId, Meal meal) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.MEAL_CREATED.eventType(),
                1, now, userId, "meal", meal.getId(),
                null, null, null,
                new MealCreatedPayload(meal.getId(), userId,
                        meal.getMealType().name(), meal.getLocalDate(),
                        meal.getTotalKcalCents(), meal.getTimezone()).toMap()));
    }
}