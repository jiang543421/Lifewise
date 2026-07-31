# plan-04-diet 实施方案

## 参考资料

- [`docs/lifewise/specs/PRD/04-diet-tracking.md`](../specs/PRD/04-diet-tracking.md) — 产品 PRD（MEAL-024 身体参数）
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.6 meal 模块边界
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V7（foods / meals / meal_items）+ V11（meals 按月分区）+ V13（物化视图）
- [`docs/lifewise/designs/04-diet-ui/2026-07-26-diet-ui-design.md`](../designs/04-diet-ui/2026-07-26-diet-ui-design.md) — UI 设计契约
- [`docs/lifewise/architecture/versions/data-model-design-v1.1.1.md`](../architecture/versions/data-model-design-v1.1.1.md) §1.1.6 饮食模块字段

## 参考目录

- backend：`app/src/main/java/com/lifewise/diet/`
  - `controller/` — MealController / FoodController / StatsController / ProfileController
  - `service/` — MealService / FoodService / NutritionCalculator / StatsService / ProfileService / FoodSeedService
  - `domain/` — Meal / MealItem / Food / UserProfile
  - `repository/` — MealRepository / MealItemRepository / FoodRepository / ProfileRepository
  - `port/` — DietReadPort（暴露给其他模块）
  - `event/` — MealCreated
  - `dto/` — MealCreateRequest / MealView / FoodCreateRequest / FoodView / NutritionView / StatsView
- frontend：`docs/lifewise/designs/04-diet-ui/`
  - `new-04-diet-ui.html` — 主界面原型（餐次时间轴 + 食物搜索 + 营养条）

## 1. 模块边界 / 包结构

diet 模块是用户**饮食追踪**的入口，独立模块，集成 `user_profiles` 身体参数（PRD MEAL-024）做卡路里目标计算。

```
diet/
├── controller/
│   ├── MealController.java            /api/meals CRUD（按月分区）+ 餐次详情
│   ├── FoodController.java            /api/foods CRUD（系统/自定义）+ 搜索
│   ├── StatsController.java           /api/meals/stats?from=&to=（营养聚合）
│   └── ProfileController.java         /api/meals/profile（身体参数 + 目标）
├── service/
│   ├── MealService.java               创建/更新/软删（写 outbox）
│   ├── FoodService.java               创建/更新/搜索食物（含中文别名 aliases）
│   ├── NutritionCalculator.java       由 meal_items 聚合 total_kcal/macros
│   ├── StatsService.java              按日/周聚合（走物化视图）
│   ├── ProfileService.java            user_profiles 读写（身高/体重/活动量/目标 kcal）
│   └── FoodSeedService.java           系统默认食物库预置
├── domain/
│   ├── Meal.java                      meals 表实体（按月分区）
│   ├── MealItem.java                  meal_items 表实体
│   ├── Food.java                      foods 表实体
│   └── UserProfile.java               user_profiles 实体（H-3）
├── repository/
│   ├── MealRepository.java
│   ├── MealItemRepository.java
│   ├── FoodRepository.java
│   └── ProfileRepository.java
├── port/
│   └── DietReadPort.java              实现 DietReadPortAdapter
├── event/
│   └── MealCreatedEvent.java          payload: {meal_id, user_id, type, occurred_at, total_kcal_cents}
└── dto/
    ├── MealCreateRequest.java         {type, occurred_at, note, items: [{food_id, servings, manual_text?}]}
    ├── MealView.java                  完整视图（含 items + 计算营养）
    ├── FoodCreateRequest.java         {name, aliases?, category, kcal_per_100g, protein_g, carb_g, fat_g}
    ├── FoodView.java
    ├── NutritionView.java             {kcal, protein_g, carb_g, fat_g}
    ├── ProfileRequest.java            {height_cm, weight_kg, gender, activity_level, daily_kcal_target?}
    ├── ProfileView.java               {height_cm, weight_kg, gender, activity_level, daily_kcal_target}
    └── StatsView.java                 {by_day, by_week, target_kcal}
```

## 2. API 契约

### 2.1 Meal CRUD（6 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/meals` | query: `?date=&type=&page=&limit=` | `{data: MealListItem[], meta}` | — |
| GET | `/api/meals/{id}` | — | `{data: MealView}` | `MEAL_NOT_FOUND` |
| POST | `/api/meals` | `MealCreateRequest` | `{data: MealView}` | `VALIDATION_FAILED` / `FOOD_NOT_FOUND` / `INVALID_SERVINGS`（BR-12） |
| PUT | `/api/meals/{id}` | `MealCreateRequest` | `{data: MealView}` | `MEAL_NOT_FOUND` |
| DELETE | `/api/meals/{id}` | — | `{message: "ok"}` | `MEAL_NOT_FOUND`（软删 → 物理删 items） |
| POST | `/api/meals/{id}/restore` | — | `{data: MealView}` | `MEAL_NOT_FOUND` |

