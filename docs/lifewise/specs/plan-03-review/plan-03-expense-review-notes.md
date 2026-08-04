# plan-03-expense Code Review Notes

> Review date: 2026-08-02
> Reviewer: `ecc:code-reviewer` subagent
> Scope: commits `faca168..ac1587c` (4 commits, 45 files, +2136 / -1 LoC)
> Verdict: **WARNING** — 3 HIGH + 6 MEDIUM + 1 LOW

---

## 1. Summary Table

| Dimension | CRITICAL | HIGH | MEDIUM | LOW |
|---|---:|---:|---:|---:|
| 1. Module boundary | 0 | 0 | 0 | 0 |
| 2. Outbox payload contract | 0 | 0 | 0 | 0 |
| 3. Transaction boundary | 0 | 0 | 0 | 0 |
| 4. N+1 / pagination | 0 | 0 | 1 | 0 |
| 5. Exception coverage | 0 | 0 | 1 | 1 |
| 6. Money arithmetic | 0 | **1** | 1 | 0 |
| 7. Immutability | 0 | 0 | 3 | 0 |
| 8. Soft delete | 0 | 0 | 0 | 0 |
| 9. Cross-user access | 0 | 0 | 0 | 0 |
| 10. Schema/code mismatch | 0 | **1** | 0 | 0 |
| 11. Outbox idempotency | 0 | **1** | 0 | 0 |
| Additional: concurrency | 0 | 0 | 1 | 0 |
| **Total** | **0** | **3** | **6** | **1** |

---

## 2. HIGH findings (BLOCK before merge)

### H1. Money arithmetic — `int amountCents` ↔ INT contract (verified 2026-08-02)

**Status**: demoted to MEDIUM (deferred to v1.1+). No active bug in v1.0.

**Verification evidence** (grep V6/V10/V12/V38):
- `V6 L53`: `expenses.amount_cents INT NOT NULL CHECK (amount_cents > 0)`
- `V6 L79`: `budgets.amount_cents INT NOT NULL CHECK (amount_cents > 0)`
- `V10 L63 COMMENT`: "BR-09 金额统一为分（int）"
- `V38`: did not modify amount_cents column type
- `V12 view`: SUM/AVG/MIN/MAX aggregate on INT; PG auto-promotes INT→BIGINT
  for SUM, so multi-year aggregation does not overflow

**Conclusion**: DB INT + Java int are fully aligned. No type mismatch, no
overflow path. INT capacity (~$21M per row) is sufficient for personal
life management scope in v1.0.

**Defer to v1.1+**: widen to BIGINT only if scope expands (multi-account
aggregation, long-horizon accumulation beyond $21M/row).

---

### H2. Schema/code mismatch — `budgets.category_id` NOT NULL vs `0L` sentinel

**File**: `app/src/main/resources/db/migration/V38__expense_budget_category_extension.sql:23-32`
**File**: `app/src/main/java/com/lifewise/expense/service/BudgetService.java:55`
**File**: `app/src/main/java/com/lifewise/expense/domain/Budget.java`

**Issue**:
- V38 schema 把 `scope` 加为 NOT NULL DEFAULT 'CATEGORY'，但未把 `category_id` 改为 nullable
- V38 COMMENT 声称 "TOTAL scope 时 category_id 应为 NULL"
- `BudgetService` 用 `0L` sentinel 绕过 NOT NULL 约束
- 领域层 `Budget.create(TOTAL)` 又要求 `categoryId` 非空（破坏 invariant）

**Fix strategy**:
1. **新增** `V39__budget_category_id_nullable.sql`：
   ```sql
   ALTER TABLE budgets ALTER COLUMN category_id DROP NOT NULL;
   DROP CONSTRAINT uq_budgets_user_scope_period;
   CREATE UNIQUE INDEX uq_budgets_total_period
       ON budgets(user_id, period_year_month)
       WHERE scope = 'TOTAL' AND deleted_at IS NULL;
   CREATE UNIQUE INDEX uq_budgets_category_period
       ON budgets(user_id, category_id, period_year_month)
       WHERE scope = 'CATEGORY' AND deleted_at IS NULL AND category_id IS NOT NULL;
   ```
