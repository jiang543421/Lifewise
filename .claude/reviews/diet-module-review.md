# Code Review: diet module (backend-diet branch) — REVISED

**Reviewed**: 2026-08-03 (revised after user challenge)
**Branch**: `backend-diet` (uncommitted)
**Scope**: 47 source files + V40 migration + 12 test files
**Decision**: **REQUEST CHANGES** — 走完补充 IT 后重新评审

## TL;DR — 修正要点

| 旧判定 | 用户复核 | 修正后 |
|--------|----------|--------|
| H1: note 静默丢失 | ✅ 属实 | **保留 HIGH** |
| H2: 双删路径 / race | ⚠️ 定性错 | **重写为 softDelete+restore 不对称 = 永久数据丢失** |
| H3: aliases 大小写 | ✅ 属实但 MEDIUM | **降级为 MEDIUM** |
| H4: 强制 join 抵消 partition pruning | ❌ 误报 | **撤销**（meal_items 非分区表；FK 复合 PK 要求 dual condition） |
| H5: 死 override | ✅ 事实对但无功能影响 | **降级 LOW** |
| M11: MealNotFound → FOOD_NOT_FOUND | 保留 | 本次合入 H 一并处理 |
| （漏）softDelete + restore 不对称 | 真 HIGH | **新增 HIGH** |
| （漏）kcal → cents 截断 | 真 MEDIUM | **新增 MEDIUM** |
| **（漏）验证结论无效** | diet 0 IT | **新增 BLOCKER** |

---

## 1. BLOCKER — diet 模块在 Postgres 上零 IT 覆盖

`app/src/test/java/com/lifewise/diet/` 实际 12 个文件 = 10 个 mock unit + 4 个 `@WebMvcTest`，**0 个 `*IT.java`**。对照 `task` / `daily` 各有 `*E2EAndOutboxIT`。

`mvn verify` 报告的 384 tests / 83.7% 覆盖率里：
- 50 IT 全部是 `FlywayMigrationIT` (10) / `DailyE2EAndOutboxIT` (24) / `TaskE2EAndOutboxIT` (7) / `JpaOutboxEventRepositoryIT` (7) / `SharedIntegrationContextTest` (4) — **没有一行 diet SQL 实际打到 Postgres / Testcontainers PG**。
- 83.7% 是 mock 覆盖率，对以下问题零信号：
  - `searchByNameOrAlias` 原生查询 `aliases @> to_jsonb(ARRAY[:q])` —— JDBC 绑定 `ARRAY[?]` 时 PG 报 `could not determine polymorphic type` 是运行时炸的
  - JPQL bulk delete + orphanRemoval 的 flush 顺序
  - 复合 FK `(meal_id, local_date)` 的级联行为
  - meals 分区表 + Hibernate IDENTITY 主键的 findById 全分区扫描（这是 daily/expense/task 共有历史问题，不属本 PR）
  - 物化视图刷新

> **判定优先级：先补 IT，再讨论 HIGH / MEDIUM。** 没有 IT，H1 的回归测试不挂在 PG 上等于没写。

---

## 2. HIGH — 真正的 blocker

### H1. `update` 静默丢 `note`（保留原 H1）
`MealService.java:131-135` 整段是注释，never writes。

**修复**：实体加 `Meal.setNote(String)`；service 分支判 `req.note() != null` 调用。
**测试**：`MealService.update ... should persist new note`。

### H2. softDelete + restore 不对称 → 永久数据丢失（原 H2 误判，重写）
`MealService.java:141-146` 软删时**物理删**所有 items；`MealService.java:149-158` 恢复时**只清 `deletedAt`**，不再插回 items。

后果：用户从回收站 "恢复" 餐次，items list 为空 + `total_kcal_cents` 残留旧值。CLAUDE.md §7.5 / 业务语义上**recovery 不可逆**。

`MealServiceTest.java:196` 把 `softDelete` 之后 items 为空当 "expected" 固化进测试 —**给永久丢数据签发了认证**。

**修复**（二选一）：
- **(A) 软删 items**：meal_items 加 `deleted_at` 列（V41 migration），`softDelete` 仅置位 + `restore` 复位；与 `meals.deleted_at` 同步。
- **(B) 明确语义 + API 文档**：`POST /meals/{id}/restore` 不恢复 items，文档顶部大字写明。配套 service 注释 + 测试断言。

**(A) 推荐**——v1.0 单用户没迁移复杂度，是补 1 张 partition 列 + 调整 3 个 service 调用的成本。

### H3. `update` 静默忽略 `type` / `localDate` / `timezone`（原 M2 升 HIGH）
与 H1 同根：当前 `update` 只换 items，其余字段静默接受后丢弃。`type` 在 `MealCreateRequest` 是 `@NotNull`，客户端调用方必然传 PUT。

**修复**：
- 走 `(A)` 则 `update` 严格 PUT 语义，所有字段必须传并落库 —— 配合 DTO 加 `@AssertTrue` 校验 `localDate != null` 等。
- 走 `(B)` 则 `update` 注解 `@RequestBody @Valid MealCreateRequest`，断言 `req.type() / req.localDate()` 与现有 meal 字段一致，否则 400。

**v1.0 建议直接拒绝**：把 `update` 限制为 `items` 替换，并在 DTO 上加 `@AssertTrue("items 之外字段必须与原 meal 一致")`（service 校验），否则 400 bad request。

---

## 3. MEDIUM

### M_new. kcal → cents 静默截断
`MealService.java:85` + `:130`：
```java
meal.setTotalKcalCents(totalKcal.multiply(new BigDecimal("100")).longValue());
```
`longValue()` 对小数部分静默截断，超出 long 范围也不抛。CLAUDE.md §6.1 要求"金额计算 100% 覆盖"。

**修复**（2 行）：
```java
meal.setTotalKcalCents(totalKcal
    .multiply(new BigDecimal("100"))
    .setScale(0, RoundingMode.HALF_UP)
    .longValueExact());
```
配合 `setTotalKcalCents` 改 `try/catch ArithmeticException` → `InvalidMealException("kcal overflow")`，给前端 400。

### M3. `sumKcalByDayInRange` / `sumKcalCentsByDayInRange` 实现重复（原 M3 保留）
`StatsRepository.java:43-46` 与 `:62-65` 字节级重复。删一个。

### M11. `MealNotFoundException` 映射到 `FOOD_NOT_FOUND`（原 M11 保留）
`DietGlobalExceptionHandler.java:53-56` 误导客户端。ErrorCode 加 `MEAL_NOT_FOUND`（向前兼容，Javadoc 允许新增）。

### M6. `NutritionCalculator` food == null 行为不对称（原 M6 保留）
kcal 抛、macro 返回 ZERO。统一为全部抛 / 全部 zero。

### M7. `MealListItem.totalKcal` scale 0 vs `MealView` scale 3（原 M7 保留）
客户端同餐次字段不一致。

其他 M1 / M2（旧）/ M5 / M8 / M9 / M10 / M12（旧）保留 review 报告中的描述。

---

## 4. LOW

- **H5 降级**：dead override，无功能影响。删 4 行即可。
- **L1-L12** 全部保留原报告。
- 新增 L13：`MealService.java:192` 注释 "Page kept in import for compile-time consistency" 指向已删除的 Page 引