### 2.2 Food（5 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/foods` | query: `?owner=system\|user&q=&category=&page=&limit=` | `{data: FoodView[], meta}` | — |
| GET | `/api/foods/search` | query: `?q=` | `{data: FoodView[]}`（含 aliases 匹配） | — |
| POST | `/api/foods` | `FoodCreateRequest` | `{data: FoodView}` | `VALIDATION_FAILED` / `NEGATIVE_NUTRIENT`（BR-13） |
| PUT | `/api/foods/{id}` | `FoodCreateRequest` | `{data: FoodView}` | `FOOD_NOT_FOUND` / `FOOD_SYSTEM_READONLY` |
| DELETE | `/api/foods/{id}` | — | `{message: "ok"}`（软删 + meal_items.food_id 置 NULL） | `FOOD_SYSTEM_READONLY` |

### 2.3 Stats（2 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/meals/stats` | query: `?from=&to=&granularity=day\|week` | `{data: StatsView}` |
| GET | `/api/meals/stats/weekly` | — | `{data: WeeklyNutritionView}`（走物化视图） |

### 2.4 Profile（3 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/meals/profile` | — | `{data: ProfileView}`（首次返回空） |
| PUT | `/api/meals/profile` | `ProfileRequest` | `{data: ProfileView}`（PRD MEAL-024） |
| POST | `/api/meals/profile/recompute-target` | — | `{data: ProfileView}`（按 BMR + 活动量算 daily_kcal_target） |

### 2.5 DietReadPort（跨模块只读契约）

```java
public interface DietReadPort {
    Optional<MealSnapshot> findById(Long userId, Long mealId);
    List<MealSnapshot> findInRange(Long userId, Instant from, Instant to);
    long sumKcalInRange(Long userId, Instant from, Instant to);          // cents
    Map<LocalDate, Long> sumKcalByDayInRange(Long userId, LocalDate from, LocalDate to);
    Optional<UserProfileSnapshot> findProfile(Long userId);
}
```

## 3. 数据模型（V7 + V11 + V13 + V2 user_profiles）

| 表 | 关键字段 | BR |
|---|---|---|
| `foods` | `owner_user_id NULL=系统` / `aliases JSONB` / `kcal_per_100g INT` / `protein_g/carb_g/fat_g NUMERIC` | BR-13 营养素 ≥ 0 |
| `meals` | `type ∈ {BREAKFAST,LUNCH,DINNER,SNACK}` / `total_kcal_cents BIGINT`（聚合后写入） | BR-11 |
| `meal_items` | `servings > 0` / `food_id NULL=deleted` / `manual_kcal_cents`（食物被删时手动指定） | BR-12 |
| `user_profiles` | `height_cm/weight_kg/gender/activity_level/daily_kcal_target` | PRD MEAL-024 |

**分区**：`meals` 按月分区（V11），分区键 `occurred_at`。

**物化视图**：`mv_meal_nutrition_weekly`（V13）— 按周聚合 `(user_id, period_year, period_week) → total_kcal/protein/carb/fat`，每日 02:30 REFRESH CONCURRENTLY（与 expense MV 02:00 错峰，H2/M5）。

索引：
- `idx_foods_owner_name` ON `foods(COALESCE(owner_user_id, 0), name)`
- GIN 索引 `idx_foods_aliases` ON `foods USING GIN(aliases jsonb_path_ops)`
- `idx_meals_user_occurred` ON `meals(user_id, occurred_at DESC)`
- `idx_meal_items_meal` ON `meal_items(meal_id)`

## 4. Outbox 事件（1 条）

| event_type | 触发 | payload | 消费方 |
|---|---|---|---|
| `meal.created` | meals INSERT（非软删） | `{meal_id, user_id, type, occurred_at, total_kcal_cents}` | ai（月度营养聚合增量）（C1：删除 `export（周报）` 误标；export 模块是只读跨域聚合，按需直连 ReadPort，不订阅 outbox 事件） |

## 5. 关键验收场景（TDD 种子）

### 5.1 Meal CRUD

- `meal_create_should_reject_type_invalid`：非 {BREAKFAST,LUNCH,DINNER,SNACK} → 400（BR-11）
- `meal_create_should_reject_items_empty`：items 为空 → 400
- `meal_create_should_calculate_total_kcal`：根据 items.servings * food.kcal_per_100g 聚合
- `meal_create_should_reject_food_not_found`：foodId 不存在或 userId 不匹配 → 404
- `meal_create_should_handle_food_deleted`：food 软删时使用 manual_kcal_cents 兜底
- `meal_update_should_recalculate_kcal`：改 items → 重新聚合
- `meal_delete_should_soft_delete_and_cascade_items`：软删 meals + 物理删 meal_items
- `meal_query_should_partition_prune`：查询 7 月只命中 `meals_2026_07` 分区

