# plan-03-expense 实施方案

## 参考资料

- [`docs/lifewise/specs/PRD/03-expense-tracking.md`](../specs/PRD/03-expense-tracking.md) — 产品 PRD
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.5 expense 模块边界
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V6 + V11（按月分区）+ V12（物化视图）
- [`docs/lifewise/designs/03-expense-ui/03-expense-ui-design.md`](../designs/03-expense-ui/03-expense-ui-design.md) — UI 设计契约
- [`docs/lifewise/architecture/versions/data-model-design-v1.1.1.md`](../architecture/versions/data-model-design-v1.1.1.md) §1.1.5 消费模块字段

## 参考目录

- backend：`app/src/main/java/com/lifewise/expense/`
  - `controller/` — ExpenseController / CategoryController / BudgetController / StatsController
  - `service/` — ExpenseService / CategoryService / BudgetService / BudgetEvaluator / StatsService / CategorySeedService
  - `domain/` — Expense / ExpenseCategory / Budget
  - `repository/` — ExpenseRepository / CategoryRepository / BudgetRepository
  - `port/` — ExpenseReadPort（暴露给其他模块）
  - `event/` — ExpenseCreated / BudgetThreshold
  - `dto/` — ExpenseCreateRequest / ExpenseView / CategoryView / BudgetRequest / BudgetView / StatsView
- frontend：`docs/lifewise/designs/03-expense-ui/`
  - `new-03-expense-ui.html` — 主界面原型（账单列表 + 分类饼图 + 预算条）

## 1. 模块边界 / 包结构

expense 模块是用户**消费追踪**的入口，独立模块，与其他业务模块无强依赖（仅 ai 通过 Port 消费）。

```
expense/
├── controller/
│   ├── ExpenseController.java         /api/expenses CRUD（按月分区）
│   ├── CategoryController.java        /api/expense-categories CRUD（BR-23/24）
│   ├── BudgetController.java          /api/budgets CRUD + 预算评估触发
│   └── StatsController.java           /api/expenses/stats?from=&to=（聚合）
├── service/
│   ├── ExpenseService.java            创建/更新/软删（写 outbox）
│   ├── CategoryService.java           系统/自定义分类 + 「其他」预置 + 删除迁移（BR-20/24）
│   ├── BudgetService.java             创建/更新预算（scope=CATEGORY|TOTAL）
│   ├── BudgetEvaluator.java           写入账单后评估预算（80%/100% 触发 Web Push）
│   ├── StatsService.java              按分类 / 时间聚合（走物化视图）
│   └── CategorySeedService.java       首次注册 → 预置系统分类 + 「其他」（BR-24）
├── domain/
│   ├── Expense.java                   expenses 表实体（按月分区）
│   ├── ExpenseCategory.java           expense_categories 表实体
│   └── Budget.java                    budgets 表实体
├── repository/
│   ├── ExpenseRepository.java
│   ├── CategoryRepository.java
│   └── BudgetRepository.java
├── port/
│   └── ExpenseReadPort.java           实现 ExpenseReadPortAdapter
├── event/
│   ├── ExpenseCreatedEvent.java       payload: {expense_id, user_id, amount_cents, category_id, occurred_at}
│   └── BudgetThresholdEvent.java      payload: {user_id, budget_id, threshold: 0.8|1.0, used_cents, total_cents}
└── dto/
    ├── ExpenseCreateRequest.java      {category_id, amount_cents, pay_method, occurred_at, note}
    ├── ExpenseView.java
    ├── CategoryCreateRequest.java     {name, icon?, color?, parent_id?}
    ├── CategoryView.java
    ├── BudgetRequest.java             {scope, category_id?, period_year, period_month, amount_cents, notify_enabled}
    ├── BudgetView.java                含 used_cents / usage_pct
    └── StatsView.java                 {total_cents, by_category: [{category_id, name, amount_cents, pct}]}
```

## 2. API 契约

### 2.1 Expense CRUD（6 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/expenses` | query: `?year=&month=&category_id=&page=&limit=` | `{data: ExpenseListItem[], meta}` | — |
| GET | `/api/expenses/{id}` | — | `{data: ExpenseView}` | `EXPENSE_NOT_FOUND` |
| POST | `/api/expenses` | `ExpenseCreateRequest` | `{data: ExpenseView}` | `VALIDATION_FAILED` / `CATEGORY_NOT_FOUND` / `INVALID_AMOUNT`（BR-09） |
| PUT | `/api/expenses/{id}` | `ExpenseCreateRequest` | `{data: ExpenseView}` | `EXPENSE_NOT_FOUND` |
| DELETE | `/api/expenses/{id}` | — | `{message: "ok"}` | `EXPENSE_NOT_FOUND`（软删） |
| POST | `/api/expenses/{id}/restore` | — | `{data: ExpenseView}` | `EXPENSE_NOT_FOUND`（恢复软删） |

