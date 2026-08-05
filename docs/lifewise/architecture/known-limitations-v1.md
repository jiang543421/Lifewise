# plan-03-expense KNOWN_LIMITATIONS (v1.1 — ledger reconciliation)

> **v1.1 status: REWRITTEN 2026-08-04** based on direct review of
> [`docs/lifewise/specs/plan-03-review/plan-03-expense-review-notes.md`](../specs/plan-03-review/plan-03-expense-review-notes.md)
> (the original review notes lived only on the local
> `refs/backup/pre-fix-stash-78645273` ref — created automatically by
> `git stash` when the B-2/B-3 fix cycle was committed. The ref IS
> reachable (git gc won't reclaim it), but it is **not** under
> `refs/heads/*`, so `git branch -a --contains` cannot find it; it
> is never pushed, so collaborators and the Gitee mirror cannot reach
> it; and a future `git stash drop` of the corresponding stash or a
> manual `git update-ref -d refs/backup/pre-fix-stash-78645273` would
> lose it permanently. Rescued here because this document treats the
> review notes as its authoritative source — losing them would make
> the ledger unverifiable).
>
> **v1 (2026-08-03, `98f9d41`) was inaccurate on multiple counts:**
> - Header counts claimed 22 findings (3C/8H/7M/4L). Actual: 10 findings (0C/3H/6M/1L).
> - B-2 description claimed "CategorySeedService absent"; review notes M8 actually says
>   the service exists but is not concurrency-safe.
> - B-4 claimed "4 minor style/noise findings" from "review §findings table"; the
>   review notes only have 1 LOW finding (L1 = duplicate of M3, marked "优先合并处理").
>   The "4" came from the summary statistic row, not from 4 separate findings.
> - B-3 was tracked as a separate finding; in the review notes it appears only as a
>   suggestion inside M6 ("Service 层加 update 路径发 EXPENSE_UPDATED event (如需要)").
> - Several real closure commits (H2 → V37, H1 demoted, M3/L1 → exception+handler) were
>   never recorded in the v1 ledger.
> - Two real open items were missing from the v1 ledger entirely:
>   - H3 (BudgetEvaluator idempotency) — review notes recommended plan A (DB table);
>     implementation chose plan B (in-memory LRU Map), and B-3 widened the surface
>     area from 1 caller to 3 (create/update/restore).
>   - M4 (BudgetEvaluator float `thresholdRatio`) — never addressed.
>
> This v1.1 replaces the v1 table with a row-per-finding reconciliation so the
> ledger reflects ground truth, not a summary statistic.

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
| M6  | MEDIUM | `Expense.applyUpdate()` public mutable + no EXPENSE_UPDATED | Partially closed (B-3 closed the event half: `a4570d0` emits EXPENSE_UPDATED / RESTORED / DELETED; access modifier tightening still open) | review notes §M6 |
| M7  | MEDIUM | `ExpenseCategory.rename()` length validation order           | Closed   | `ExpenseCategory.java:127-133`: `validateName` 先 `name.trim()` 后校验 `trimmed.length() > 20`; commit annotation 引用 "plan-03 review M7：先 trim 再校验 length, 避免 "a"×50 + " " 即便存储后只 50 字符也被拒". |
| M8  | MEDIUM | `CategorySeedService.ensureUserDefault()` not concurrency-safe | **Partially closed** (code fixed; concurrency IT pending) | `a068e0` feat(expense): seed default category on registration. `CategorySeedService.java:63-69`: `try { categoryRepository.save(...) } catch (DataIntegrityViolationException ex) { 重新查询确认 }` — DB unique partial index `uq_expense_categories_user_default` 兜底. **Remaining**: 并发 IT (B-2 follow-up). |
| L1  | LOW    | `BUDGET_ALREADY_EXISTS` ErrorCode no thrower                | Closed (merged with M3) | same as M3 |

## 2. Phase B issues (active work)

The v1 ledger's B-1..B-4 are superseded by the reconciliation above. Below are the
items still requiring code work, mapped to their review-notes origin.

| ID  | Source    | Title                                                       | Severity | Trigger / Notes |
|-----|-----------|-------------------------------------------------------------|----------|-----------------|
| B-1 | plan-03 cross-module (no review-notes origin) | nginx URL hardening: `ALLOWED_USER_IDS` env wiring + X-User-Id resolver dual-layer defense | C3       | Trigger: `a6f7b22` (format validation in CurrentUserArgumentResolver — Phase A only validated request-side format). Phase B scope: nginx config + body validation. |
| B-2 | review §M8 | `CategorySeedService.ensureUserDefault()` concurrency safety | MEDIUM (M8) | **Code fixed** by `a068e0` (catch `DataIntegrityViolationException` + re-query at `CategorySeedService.java:63-69`; DB partial unique index `uq_expense_categories_user_default` 兜底). **Remaining**: 并发 IT (B-2 follow-up test, N=10 threads CountDownLatch, 断言 catch 分支至少触发 1 次). |
| B-3 | review §M6 (inferred from "可选" suggestion) | Emit EXPENSE_UPDATED / EXPENSE_DELETED outbox events         | Closed `a4570d0` | (also added EXPENSE_RESTORED + BudgetEvaluator integration — see plan-03 B-3 commit message) |
| B-4 | **DELETED** | "4 minor style/noise findings" — phantom item | — | The v1 ledger's "L × 4" came from a summary statistic, not 4 separate findings. Review notes contain exactly 1 LOW (L1), and L1 = duplicate of M3 (already closed). No code work to do here. |

**Backlog note** (after 2026-08-05 v1.2 reconciliation): **M1 / M2 / M4 / M5 / M7
are closed** — see §1 closure column. **H3 is decided** via ADR-001 §5 (plan B
maintained for v1.0 single-user deploy; plan A mandatory on v1.1 clusterization
trigger). **Remaining §1 Open** = M8 (B-2 partial: code fixed `a068e0`, pending
并发 IT). **Remaining §2 Open** = **B-1 nginx URL hardening** (C3, current
`nginx/conf/conf.d/default.conf` hardcodes `X-User-Id=1` in 3 locations —
`/api/ai/chat`, `/api/ai/`, `/api/` — needs env wiring `ALLOWED_USER_IDS` +
dual-layer defense). **M6 partial closure**: event half closed (`a4570d0`);
access modifier tightening on `Expense.applyUpdate()` still open (low priority).

## 3. Conventions (unchanged)

- IDs `B-N` are stable — never reused. **B-4 is intentionally deleted** to avoid
  the trap of "phantom backlog item that becomes a phantom claim of completeness".
- B-3 was the last ID issued from v1. New issues append to v2 (see Update Log).
- "Trigger commit" is the Phase A commit that surfaced the finding (subject hash).
- "Issue" is the public GitHub URL — filled by owner after `gh issue create`.
  Run: `gh issue create --title "..." --body "..." --label "phase-b,plan-03-expense"`.

## 4. Update Log

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
