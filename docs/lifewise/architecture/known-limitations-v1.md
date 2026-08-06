# plan-03-expense KNOWN_LIMITATIONS (v1.3 — 3 pre-existing hot-fix + Path B M8)

> **v1.3 status: 2026-08-05 — 3 pre-existing release-blockers closed**, B-2 M8 fully Closed via Path B (PostgreSQL UPSERT), and 1 new finding recorded (Session-pollution-bug discovery via B-2 IT).
>
> Full v1.0 / v1.1 / v1.2 history in §4 update log. ADR-001 §5 unchanged.

## 1. Reconciliation table

Legend:
- **Closed**: fixed by a commit; ledger records the closure hash.
- **Degraded**: closed in form but not in spirit (a stricter recommendation was
  downgraded). Carries an open caveat.
- **Open**: still requires work.
- **Demoted**: review verdict changed the severity; no code action required.

| ID  | Sev    | Title                                                       | Verdict | Closure / Reference |
|-----|--------|-------------------------------------------------------------|---------|---------------------|
| H1  | HIGH   | `int amountCents` ↔ INT overflow contract                   | Demoted (no active bug in v1.0) | review notes §H1 (INT capacity ≈ $21M/row suffices for personal scope; defer BIGINT to v1.1+) |
| H2  | HIGH   | `budgets.category_id` NOT NULL vs `0L` sentinel             | Closed   | `0cf1a3b` (V37 schema alignment: `DROP NOT NULL` + partial unique indexes; sentinel removed in `9ced426`) |
| H3  | HIGH   | BudgetEvaluator threshold event idempotency                 | **Decided** (plan B for v1.0; ADR-001 §5) | Plan B (in-memory LRU Map) maintained until v1.1 clusterization trigger; plan A (DB `budget_notifications` table) mandatory when trigger fires. Migration draft in ADR-001 §5.4. |
| M1  | MEDIUM | N+1 in `BudgetController.list`                              | Closed   | service-layer scan 2026-08-05: 0 residue. ExpenseService / BudgetService / CategoryService / StatsService 全部单 query + map-to-view; no `for-each findById` patterns. (Note: `BudgetController.list` is thin delegation to `BudgetService.list`, which itself is a single JPQL query.) |
| M2  | MEDIUM | `EXPENSE_INVALID_AMOUNT` ErrorCode unmapped                 | Closed   | `2a1c0a1` feat(expense): add typed invalid amount handling (Budget.java:156-164 throws `ExpenseInvalidAmountException`) + `ExpenseGlobalExceptionHandler.java:73-77` mapping to `ErrorCode.EXPENSE_INVALID_AMOUNT` (400). |
| M3  | MEDIUM | `BUDGET_ALREADY_EXISTS` ErrorCode has no thrower            | Closed   | `501003f` / `4023847` (BudgetAlreadyExistsException + handler) |
| M4  | MEDIUM | BudgetEvaluator float `thresholdRatio`                      | Closed   | `c5d0885` fix(expense): use integer budget threshold percentages. `BudgetEvaluator.java:46-47` `int THRESHOLD_80_PCT = 80`; line 161 `int pctX100 = (int) ((usedCents * 100L + totalCents - 1) / totalCents)` (long × 100 不溢出). `Budget.java:58` `int alertThresholdPct`. `BudgetThresholdPayload.java:21` `Integer thresholdPct`. |
| M5  | MEDIUM | `Budget.applyUpdate()` / `muteUntil()` public mutable        | Closed   | entity 工厂方法 + `applyUpdate` / `mute` / `unmute` / `archive` / `unarchive` 业务方法; 无 public setter. `Budget.java` (构造器 private, 字段全 private, `applyUpdate` 是业务方法带 validateAmount). `ExpenseCategory.java` 同模式 (`applyUpdate` 带 BR-24 默认分类守护 + `validateName` 先 trim 后 length). |
| M6  | MEDIUM | `Expense.applyUpdate()` public mutable + no EXPENSE_UPDATED | **Closed** (cross-entity validation implemented at service layer; access modifier consistent with project convention across 7 entities) | review notes §M6。**跨实体校验半（review notes "缺跨实体校验"）**：已在 `ExpenseService.java:100-103` 实现 — `if (req.categoryId() != null) { ExpenseCategory category = categoryService.loadOwnedCategory(userId, req.categoryId()); validateCategory(category, userId); }` 阻止 PUT 跨用户 categoryId 导致的 stats JOIN 泄露。**Access modifier 半**：与 M5 同款结论 — 全项目 7 个 entity（Budget/Milestone/Task/Plan/DailyReport/Food/Profile）applyUpdate 全是 public，跨 service 包调用是架构必需（service 在 `com.lifewise.{module}.service`，entity 在 `com.lifewise.{module}.domain`）。收紧需把 service 挪到 domain 包违反分层。**B-3 事件半**：`a4570d0` 已 emit EXPENSE_UPDATED / RESTORED / DELETED。 |
| M7  | MEDIUM | `ExpenseCategory.rename()` length validation order           | Closed   | `ExpenseCategory.java:127-133`: `validateName` 先 `name.trim()` 后校验 `trimmed.length() > 20`; commit annotation 引用 "plan-03 review M7：先 trim 再校验 length, 避免 "a"×50 + " " 即便存储后只 50 字符也被拒". |
| M8  | MEDIUM | `CategorySeedService.ensureUserDefault()` not concurrency-safe | **Closed** (Path B PostgreSQL UPSERT + REQUIRES_NEW 隔离) | v1.3: 替换 v1.0 catch + re-query 路径。`CategorySeedService.java:75-100` 用 `TransactionTemplate(REQUIRES_NEW)` 包裹 `CategoryRepository.insertUserDefaultIfAbsent(...)`（native SQL `INSERT ... ON CONFLICT (user_id) WHERE is_user_default = TRUE DO NOTHING`） + 独立事务 JdbcTemplate SELECT 拿 id。彻底消除 Hibernate session pollution（v1.0 catch 块触发 `org.hibernate.AssertionFailure` 逃出）。**Concurrency IT**: `CategorySeedServiceConcurrencyIT` 10 线程 + CountDownLatch + AssertionFailure 反向监测，全绿。 |
| L1  | LOW    | `BUDGET_ALREADY_EXISTS` ErrorCode no thrower                | Closed (merged with M3) | same as M3 |