### 2.2 Category（5 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/expense-categories` | query: `?system=true&archived=false` | `{data: CategoryView[]}` | — |
| POST | `/api/expense-categories` | `CategoryCreateRequest` | `{data: CategoryView}` | `CATEGORY_NAME_EXISTS`（BR-23） |
| PUT | `/api/expense-categories/{id}` | `CategoryCreateRequest` | `{data: CategoryView}` | `CATEGORY_NOT_FOUND` / `CATEGORY_PROTECTED`（BR-24 不可改「其他」） |
| DELETE | `/api/expense-categories/{id}` | — | `{message: "ok"}` | `CATEGORY_PROTECTED`（BR-24）/ `CATEGORY_HAS_BUDGET`（先删预算） |
| GET | `/api/expense-categories/system` | — | `{data: CategoryView[]}`（系统分类） | — |

### 2.3 Budget（5 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/budgets` | query: `?period_year=&period_month=` | `{data: BudgetView[]}`（含 used_cents） | — |
| POST | `/api/budgets` | `BudgetRequest` | `{data: BudgetView}` | `BUDGET_EXISTS`（BR-10 UNIQUE）/ `INVALID_AMOUNT` |
| PUT | `/api/budgets/{id}` | `BudgetRequest` | `{data: BudgetView}` | `BUDGET_NOT_FOUND` |
| DELETE | `/api/budgets/{id}` | — | `{message: "ok"}` | — |
| POST | `/api/budgets/{id}/mute` | query: `?until=ISO_DATE` | `{data: BudgetView}` | `BUDGET_NOT_FOUND`（H-5 notify_muted_until） |

### 2.4 Stats（1 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/expenses/stats` | query: `?from=&to=&group_by=category\|day` | `{data: StatsView}` |

实现走物化视图 `mv_expense_monthly_category`，日终刷新。

### 2.5 ExpenseReadPort（跨模块只读契约）

```java
public interface ExpenseReadPort {
    Optional<ExpenseSnapshot> findById(Long userId, Long expenseId);
    List<ExpenseSnapshot> findInRange(Long userId, Instant from, Instant to);
    List<ExpenseSnapshot> findByCategory(Long userId, Long categoryId, int limit);
    long sumInRange(Long userId, Instant from, Instant to);
    Map<Long, Long> sumByCategoryInRange(Long userId, Instant from, Instant to);
}
```

## 3. 数据模型（V6 + V11 + V12）

| 表 | 关键字段 | BR |
|---|---|---|
| `expense_categories` | `user_id NULL=系统` / `parent_id NULL` / `is_user_default` / `is_archived` | BR-23/24 |
| `expenses` | `amount_cents BIGINT > 0` / `pay_method` / `occurred_at` | BR-09 |
| `budgets` | `scope ∈ {TOTAL,CATEGORY}` / `UNIQUE(user_id, scope, category_id, period_year, period_month)` / `notify_enabled` + `notify_muted_until` | BR-10 / H-5 |

**分区**：`expenses` 按月分区（V11），分区键 `occurred_at`。

**物化视图**：`mv_expense_monthly_category`（V12）— `(user_id, period_year, period_month, category_id) → total_cents`，每日 02:00 REFRESH CONCURRENTLY。

索引：
- `uq_expense_categories_user_name` ON `expense_categories(COALESCE(user_id, 0), name)`（BR-23 双场景）
- `idx_expenses_user_occurred` ON `expenses(user_id, occurred_at DESC)`
- `idx_expenses_user_category_occurred` ON `expenses(user_id, category_id, occurred_at DESC)`
- `uq_budgets_user_scope_period`（BR-10）
- `idx_budgets_user_period` ON `budgets(user_id, period_year, period_month)`

## 4. Outbox 事件（2 条）

| event_type | 触发 | payload | 消费方 |
|---|---|---|---|
| `expense.created` | expenses INSERT/UPDATE（非软删） | `{expense_id, user_id, amount_cents, category_id, occurred_at}` | ai（月度聚合增量）/ **notify（预算评估触发；B1 X7）** |
| `budget.threshold` | BudgetEvaluator 检测到 80%/100% | `{user_id, budget_id, threshold: 0.8\|1.0, used_cents, total_cents}` | **notify（Web Push；B2 X7）** |

注：BudgetEvaluator 是异步监听 `expense.created` 的内部组件，写 `budget.threshold` 事件后由 notify 模块订阅后投递 Web Push（X7 对齐；plan-shared-integration §4 为权威）。

## 5. 关键验收场景（TDD 种子）

### 5.1 Expense CRUD

- `expense_create_should_reject_amount_zero`：`amount_cents <= 0` → 400（BR-09）
- `expense_create_should_reject_amount_overflow`：`amount_cents > 9_999_999_999` → 400
- `expense_create_should_reject_category_not_found`：categoryId 不存在或 userId 不匹配 → 404
- `expense_create_should_reject_category_archived`：归档分类 → 400
- `expense_update_should_recompute_budget`：改 amount → 触发 BudgetEvaluator
- `expense_delete_should_soft_delete_and_unbudget`：软删 → 已用金额回退
- `expense_restore_should_recompute_budget`：恢复 → 重新计入预算
- `expense_query_should_partition_prune`：查询 7 月只命中 `expenses_2026_07` 分区