2. `BudgetService.CATEGORY_ID_SENTINEL = 0L` 删除
3. `BudgetService.create()` 在 TOTAL scope 直接传 `null`
4. `Budget.domain.categoryId` 类型不变（已是 `Long` 字段），仅移除 null 校验即可
5. `BudgetView.from()` 不变
6. `ExpenseReadPort.sumByCategoryInRange(Long, Long, ...)` 在 `categoryId == null` 时需 NPE 守卫（实际不可能，因为 expense 必填）

**影响范围**：1 新增 migration + 1 service 修改 + 1 domain 字段 nullable（不改字段类型）。无 commit history 改动（V39 是新 commit）。

---

### H3. Outbox idempotency — BudgetEvaluator 重复发阈值事件

**File**: `app/src/main/java/com/lifewise/expense/service/BudgetEvaluator.java:64-85`

**Issue**: 同一预算达到阈值后，每次新建 expense 都会再次触发 `BUDGET_THRESHOLD` 事件，没有幂等控制。下游通知模块会收到多条同预算同阈值的重复提醒。

**Fix strategy**（任选其一）：

**方案 A（推荐）— 预算级 period 幂等键**：
1. 新增 `budget_notifications` 表：
   ```sql
   CREATE TABLE budget_notifications (
       id BIGSERIAL PRIMARY KEY,
       budget_id BIGINT NOT NULL,
       threshold_pct INT NOT NULL,
       period_year_month VARCHAR(7) NOT NULL,
       sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       UNIQUE (budget_id, threshold_pct, period_year_month)
   );
   ```
2. `BudgetEvaluator.evaluateOnExpense()` 在 append outbox 前先尝试 insert 该表；捕获 unique violation → 已发过，跳过
3. `Budget` schema 加列（如需）以标记 `last_notified_threshold_pct`

**方案 B（更轻量）— 内存 + 周期重置**：
- 引入 `ConcurrentHashMap<Long, OffsetDateTime>` 记录"已通知预算 + 周期"
- 跨周期 / 跨进程重启会丢（不推荐生产）

**方案 C（最简单）— 阈值跨越判断**：
- 维护 `previous_used < threshold && current_used >= threshold` 才发
- 用 in-memory state（同样有跨进程问题）

**影响范围**：方案 A 需新增 1 migration + 1 entity + 1 repository + evaluator 改写。最大，但最稳。方案 B/C 简单但有进程边界问题。

---

## 3. MEDIUM findings (track, may fix before merge)

### M1. N+1 in `BudgetController.list`
- **File**: `app/src/main/java/com/lifewise/expense/controller/BudgetController.java:36-40`
- 每 budget 单独调 `usedCents()` → N 次 `sumInRange`/`sumByCategoryInRange` 查询
- **Fix**: Repository 加批量 `sumByBudgetScopeInRange(...)`，Service 内存构造 Map<Long, Long> 后 O(1) 查询
- 现实场景中预算数量 ≤ 12（12 分类 + 1 总），问题影响小但建议优化

### M2. `EXPENSE_INVALID_AMOUNT` ErrorCode 未映射
- **File**: `app/src/main/java/com/lifewise/expense/controller/ExpenseExceptionHandler.java:66-70`
- 现有金额非法抛 `IllegalArgumentException`，handler 映射为 `INVALID_INPUT`
- **Fix**: 新增 `ExpenseInvalidAmountException extends RuntimeException`；Expense/Budget.create 改抛此异常；handler 新增 `@ExceptionHandler(ExpenseInvalidAmountException.class)` → `EXPENSE_INVALID_AMOUNT`
- 影响：1 新增 exception + 2 domain 修改 + 1 handler 新增

### M3. `BUDGET_ALREADY_EXISTS` ErrorCode 无 thrower
- **File**: `app/src/main/java/com/lifewise/shared/integration/dto/ErrorCode.java:47`
- 创建重复预算时，DB UNIQUE 违反抛 `DataIntegrityViolationException`，handler 未映射
- **Fix**: `BudgetService.create()` 捕获 `DataIntegrityViolationException` → 抛 `BudgetAlreadyExistsException`，handler 映射为 `BUDGET_ALREADY_EXISTS`
- 影响：1 新增 exception + 1 service try/catch + 1 handler 新增