### 5.2 Food

- `food_create_should_reject_negative_kcal`：`kcal_per_100g < 0` → 400（BR-13）
- `food_create_should_reject_negative_macros`：protein/carb/fat 任一 < 0 → 400
- `food_update_should_reject_when_system`：系统食物 owner_user_id=NULL → 400
- `food_search_should_match_aliases`：q="西红柿" 命中 aliases 含「西红柿」
- `food_search_should_use_gin_index`：查询命中 GIN
- `food_delete_should_set_meal_items_food_null`：食物软删 → 相关 meal_items.food_id = NULL

### 5.3 NutritionCalculator

- `nutrition_should_aggregate_kcal`：1 份（100g）米饭 → servings * kcal_per_100g
- `nutrition_should_aggregate_macros`：protein/carb/fat 按 servings 比例
- `nutrition_should_use_manual_when_food_null`：food_id=NULL 用 manual_kcal_cents

### 5.4 Stats（物化视图）

- `stats_should_use_materialized_view`：查询命中 `mv_meal_nutrition_weekly`
- `stats_should_refresh_concurrently`：REFRESH 不阻塞读
- `stats_should_compare_to_target`：返回 vs daily_kcal_target 完成率

### 5.5 Profile（PRD MEAL-024）

- `profile_should_compute_bmr`：Mifflin-St Jeor 公式（按 gender 区分）
- `profile_should_compute_target_kcal`：BMR × activity_level 系数
- `profile_should_recompute_on_height_weight_change`：改身高体重 → target_kcal 重算
- `profile_recompute_should_not_overwrite_manual_target`：用户手动指定 target 时不覆盖

### 5.6 Outbox

- `meal_should_emit_created_event`：创建 → outbox 写 meal.created
- `outbox_should_rollback_on_business_failure`：service 异常 → outbox 不写入

### 5.7 Port（其他模块集成）

- `port_should_sum_kcal_in_range`：ai 模块调 `sumKcalInRange`
- `port_should_sum_kcal_by_day`：日卡路里摄入
- `port_should_find_profile`：获取目标 kcal

### 5.8 UI（浏览器手动验证）

- `ui_meal_timeline_should_show_by_type`：早中晚晚餐按时间轴
- `ui_food_search_should_show_aliases_match`：中文别名搜索
- `ui_nutrition_bar_should_compare_target`：今日 vs 目标进度
- `ui_responsive_mobile`：移动端餐次卡片

## 6. 验收标准

- [ ] 16 个 API 端点全部实现 + Swagger 文档
- [ ] 4 张表（含 1 个分区表）Repository 单测覆盖率 ≥ 85%
- [ ] 物化视图 `mv_meal_nutrition_weekly` 每日 02:30 刷新成功
- [ ] 1 条 Outbox 事件注册到 EventType 枚举
- [ ] DietReadPort 暴露给其他模块
- [ ] 系统默认食物库预置
- [ ] 关键路径 100% 覆盖（卡路里计算 / 食物搜索 / profile 目标）
- [ ] UI 主界面浏览器手动验证
- [ ] PRD 04 §BR 全部覆盖（BR-11/12/13 + MEAL-024）

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 食物被删后 meal_items 营养丢失 | 中 | BR-12 手动 kcal_cents 兜底 |
| 卡路里聚合浮点误差 | 高 | 一律 `BIGINT cents` 计算（与 expense 一致） |
| aliases 中文拼音混排 | 中 | GIN 索引 + `simple` 配置 + 二分查找 |
| 物化视图刷新阻塞读 | 低 | CONCURRENTLY + 凌晨 02:30 + UNIQUE INDEX |
| profile 目标 kcal 计算偏差 | 中 | 标准 BMR 公式 + 用户可手动覆盖 |
| 系统食物被改/删 | 高 | owner_user_id=NULL → service 校验拒绝 |
| meal_items 食物被软删致查询失败 | 低 | food_id=NULL 时 manual_kcal_cents 兜底显示 |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`
  - `plan-data-flyway.md`（V7 + V11 分区 + V13 物化视图 + V2 user_profiles）
  - `plan-shared-infra.md`
  - `plan-shared-integration.md`
  - `plan-auth.md`
  - `plan-01-task.md`（无强依赖）
  - `plan-02-daily.md`（无强依赖）
  - `plan-03-expense.md`（无强依赖）
  - `plan-observability-backup.md`（mv_meal_nutrition_weekly 监控 + meal.created 事件流 + profile 目标计算告警）
- 下游：
  - `plan-06-ai.md`（消费 meal.created + DietReadPort + 物化视图统计 + profile 目标）