### 5.2 Category

- `category_seed_should_create_default_other`：首次注册 → 自动创建 is_user_default=true「其他」（BR-24）
- `category_create_should_reject_duplicate_name`：同用户下 name 重复 → 409（BR-23）
- `category_update_should_reject_when_protected`：改「其他」name → `CATEGORY_PROTECTED`（BR-24）
- `category_delete_should_migrate_expenses_to_default`：删除前把所有账单归到「其他」（BR-20）
- `category_delete_should_reject_when_protected`：删「其他」 → `CATEGORY_PROTECTED`
- `category_delete_should_reject_when_has_budget`：分类下还有未删预算 → 400
- `category_archive_should_soft_hide`：`is_archived=true` 不出现在默认列表

### 5.3 Budget

- `budget_create_should_reject_amount_zero`：`amount_cents <= 0` → 400（BR-10）
- `budget_create_should_reject_duplicate`：同 scope/period/category 已存在 → 409
- `budget_update_should_recompute_usage`：amount 变更 → used_cents 重算
- `budget_query_should_include_usage`：返回 `used_cents / amount_cents / usage_pct`
- `budget_mute_should_set_until`：mute until → notify_muted_until 写入（H-5）
- `budget_mute_should_skip_threshold_evaluation`：mute 期内 budget.threshold 不触发

### 5.4 BudgetEvaluator（关键路径）

- `evaluator_should_trigger_at_80_percent`：累计用 80% → 发 budget.threshold(0.8)
- `evaluator_should_trigger_at_100_percent`：累计用 100% → 发 budget.threshold(1.0)
- `evaluator_should_not_trigger_below_80`：75% 不触发
- `evaluator_should_not_trigger_twice`：同一周期 80% 已触发，再插入不重复
- `evaluator_should_skip_muted_budget`：notify_muted_until > NOW → 不触发
- `evaluator_should_handle_total_and_category_scope`：两种 scope 都覆盖

### 5.5 Stats（物化视图）

- `stats_should_use_materialized_view`：查询命中 `mv_expense_monthly_category`
- `stats_should_refresh_concurrently`：REFRESH 不阻塞读
- `stats_should_aggregate_by_category`：分类饼图正确
- `stats_should_aggregate_by_day`：按日趋势正确

### 5.6 Outbox

- `expense_should_emit_created_event`：创建 → outbox 写 expense.created
- `outbox_should_rollback_on_business_failure`：service 异常 → outbox 不写入

### 5.7 Port（其他模块集成）

- `port_should_sum_by_category`：ai 模块调 `sumByCategoryInRange`
- `port_should_sum_in_range`：月总消费统计

### 5.8 UI（浏览器手动验证）

- `ui_expense_list_should_render`：账单列表正确
- `ui_category_pie_should_show`：分类饼图正确
- `ui_budget_bar_should_show_usage`：预算条 + 百分比
- `ui_budget_mute_should_show_until`：mute 状态显示
- `ui_responsive_mobile`：移动端布局

## 6. 验收标准

- [ ] 17 个 API 端点全部实现 + Swagger 文档
- [ ] 3 张表（含 1 个分区表）Repository 单测覆盖率 ≥ 85%
- [ ] 物化视图 `mv_expense_monthly_category` 每日 02:00 刷新成功
- [ ] 2 条 Outbox 事件注册到 EventType 枚举
- [ ] ExpenseReadPort 暴露给其他模块
- [ ] 首次注册自动预置「其他」分类
- [ ] 关键路径 100% 覆盖（金额计算 / BudgetEvaluator / 物化视图）
- [ ] UI 主界面浏览器手动验证
- [ ] PRD 03 §BR 全部覆盖（BR-09/10/20/23/24 + H-5）

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 金额浮点累加误差 | 高 | 一律 `BIGINT cents`（PRD EXP §8 已明确） |
| BudgetEvaluator 重复触发 | 中 | 阈值事件去重（period + threshold UNIQUE） |
| 物化视图刷新阻塞读 | 低 | CONCURRENTLY + 凌晨 02:00 + UNIQUE INDEX |
| 分类删除致账单孤儿 | 中 | BR-20 应用层事务迁移到「其他」 |
| 「其他」分类被改/删 | 高 | BR-24 CHECK + 应用层守卫 |
| expense.created 事件堆积 | 中 | outbox 监控 + 分区 |
| Web Push 重复打扰 | 中 | notify_muted_until + 80%/100% 去重 |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`
  - `plan-data-flyway.md`（V6 + V11 分区 + V12 物化视图）
  - `plan-shared-infra.md`
  - `plan-shared-integration.md`
  - `plan-auth.md`
  - `plan-01-task.md`（无强依赖）
  - `plan-02-daily.md`（无强依赖）
  - `plan-observability-backup.md`（mv_expense_monthly_category 监控 + budget.threshold Prometheus 指标 + expense.created 事件流）
- 下游：
  - `plan-06-ai.md`（消费 expense.created + ExpenseReadPort + 物化视图统计）