### M4. `BudgetEvaluator` 浮点计算 `thresholdRatio`
- **File**: `app/src/main/java/com/lifewise/expense/service/BudgetEvaluator.java:67`
- `double thresholdRatio = b.getAlertThresholdPct() / 100.0` 违反 "金额边界避免浮点"
- **Fix**: payload 改用 `int threshold_pct`（整数百分比）；删除 `thresholdRatio` 浮点
- 影响：1 payload 字段类型 + 1 evaluator 改写

### M5. `Budget.applyUpdate()`/`muteUntil()` 公开可变方法
- **File**: `app/src/main/java/com/lifewise/expense/domain/Budget.java:98-120`
- **Fix**: 限制为 package-private，由 Service 通过工厂命令统一调用
- 影响：1 domain 访问修饰符调整

### M6. `Expense.applyUpdate()` 公开可变方法 + 缺跨实体校验
- **File**: `app/src/main/java/com/lifewise/expense/domain/Expense.java:98-118`
- **Fix**: 同 M5；Service 层加 update 路径发 `EXPENSE_UPDATED` event（如需要）
- 影响：1 domain 访问修饰符 + 可选 event emit

### M7. `ExpenseCategory.rename()` 长度校验顺序
- **File**: `app/src/main/java/com/lifewise/expense/domain/ExpenseCategory.java:84-90`
- trim 前校验 length，"a"×50 + " " 会被拒但实际只存 50 字符
- **Fix**: 先 `newName = newName.trim()`，再校验 length
- 影响：1 domain 方法微调

### M8. `CategorySeedService.ensureUserDefault()` 非并发安全
- **File**: `app/src/main/java/com/lifewise/expense/service/CategorySeedService.java:27-30`
- 两并发请求可能都看到空、都 insert，导致重复"其他"
- **Fix**: 捕获 unique violation 后重新查询；或加 SELECT FOR UPDATE
- 影响：1 service try/catch

---

## 4. LOW finding

### L1. `BUDGET_ALREADY_EXISTS` ErrorCode 当前无 thrower
- 同 M3。优先合并处理。

---

## 5. Recommended commit plan for next session

按 HIGH 阻塞优先级：

| 顺序 | Commit | 内容 | 文件数估算 |
|------|--------|------|------|
| 1 | `fix(expense): int→long for amount cents (BIGINT contract)` | 6 文件类型调整 + 测试（如果有） | 6-8 |
| 2 | `fix(expense): V39 budgets category_id nullable + remove 0L sentinel` | 1 migration + BudgetService + Budget | 3 |
| 3 | `fix(expense): BudgetEvaluator idempotency via budget_notifications table` | 1 migration + entity + repo + evaluator | 4-5 |
| 4 | `feat(expense): dedicated amount/duplicate exceptions + handlers` | 3 exception + 2 service + 1 handler | 5 |
| 5 | `test(expense): unit + integration + test report (≥80% coverage)` | ~15 测试 + 1 报告 | 16 |

预计总文件 ~35。下次 session 启动可直接按此 backlog 推进。

---

## 6. Session handover brief

**当前 working tree 状态**:
- ✅ mvn compile BUILD SUCCESS
- ✅ 4 commits 已落地（faca168 / f4754b8 / b5ee6de / ac1587c）
- ✅ working tree 仅剩 daily/、meal 其它、meal/test、V38-daily、V39-diet、docs/testing/ 等 plan-02/plan-04 范围 untracked

**下一步建议**:
1. 先 commit HIGH fixes（按上面 5 commit plan）
2. 再做 commit 4（测试 + 报告）

**关键文件位置**:
- 实施方案：`docs/lifewise/planning/plan-03-expense.md`
- PRD：`docs/lifewise/specs/PRD/03-expense-management.md`
- 数据模型：`docs/lifewise/architecture/data-model-v1.2-amendment.md`
- 已落地代码：`app/src/main/java/com/lifewise/expense/` (45 文件)

**红线提醒**（CLAUDE.md §10）:
- 修改 `budgets.category_id` NOT NULL → NULL 属于 schema 变更，必须先 Flyway migration（V39）+ 评审
- 任何 `git push` / force push 必须先与用户确认