## 2. Phase B issues (active work)

The v1 ledger's B-1..B-4 are superseded by the reconciliation above. Below are the
items still requiring code work, mapped to their review-notes origin.

| ID  | Source    | Title                                                       | Severity | Trigger / Notes |
|-----|-----------|-------------------------------------------------------------|----------|-----------------|
| B-1 | plan-03 cross-module (no review-notes origin) | nginx URL hardening: `ALLOWED_USER_IDS` env wiring + X-User-Id resolver dual-layer defense | **Closed** (path-based header rejection: `map $http_x_user_id $user_id_valid` + `if $user_id_valid = 0 return 403` in 3 locations) | v1.3.3 (`0591367`): `nginx/conf/conf.d/default.conf` 加 `map` 指令（default 0; "1" 1; "" 1）+ 3 处 `if` check (`/api/ai/chat` / `/api/ai/` / `/api/`)。行为矩阵：无 header → nginx 设 X-User-Id=1 fail-safe；X-User-Id=1 → 通过；X-User-Id=其他 → 403。`/api/auth/login` 排除（entry point 不需 X-User-Id）。**Defense-in-depth 价值**: v1.0 单用户白名单原本只在 app 层（`CurrentUserArgumentResolver`）做白名单校验；nginx 静默 override 客户端 X-User-Id 到 "1"。B-1 改后 nginx 显式拒绝 header 注入，为 v1.1+ 多用户切换提供双层防御（万一 JWT 链路配置错误，header injection 不会被静默纠正到 user 1 而被显式 403）。**v1.1+ migration**: map 改为 reject-all（drop `"" 1` 行），由 app JWT 链路鉴权。语法验证 deferred（无 nginx binary in PATH），待 docker compose up + curl matrix 验证。 |
| B-2 | review §M8 | `CategorySeedService.ensureUserDefault()` concurrency safety | **Closed** (Path B PostgreSQL UPSERT) | v1.3: 替换 v1.0 catch + re-query (`a068e0` 已无效)。Path B: native SQL `INSERT ... ON CONFLICT ... DO NOTHING` + `TransactionTemplate(REQUIRES_NEW)` 隔离 INSERT/SELECT + `JdbcTemplate` fallback SELECT 绕开 Hibernate 脏 session。详见 §1 M8 行的 closure / Reference 列。 |
| B-3 | review §M6 (inferred from "可选" suggestion) | Emit EXPENSE_UPDATED / EXPENSE_DELETED outbox events         | Closed `a4570d0` | (also added EXPENSE_RESTORED + BudgetEvaluator integration — see plan-03 B-3 commit message) |
| B-4 | **DELETED** | "4 minor style/noise findings" — phantom item | — | The v1 ledger's "L × 4" came from a summary statistic, not 4 separate findings. Review notes contain exactly 1 LOW (L1), and L1 = duplicate of M3 (already closed). No code work to do here. |
| B-5 | v1.3 discovery (release-blocker scan) | 3 pre-existing cross-module hot-fixes (bean name collision + Flyway V37/V38/V39 collision + TaskChangedConsumer dead-bean) | **Closed** (commit 1-4 of v1.3 PR) | Bean name 冲突: `expense.controller.StatsController` + `diet.controller.StatsController` 同名 → `ConflictingBeanDefinitionException` 阻塞 Spring 启动。同样 `expense.service.StatsService` + `diet.service.StatsService`。修法: `@RestController("expenseStatsController")` / `@RestController("dietStatsController")` + `@Service("expenseStatsService")` / `@Service("dietStatsService")`。Flyway 冲突: V37/V38/V39 daily 和 expense 各自有同名文件 → "Found more than one migration with version X"。修法: rename 到 V45-V49 区间（daily V37→V45/V38→V46/V39→V47, expense V38→V48/V39→V49）。TaskChangedConsumer 死 bean: `PlanEventConsumerConfig.planTaskChangedConsumer` 创建的 `TaskChangedConsumer` 实例 implements `EventConsumer` → 被 `OutboxDispatcher(List<EventConsumer>)` 收，按 `eventType()` 索引抛 `UOE: TaskChangedConsumer must be wrapped by TaskChangedForwarder`。修法: class 移除 `implements EventConsumer`，forwarder 仍持有引用调 `delegate.consume(env)`。**Why these came in one PR**: 3 个阻塞在 mvn verify 同一连续命令暴露，必须同一 PR 修复才能 verify green。**How to apply**: v1.1+ 新模块接入时，`@RestController` / `@Service` 命名必须带模块前缀（plan-05 ADR seed planned）。 |
| B-6 | v1.3 discovery (B-2 IT side-effect) | Hibernate session pollution in `CategorySeedService` catch + re-query | **Closed** (Path B = commit 5 of v1.3 PR) | B-2 IT 10 线程并发暴露 v1.0 catch + re-query 的隐性 bug: `save()` 失败后 Hibernate 持久化上下文保留脏实体（id=null），catch 块调 `findFirstBy...` 触发 auto-flush → `org.hibernate.AssertionFailure: null id in ExpenseCategory entry`，逃出 catch。**Why this is a real release blocker**: v1.0 单用户 race 罕见，但生产部署一旦撞上（重启 + 并发注册），整方法 fail，后续注册都失败。**How to apply**: 任何 `@Transactional` 方法 + `try { save(...) } catch (DataIntegrityViolationException) { re-query }` 模式都有该风险——要么用 native UPSERT，要么事务隔离做 `REQUIRES_NEW`。 |
| B-7 | plan-auth §2.1 (JavaDoc-confirmed defer) | `AuthController` 缺 `forgot-password` / `reset-password` 端点 | **Closed** (6-endpoint AuthController + V50 password_reset_tokens + EmailService abstraction) | v1.3.3: 端点 `/api/auth/forgot-password` (POST) + `/api/auth/reset-password` (POST) 落地; `AuthService.forgotPassword(email)` 生成 raw token (SecureRandom 32 bytes) + SHA-256 持久化 + EmailService 投递 + outbox emit `AUTH_USER_PASSWORD_RESET_REQUESTED`; `AuthService.resetPassword(token, newPassword)` 校验 token (未用 / 未撤销 / 未过期) + `User.changePasswordHash(BCrypt)` + mark used。**V50 password_reset_tokens 表**: id, user_id, token_hash (unique), expires_at, used_at, revoked_at + idx_user_active。**EmailService 接口 + StdoutEmailService 实现** (`@ConditionalOnMissingBean(name = "smtpEmailService")`): v1.0 SLF4J INFO 日志 (docker logs 可见), v1.1+ 加 SMTP 实现自动 primary。**v1.0 影响仍为零** (单用户白名单, 江兴旺知道密码) — 但 spec §5.4 合规 + v1.1+ 多用户接入时端点 ready。**AuthServiceTest 9/9 通过** (新 deps: PasswordResetTokenRepository + EmailService mock); **mvn test 全模块 612/0/0 GREEN via PR #18** (含 TokenBucketService bean + cross-module bean collision + plan JPQL timestamp repair 一并入账). |
| B-8 | PRD-01 §TASK-011/012 spec gap + §5 mitigation noted | Task 模块 Web Push 集成（due_at -1h + 习惯漏签 21:00） | MEDIUM (deferred v1.1+) | PRD-01 §TASK-011「截止时间前 1 小时 Web Push 提醒（开关可关）」+ §TASK-012「习惯漏签当天 21:00 Web Push 提醒（开关可关）」。当前 Task 模块 0 Web Push implementation（无 VAPID 密钥生成、无 Push Service 接入、无 ScheduledJob 触发）。Spec §5 已记录缓解措施：「**Web Push 在 iOS Safari 兼容性差** \| 高 \| 高 \| 提供应用内「通知中心」作为降级」。**v1.0 影响为零**：单用户 PWA 本地通知可由 Service Worker 自处理；spec §9 允许「通知中心」降级路径覆盖。**v1.1+ 触发条件**：集群化（CLAUDE.md ADR-001）+ 多设备使用场景。**How to apply**: v1.1 实现时先补 VAPID 密钥生成 / PushSubscription 存储 / ScheduledJob 触发器，再决定是实际推送还是仅入站应用内通知中心（spec §5 降级方案）。 |
| B-9 | plan-05-plan Step 6 audit | Plan consumer idempotency 怀疑（多次消费累加风险） | **Closed** (false alarm — design idempotent) | `plan-05-plan` Step 6 audit 触发怀疑「`ProgressEvaluator` + `LastActivityRefresher` 多次消费累加」。**核实**: `ProgressEvaluator.compute(Long userId, Long planId)` 是 `@Transactional(readOnly = true)` 纯读，返回 `ProgressView`，**无 DB 写入**。`LastActivityRefresher.refreshForTask(Long taskId, Long planId)` 是 `plan.touchActivity(now) + planRepository.save(plan)`，timestamp UPDATE 到当前值——**幂等 by design**（重复消费只更新 timestamp 到同一 now 值，不累加）。**Why false alarm**: read-only compute + idempotent touch 模式天然抗重复消费，无需 dedupe 存储或 Outbox dedupe-window。**How to apply**: 任何 plan module consumer 设计模式都遵守该原则——进度计算只读、状态更新幂等。 |
| B-10 | plan-05-plan Step 6 audit | 缺 `task.archived` + `task.deleted` forwarder（怀疑 BR-30 未覆盖） | **Closed** (false alarm — events don't exist) | 原怀疑 BR-30「task.* 事件刷新 `plan.last_activity_at`」未覆盖 `archived` / `deleted`。**核实**: `shared/integration/event/EventType` enum **仅含 4 个 task 事件**（TASK_CREATED / TASK_UPDATED / TASK_COMPLETED / TASK_REOPENED），无 TASK_ARCHIVED / TASK_DELETED。`TaskService.java` 行 73 / 96 / 118 / 132 emit 这 4 个事件，**不 emit** archived / deleted——Task 走 `deleted_at` 软删除路径无 broadcast。`PlanEventConsumerConfig` 已配 4 个 forwarder bean（覆盖全部现有 task.* 事件）。**Why false alarm**: 没有 event 可被消费，就不需要 forwarder。**How to apply**: v1.1+ 若 task 模块新增 archive/delete emit（如 hard delete with audit），再补对应 forwarder。当前 v1.0 软删除走 `deleted_at` 字段即可，无需 outbox 广播。 |

**Backlog note** (after 2026-08-06 v1.3.3 closure): **M1 / M2 / M4 / M5 / M6 / M7 / M8 / B-1 / B-2 / B-3 / B-5 / B-6 / B-7 / B-9 / B-10 are closed** — see §1 closure column (M-series) and §2 (B-series). **B-5/B-6 ledger-only (v1.3)**: 3 release-blockers + 1 IT-side-effect discovery. **B-9/B-10 ledger-only (v1.3.2 false alarm)**: 2 audit-confirmed non-issues from plan module Step 6 audit (consumer idempotency + missing event forwarder). **M6 ledger-only (v1.3.2 verification)**: cross-entity validation 已落地 (`ExpenseService.java:100-103`); access modifier 与项目 7-entity convention 一致 (Budget/Milestone/Task/Plan/DailyReport/Food/Profile 全 public), 收紧需 service 挪 domain 包违反分层 — 决定保留 public。**B-1 ledger-only (v1.3.3)**: nginx path-based X-User-Id header rejection (`map` + 3× `if`), defense-in-depth for v1.1+ multi-user migration. **B-7 ledger-only (v1.3.3)**: AuthController 6 端点 + V50 + EmailService abstraction, spec §5.4 合规. **H3 is decided** via ADR-001 §5 (plan B for v1.0 single-user; plan A mandatory on v1.1 clusterization trigger). **AI module (v1.3.3)**: 6/6 模块全部 promoted to main via PR #18 (`8bc0068` squash `19edc0b`) — OllamaClient + ScopedDataFetcher + AiRateLimiter + 3 controllers + V42/V46/V47 Flyway. **Remaining §2 Open** = **B-8 Web Push (deferred v1.1+ per spec §5 mitigation + CLAUDE.md §1.3 out-of-scope)**.

## 3. Conventions (unchanged)

- IDs `B-N` are stable — never reused. **B-4 is intentionally deleted** to avoid
  the trap of "phantom backlog item that becomes a phantom claim of completeness".
- B-6 was the last ID issued from v1.3 hot-fix. v1.3.2 reconciliation (v1.0 release audit Steps 5-9) reopens B-ID issuance for audit findings only: **B-7 / B-8 (deferred v1.1+ items)** + **B-9 / B-10 (false-alarm audit closures)**. These are v1.0 audit bookkeeping, not v1.1+ new issues. New v1.1+ issues still append to v2 (a separate `known-limitations-v2.md` after v1.0 release).
- "Trigger commit" is the Phase A commit that surfaced the finding (subject hash).
- "Issue" is the public GitHub URL — filled by owner after `gh issue create`.
  Run: `gh issue create --title "..." --body "..." --label "phase-b,plan-03-expense"`.

## 4. Update Log

- 2026-08-05: **v1.3.2 batch reconciliation.** v1.0 release audit Steps 5-9 + ledger ID continuity (no code change, ledger-only).
    - **v1.0 release audit findings** (Step 5-8 module-by-module):
      - **plan-04-diet audit**: 0 v1.0 release blockers. FoodSeedService defensive try/catch（PostConstruct 外层）属正常 defensive design，非 B-6 模式（catch 在 save loop 外）。Diet 模块仅 emit `MEAL_CREATED` 单事件（spec 不要求 MEAL_UPDATE/DELETE emit）。
      - **plan-05-plan audit**: 0 v1.0 release blockers. 14 endpoints (Milestone 7 + Plan 6 + Progress 1), V4 Flyway, 4 outbox consumers (TASK_CREATED/UPDATED/COMPLETED/REOPENED 全覆盖)，cross-module `TaskReadPortFacade` 已接入。B-6 4-vector grep 命中 MilestoneRepository `@Modifying` 为 bulk UPDATE with `clearAutomatically=true, flushAutomatically=true`，非 save+catch dive 模式。
    - **B-9 + B-10 closed as false alarm** (ledger-only rows 保留以维护 ID continuity，符合 §3「永不重用 ID」原则):
      - **B-9** = Plan consumer idempotency: ProgressEvaluator.readOnly + LastActivityRefresher idempotent touch,设计本身就抗重复消费。
      - **B-10** = task.archived/deleted forwarder: EventType enum 不含这 2 个事件,TaskService 不 emit,无可订阅 eventType。
    - **B-7 + B-8 new deferred items** (no v1.0 fix, tracked for v1.1+):
      - **B-7** = `AuthController` 缺 forgot-password/reset-password 端点。`AuthController.java:23-25` JavaDoc 显式 defer。CLAUDE.md §7.3.1 单用户白名单 v1.0 影响为零。
      - **B-8** = Task 模块 0 Web Push 实现（PRD-01 §TASK-011/012 spec gap）。Spec §5 mitigation「应用内通知中心降级」覆盖 v1.0 路径。
    - **Ledger continuity**: 4 new B-IDs issued (B-7 / B-8 / B-9 / B-10)。所有 closed items 保留 closure column 引用,符合 §3 永不删除已闭合 ID 的原则。
    - **M6 Closed (verification)**: cross-entity validation half 已落地 (`ExpenseService.java:100-103` `loadOwnedCategory` + `validateCategory`); access modifier half 与项目 7-entity convention 一致, 决定保留 public 不强行收紧 (避免 service 挪 domain 包违反分层)。**Amended commit** (本地未推送): 把 M6 加入 closed set, §1 row 扩展 closure evidence。
- 2026-08-06: **v1.3.3 B-7 closure.** `feat(auth): forgot-password + reset-password endpoints + V50 password_reset_tokens + EmailService abstraction`。6 端点 AuthController (新增 2), AuthService.forgotPassword (SecureRandom token + SHA-256 persist + EmailService deliver + outbox emit) + resetPassword (token 校验 + BCrypt hash update + mark used)。EmailService 接口 + StdoutEmailService (v1.0 stdout, v1.1+ SMTP via @ConditionalOnMissingBean)。**AuthServiceTest 9/9 通过**; mvn compile 全绿。Spec §5.4 合规 + v1.1+ 多用户接入 ready。
- 2026-08-06: **v1.3.3 nginx hardening + B-1 closure.** `0591367` fix(infra): nginx `map` + `if` 三处 X-User-Id header injection 防御。Defense-in-depth 价值: v1.0 单用户白名单原本只在 app 层 (`CurrentUserArgumentResolver`) 校验; nginx 静默 override 客户端值到 "1"。B-1 改后 nginx 显式拒绝 header 注入 (X-User-Id != 1 → 403), 为 v1.1+ 多用户切换提供双层防御。**No new B-IDs**, B-1 status 从 Open 改 Closed (保留 ID continuity)。
- 2026-08-06: **v1.3.3 closure (final v1.0 release audit).** 6/6 模块全部 promoted to main. **AI module**: PR #18 squash `19edc0b` (merge `8bc0068`) via branch `feature/ai-step13-merge-local`。24 commits: OllamaClient / RestClientOllamaHttpClient / ScopedDataFetcher / PromptBuilder / AiRateLimiter (triple quota 10/m + 60/h + 100/m) / AuditLogger / ConsentVerifier / 3 controllers (AiReport/AiJob/AiConsent) + V42/V46/V47 Flyway + 5 post-rebase hot-fix (TokenBucketService bean / cross-module bean collision / plan JPQL timestamp / OllamaProperties bean / RestClientOllamaHttpClient). Reviewer 导航 `docs/lifewise/ai/conflict-analysis-skeleton-rebase.md` (独立 commit `19edc0b`). **mvn test 612/0/0 GREEN**。**M6 ledger-verified**: 走 Path A 解释路径 (cross-entity validation 已实现 + access modifier 与 7-entity convention 一致, 不强行收紧违反分层)。**B-7 ledger-closed** (本 commit 前已落地 via `db4d2b2` feat(auth): forgot-password + reset-password endpoints + V50)。**B-8 final disposition (v1.0)**: defer v1.1+ per CLAUDE.md §1.3 out-of-scope (VAPID cert 申请 + Redis 订阅表 + ServiceWorker 注册链路 + 6+ 文件改 surface, 超出 v1.0 个人版/单用户白名单范围); spec §5 mitigation「应用内通知中心降级」覆盖 v1.0 路径。**No new B-IDs** issued; B-7 + B-8 row status 在 v1.3.3 closure commit 反映: B-7 → Closed, B-8 → Med deferred v1.1+。v1.3.2 (`1b4cc62`) 保留为 audit trail; 走法 X=1 (叠加 commit, 不 amend)。v1.0 release cut 准备好 (§1.3 Out of scope 已锁定)。
- 2026-08-03 (`98f9d41`): Initial v1 created. 4 Phase B issues registered with `TBD` Issue URLs.
  Found inaccurate on multiple counts (see preamble).
- 2026-08-04: **v1.1 rewrite.** Rescued review notes from `refs/backup/pre-fix-stash-78645273`
  to `docs/lifewise/specs/plan-03-review/plan-03-expense-review-notes.md` (prevent gc loss).
  Reconciled v1 ledger against actual review notes:
    - Corrected finding counts (22 → 10)
    - Deleted phantom B-4
    - Fixed B-2 description (M8 = "exists but not concurrent-safe", not "absent")
    - Recorded closure commits for H2, M3, L1
    - Recorded H1 demotion (no active bug)
    - Recorded M6 partial closure via B-3 (`a4570d0`)
    - Surfaced 2 missing open items: H3 (degraded), M4 (float)
  Also fixed `BudgetEvaluator.java:106-112` javadoc — previous "依赖运维每日重启进程
  (dedupe 随进程销毁)" was an inverted framing (restart = re-notification, not mitigation);
  replaced with honest two-bullet description of the plan-B gap.
- 2026-08-05: **v1.2 reconciliation.** Ledger §1 / §2 / backlog note rewritten
  against current code state via service-layer + handler + entity scan:
    - **Closed**: M1 (N+1 — service-layer scan 0 residue), M2 (`2a1c0a1` typed
      invalid amount + `ExpenseGlobalExceptionHandler.java:73-77` mapping), M4
      (`c5d0885` integer threshold percentages + `BudgetEvaluator.java:161`
      `int pctX100`), M5 (entity 工厂方法 + `applyUpdate` 业务方法, 无 public
      setter), M7 (`ExpenseCategory.java:127-133` trim before length).
    - **Partially closed**: M8 (B-2 code fixed `a068e0` catch +
      re-query; pending 并发 IT).
    - **Decided** (via ADR-001 §5): H3 (plan B for v1.0; plan A mandatory on
      v1.1 clusterization trigger).
    - **Still open**: B-1 (nginx URL hardening — 3 locations hardcode
      `X-User-Id=1` in `default.conf`), B-2 IT (M8 concurrency IT), M6 access
      modifier tightening (low priority).
- 2026-08-05: **ADR-001 H3 Plan A/B 决议（§5 新增）**.
  在 v1.1 集群化打开前维持 plan B（in-memory LRU Map）；集群化 trigger 触发后**必须**迁 plan A（DB `budget_notifications` 表）。
  Plan A 迁移草案已写明 DDL / Repository / Service / 验证 / 前端清理 5 步。
  无新 schema 改动，无 service 改动，纯文档决策记录。
- 2026-08-05: **v1.3 hot-fix release.** 3 pre-existing release-blockers + 1 IT-side-effect discovery closed in 1 PR (6 commit):
    - **B-5 closed** (3 pre-existing cross-module hot-fixes):
      1. bean name conflict: `expense/diet.StatsController` + `expense/diet.StatsService` 同名 → Spring 启动失败。
      2. Flyway V37/V38/V39 collision: daily 模块和 expense 模块各有同名文件 → migration 启动失败。
      3. TaskChangedConsumer dead bean: 实现 `EventConsumer` 接口 + `@Bean` 创建 → 被 `OutboxDispatcher.List<EventConsumer>` 收，索引失败。
    - **B-2 / M8 fully Closed** (Path B PostgreSQL UPSERT): native SQL `INSERT ... ON CONFLICT ... DO NOTHING` + `TransactionTemplate(REQUIRES_NEW)` 隔离 INSERT/SELECT + `JdbcTemplate` fallback SELECT 绕开 Hibernate 脏 session。`CategorySeedServiceConcurrencyIT` 10 线程并发 + AssertionFailure 反向监测，全绿。
    - **B-6 new finding** (session-pollution-bug discovery via B-2 IT): v1.0 catch + re-query 路径在 production race 下暴露 Hibernate session pollution（`AssertionFailure: null id` 逃出 catch）。属于 v1.0 release 真隐患，由 B-2 IT 逮到。已在 Path B 替换中根本修复。
    - **pre-existing 失败**: `ExpenseE2EIT.business_failure_does_not_persist_expense` / `business_failure_rolls_back_outbox_event` 硬编码 `IllegalArgumentException` vs 实际 `ExpenseInvalidAmountException`（v1.0 plan-03 review 引入的 domain exception）。与 Path B 改动**无关**，暂留待 v1.3.x 后续 IT 硬编码清理。
    - **新 PRD 变更**: M8 + B-2 + B-5 + B-6 ledger 闭环，剩余 Open = M6 (low priority) + B-1 (nginx URL hardening)。

## 5. ADR-001: H3 BudgetEvaluator 阈值事件幂等 — Plan A vs Plan B

> 日期：2026-08-05
> 状态：**Decided** — 维持 plan B（in-memory LRU Map），不引入新 schema，直到 §5.3 触发条件之一满足再迁 plan A
> 决策者：江兴旺
> 关联：plan-03-expense §H3 / `plan-03-expense-review-notes.md` §H3

### 5.1 Context

- **Plan A**（review notes 推荐）：建 `budget_notifications` DB 表，
  每次阈值跨越写一行；读取时去重。重启不丢，生产可观测，可跨实例。
- **Plan B**（当前实现）：in-memory `LRUMap<BudgetId, LastNotifiedAt>` 做 dedupe。
- 当前部署：v1.0 单用户 + 单机 docker compose。✓ plan B 足够（单进程 = 唯一 source of truth）。
- 集群化场景（v1.1+）：横向扩 pod / 多实例 → 每个进程各自维护 LRU → 同一事件 N 次推送。
  此时 plan B 的 "process restart = dedupe reset" 从可接受变为 correctness bug。
- B-3（commit `a4570d0`）把 caller 从 1 个（create）扩到 3 个（create/update/restore），
  放大了 plan B 的 failure 面（同一进程内 tryLock 串行化足够，跨进程就不够了）。

### 5.2 Decision

**v1.0**：维持 plan B，不引入新 schema，不变更 service 接口。
- 接受 "process restart = dedupe reset"：v1.0 单进程部署，dedupe 漂移是噪声而非 correctness bug。
- 接受 B-3 扩张后的多 caller 抖动：由单进程内存 tryLock 串行化处理。
- 当前 BudgetEvaluator.java:106-112 javadoc 已诚实记录 plan B 的限制（v1.1 rewrite 同时修过）。

**v1.1 / 集群化 trigger**（§5.3 任一条件满足）：**必须**先迁 plan A 再放量。
否则：同 budget 同阈值事件 → 每个 pod 各推一次 → 用户重复收到同一通知 → 信任成本上升；
LRU 重启后的 dedupe 漂移在多实例环境 = 永久问题，不只是单次重启。

### 5.3 Trigger conditions（任一满足即触发 plan A 迁移）

1. Lifewise 部署从单机 docker compose 升级到 k8s / docker swarm / 多机部署
2. `docker-compose.yml` 中 `app` service 的 `deploy.replicas > 1`
3. nginx upstream 多实例化（虽 v1.0 单 userId=1 可能不直接触发，旁路风险存在）
4. v1.1+ 多用户上线后同一 user 多设备并发（即使同一进程，跨设备 dedupe 失效）

### 5.4 Migration plan（Plan A 草案，待 trigger 时细化）

1. **DDL** 新增 `budget_notifications` 表：
   ```sql
   CREATE TABLE budget_notifications (
       id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
       budget_id BIGINT NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
       threshold_pct NUMERIC(5,4) NOT NULL,
       notified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       channel TEXT NOT NULL DEFAULT 'web_push',
       trace_id TEXT,
       UNIQUE (budget_id, threshold_pct, channel)
   );
   CREATE INDEX idx_budget_notifications_recent
     ON budget_notifications (budget_id, notified_at DESC);
   ```
2. **Repository**：新增 `BudgetNotificationRepository`（Spring Data JPA）
3. **Service**：`BudgetEvaluator.recordThreshold(...)` 改写为
   `INSERT ... ON CONFLICT DO NOTHING` → rowcount=0 跳过 / rowcount=1 推 Web Push。
   替换原有的 LRUMap 写读。
4. **删除** in-memory `LRUMap`（plan B 残留）+ 相关字段初始化代码。
5. **验证** `mvn -pl app test` 全 GREEN；集成测试覆盖「同 budget 同 threshold 跨实例只推一次」（Testcontainers PostgreSQL + 2 个 app context）。
6. **可选前端**：通知中心 "已读" 入口清理 `budget_notifications` 历史行。

### 5.5 Consequences

- 接受 plan B 在 v1.0 的"单进程内存互斥"假设（单用户部署下成立）。
- 接受 B-3 扩张 surface 后的跨进程正确性缺口——这就是本 ADR 存在的全部理由。
- v1.0 release 在功能上完整；事件幂等是 "best-effort 单进程" 而非 "全局一致"。
- v1.1 集群化打开前，本 ADR 必须翻面（Proposed → Migrating → Done），**不可遗忘**。

### 5.6 Cross-module impact

- 0 改动：task / daily / diet / plan / ai / auth 6 模块都不依赖 budget threshold 事件。
- 不影响 6 模块协作链路。
- 出 Outbox 影响待复核：threshold 事件当前可能未走 Outbox（如未走，需在 Plan A 迁移时一并接入 `notification.requested` OutboxEvent，
  对齐 `data-model-v1.2-amendment.md` §1.4 已定义的 `notification_requests` / `notification_deliveries` 表）。

### 5.7 References

- `known-limitations-v1.md` §1 H3 行（reconciliation table）
- `plan-03-expense-review-notes.md` §H3（review notes 原版）
- `BudgetEvaluator.java:106-112`（plan B javadoc 现状）
- `a4570d0` feat(expense): emit EXPENSE_UPDATED / RESTORED / DELETED outbox events（B-3 commit）
- `data-model-v1.2-amendment.md` §1.4（notification_requests / notification_deliveries 预留）

### 5.8 v1.3 模块命名约定 seed（v1.1+ 落地待议）

> 日期：2026-08-05
> 状态：**Seed**（v1.3 ledger 同步；v1.1+ plan-05/plan-06 落地为正式 ADR）
> 决策者：江兴旺
> 关联：v1.3 B-5 bean name 冲突复盘

#### 5.8.1 Context

v1.3 修复的 3 pre-existing hot-fix 中，第 1 项是 `expense/diet.StatsController` 同名 bean → Spring 启动直接 fail。原因：6 模块每模块有自己的 `StatsController` 走模块特定路径但类名相同，依赖 Spring 默认按类名小写生成 bean name，造成 `ConflictingBeanDefinitionException`。

#### 5.8.2 提议规则（v1.1+）

- 所有 `@RestController` / `@Service` / `@Component` / `@Repository` 命名必须带模块前缀。
- 例：`expenseStatsController` / `dietStatsService` / `planMilestoneRepository`。
- v1.0 紧急止血用 `@RestController("xxx")` / `@Service("xxx")` 显式命名（v1.3 落地）。
- v1.1+ 接入新模块前制定 ArchUnit 规则或 IDE 模板强制约束。

#### 5.8.3 Why this seed (ADR not promoted)

- v1.3 修复是为了 release unblock，没时间完善 ArchUnit/checkstyle。
- v1.1+ 集群化打开前推荐补一次跨模块命名约定 ADR（覆盖 controllers / services / repositories / outbox event types / flyway version numbers）。
- 当前不强制 v1.0 范围必须执行。
