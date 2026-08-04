# diet 模块 Retrospective (2026-08-03)

> 维护窗口：2026-08-03
> 分支：`backend-diet`
> 范围：4 commits（680ac9d → 6d72ad8）
> 关联：[plan-04-diet.tdd.md](../planning/plan-04-diet.tdd.md) 实施时点证据（冻结）
> 关联：[plan-04-diet.md](../planning/plan-04-diet.md) 实施期设计（不追加维护期发现）

## 1. 维护窗口概况

- 测试：384 → 392（+6 IT + 2 unit）
- `mvn verify`：BUILD SUCCESS
- 覆盖率：`service` 67 → 76%，`repository` 11 → 33.3%；bundle ≥80% gate PASS
- 文件：V41 Flyway 迁移新增；`StatsRepository` projection 接口改 getter 风格；`MealItemRepository` 删除

## 2. 4 commits 实际内容（按时间顺序）

| Hash     | 类型   | 主题                                                                     |
| -------- | ------ | ------------------------------------------------------------------------ |
| 680ac9d  | wip    | step 0+3+4+5 兜底（KNOWN ISSUES 未修复，message 留痕）                  |
| bafa104  | fix    | scale 对齐 + cents HALF_UP（Bug A / Bug B / Bug C）                      |
| 473d09c  | test   | DietE2EAndOutboxIT + V41 + projection fix                                 |
| 6d72ad8  | chore  | 清理误跟的 `.claude/diet-it-msg.txt`（commit -F 时被 `git add -A` 捎带） |

## 3. Bug 与修复（按发现顺序）

| #  | 严重度 | Bug                                                                                                                          | 修复                                       | Commit    |
| -- | ------ | ---------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ | --------- |
| 1  | P0     | softDelete + restore 互转触发 `MealItemRepository.deleteAll` 双重清 items，永久数据丢失                                       | 删 `MealItemRepository` 与调用点           | 680ac9d   |
| 2  | P1     | PUT 允许 type / localDate / timezone 变更，破坏分区键 + 复合 FK 迁移                                                        | service 层 400 reject                      | 680ac9d   |
| 3  | P1     | `MealService.toCents` 用 `longValue` 静默截断（≤ 2^63 仍可能误通过）                                                        | 改 `longValueExact`                        | 680ac9d   |
| 4  | P1     | `StatsRepository` SQL `COALESCE(SUM(...), 0)::BIGINT * 100` cast 在 ×100 之前，分位被丢（199.75 → 20000 cents 错）          | 调换为 `(SUM(...) * 100)::BIGINT`          | 680ac9d   |
| 5  | P1     | 死代码（`NutritionCalculator.kcal` 三元两边 null、Food dead override）/ `MealNotFoundException` 误映射 `FOOD_NOT_FOUND` / `sumKcalCentsByDayInRange` 与 `sumKcalByDayInRange` 内容重复 | 删死代码 / `ErrorCode.MEAL_NOT_FOUND` / 默认方法 delegate | 680ac9d   |
| 6  | **P0** | **Bug A**：toCents 缺 `setScale(0, HALF_UP)`，`computeKcalSnapshot` 返回 scale=3 时 `longValueExact()` 抛 ArithmeticException → 所有 kcal 末位非零的合法 create 被 400 拒 | 补 `setScale(0, RoundingMode.HALF_UP)`     | bafa104   |
| 7  | **P0** | **Bug B**：`NutritionCalculator` `divide(HUNDRED, 3, HALF_UP)` 与 `meal_items.kcal_snapshot NUMERIC(10,2)` 不匹配，Java 端 list 视图与 SQL 端 stats 视图 cents 口径分裂 | 4 个 `computeXxxSnapshot` 改 scale=2；4 个 `xxxG` 读方法改 `setScale(2)` | bafa104   |
| 8  | P1     | **V41**：DietE2EAndOutboxIT 触发 `meal_items.deleted_at` 列缺失（V7 建表漏写；`MealItem extends BaseEntity` 声明该列）；unit 全 mock 走不到 INSERT，IT 是首个真 INSERT 路径 | 加 `V41__add_meal_items_deleted_at.sql`    | 473d09c   |
| 9  | P1     | `StatsRepository.DayKcalRow` / `WeeklyBucketRaw` record 风格（`day()`）被 Spring Data JPA native + interface projection 拒绝：`is no accessor method` | 全部改 JavaBean getter（`getDay` 等）      | 473d09c   |

## 4. 测试增量

- 新增 `DietE2EAndOutboxIT`：6 端到端断言
  1. create meal → 1 行 meals + N 行 meal_items + `total_kcal_cents` 写库 + outbox `meal.created`
  2. update 替换 items → orphanRemoval 清旧 + cents 重算 + note 持久化
  3. softDelete + restore → `meal.deleted_at` 切换；`meal_items` 行零数据丢失
  4. foods JSONB `@>` alias 模糊搜索（V40 GIN 索引 + 系统食物 name LIKE）
  5. cents 一致性 → `StatsService.sumKcalByDayInRange` 与 `SUM(meals.total_kcal_cents)` 一致
  6. `mv_meal_nutrition_weekly` REFRESH 后周聚合命中
- 新增 2 unit（`NutritionCalculatorTest` + `MealServiceTest`）：33.33g × 99.99kcal 真实小数 fixture 锁住 Bug A / Bug B（scale=2 HALF_UP 33.33）

## 5. 当前覆盖率（JaCoCo 0.8.12，diet 模块）

| 包                                       | inst%  | line%  | 备注                                                              |
| ---------------------------------------- | ------ | ------ | ----------------------------------------------------------------- |
| `com.lifewise.diet.service`              | 73.4%  | 76.0%  | +9pp，DietE2EAndOutboxIT 覆盖 create/update/softDelete+restore 链 |
| `com.lifewise.diet.repository`           | 52.2%  | 33.3%  | +22pp，`sumKcalByDayRaw` / `weeklyBucketsRaw` 走真 PG 触发代理    |
| `com.lifewise.diet.port.out`             | 82.4%  | 75.0%  | 首次列入（`MealReadPortAdapter`）                                 |
| `com.lifewise.diet.controller`           | 95.9%  | 97.1%  | 持平                                                              |
| `com.lifewise.diet.controller.exception` | 56.5%  | 60.0%  | 持平（共享 ErrorCode 路径已被 daily / task 覆盖）                 |
| `com.lifewise.diet.dto`                  | 90.8%  | 92.6%  | 持平                                                              |
| `com.lifewise.diet.domain`               | 81.7%  | 80.8%  | 持平                                                              |
| `com.lifewise.diet.web`                  | 82.8%  | 78.6%  | 持平（CurrentUserArgumentResolver 白名单）                         |
| `com.lifewise.diet.event.payload`        | 100%   | 100%   | 持平                                                              |
| `com.lifewise.diet.config`               | 100%   | 100%   | 持平                                                              |
| **bundle（CLAUDE.md §6.1 强制 ≥80%）**   | —      | ≥80%   | **PASS**                                                          |

## 6. 已知未做 follow-ups

- diet 模块 review 中 12 MEDIUM + 12 LOW 待办（详见 `.claude/reviews/diet-module-review.md`）
- `AbstractPostgresIT` 重构：跨 3 个 IT 模块抽 base（独立分支、独立 PR、与 diet 主线解耦；2-3h 投入；任何回归都会牵连 task / daily）
- `@DataJpaTest` 补 4 个 `@Query` 把 `repository` line% 从 33.3% 拉到 85%
- 中文文档 / 用户手册（v1.1 计划）