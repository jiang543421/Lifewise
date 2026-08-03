# plan-03-expense KNOWN_LIMITATIONS (v1)

> Plan-03 code review identified 22 findings (3 CRITICAL / 8 HIGH / 7 MEDIUM / 4 LOW).
> Phase A (commits `#1` → `#8a-1b`) closed 18 of them.
> The remaining 4 are deferred to **Phase B** and tracked below.

## Phase B Issues

| ID  | Severity | Title                                                 | Trigger commit | Issue |
|-----|----------|-------------------------------------------------------|----------------|-------|
| B-1 | C3       | nginx URL hardening (ALLOWED_USER_IDS env + body format validation) | `a6f7b22` (#3) | TBD   |
| B-2 | H3       | CategorySeedService (seed user-default "其他" absent) | `0ff809a` (#1) | TBD   |
| B-3 | M        | EXPENSE_UPDATED / EXPENSE_DELETED events not emitted  | `8554f4c` (#2) | TBD   |
| B-4 | L × 4    | Static noise (4 minor style/noise findings)           | `b626d66` (#5) | TBD   |

## Per-Issue Notes

### B-1: nginx URL hardening (C3, cross-module)

- **Trigger**: commit `a6f7b22` added format validation to `CurrentUserArgumentResolver`.
  Phase A only validated request-side format. The HTTP entry (nginx `ALLOWED_USER_IDS`
  env wiring + request body format) was scoped as cross-module C3 follow-up.
- **Phase B scope**: nginx config `nginx/conf/lifewise.conf` env wiring + X-User-Id
  resolver dual-layer defense (memory of project: `v1.0 X-User-Id 白名单方案`).
- **Why deferred**: cross-module (auth + nginx + expense) requires coordinated change
  touching deployment config not appropriate for plan-03-expense Phase A.

### B-2: CategorySeedService (H3)

- **Trigger**: commit `0ff809a` added CRUD for user categories.
  BR-24 requires every user to have exactly one `is_user_default = TRUE` category
  named "其他" (catch-all). Service to seed this on user registration is absent.
- **Verification**: `grep -r CategorySeedService src/main` → no matches.
- **Phase B scope**: implement `CategorySeedService.createUserDefault(userId)` and
  wire to user-registration flow (likely in `auth` module).
- **Why deferred**: requires cross-module event listener (auth → expense), which
  was out of scope for plan-03-expense.

### B-3: EXPENSE_UPDATED / EXPENSE_DELETED events (M)

- **Trigger**: commit `8554f4c` wired BudgetEvaluator to `EXPENSE_CREATED` outbox
  events. Plan-03 also defines `EXPENSE_UPDATED` / `EXPENSE_DELETED` event types
  for downstream consumers (ai module aggregation), but they are not yet emitted
  from `ExpenseService.update()` / `softDelete()`.
- **Verification**: `grep -r "EXPENSE_UPDATED\|EXPENSE_DELETED" src/main` → no
  matches (events don't even exist in `EventType` enum).
- **Phase B scope**: emit outbox events in `ExpenseService.update/softDelete/restore`
  methods, mirroring `create()` pattern.
- **Why deferred**: no consumer pressure yet (ai aggregation planned for v1.1).

### B-4: Static noise (L × 4)

- **Trigger**: commit `b626d66` (subject already tagged `H6+LOW`) introduced LOW
  findings during review pass.
- **Phase B scope**: 4 minor style/noise items (exact list in plan-03 review
  §findings table). Each is < 5 lines of code change.
- **Why deferred**: LOW severity does not block functional correctness; bundled
  cleanup is more efficient than 4 atomic commits.

## Conventions

- IDs `B-N` are stable — never reused.
- "Trigger commit" is the Phase A commit that surfaced the finding (subject hash).
- "Issue" is the public GitHub URL — filled by owner after `gh issue create`.
  Run: `gh issue create --title "..." --body "..." --label "phase-b,plan-03-expense"`.
- v1 doc closes when all 4 IDs reach `Issue` ≠ `TBD`. Future phases append
  v2 / v3 instead of mutating v1 (changelog below).

## Update Log

- 2026-08-03 (commit `#8a-2`): Initial v1 created. 4 Phase B issues registered
  with `TBD` Issue URLs pending `gh issue create` authorization.