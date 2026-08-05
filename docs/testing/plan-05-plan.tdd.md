# plan-05-plan TDD Evidence Report

> Source plan: `docs/lifewise/planning/plan-05-plan.md`
> User journeys: extracted from `docs/lifewise/specs/PRD/05-plan-management.md` + plan
> Branch: `backend-plan` · Date: 2026-08-04

## 1. Scope

14 endpoints (6 plan CRUD + 7 milestone + 1 progress) + 4 `task.*` event subscriptions
(`task.completed`, `task.reopened`, `task.created`, `task.updated`).

## 2. Test Specification

| # | What is guaranteed | Test file | Result |
|---|---|---|---|
| 1 | PlanService.create enforces BR-15 (end >= start) | `service/PlanServiceTest.java` | PASS |
| 2 | PlanService.create trims title and rejects blank | `service/PlanServiceTest.java` | PASS |
| 3 | PlanService.update applies new title/type | `service/PlanServiceTest.java` | PASS |
| 4 | PlanService.abandon sets status=CANCELLED + emits event | `service/PlanServiceTest.java` | PASS |
| 5 | PlanService.softDelete cascades milestones | `service/PlanServiceTest.java` | PASS |
| 6 | PlanService.list excludes CANCELLED by default | `service/PlanServiceTest.java` | PASS |
| 7 | PlanService.list includes CANCELLED when flag=true | `service/PlanServiceTest.java` | PASS |
| 8 | MilestoneService.create enforces sortOrder validation | `service/MilestoneServiceTest.java` | PASS |
| 9 | MilestoneService.complete throws MilestoneAlreadyDone | `service/MilestoneServiceTest.java` | PASS |
| 10 | MilestoneService.reopen only works from DONE | `service/MilestoneServiceTest.java` | PASS |
| 11 | MilestoneService.update rejects when DONE (BR-14) | `service/MilestoneServiceTest.java` | PASS |
| 12 | MilestoneService.list throws PlanNotFound for foreign plan | `service/MilestoneServiceTest.java` | PASS |
| 13 | ProgressEvaluator excludes CANCELLED from total | `service/ProgressEvaluatorTest.java` | PASS |
| 14 | ProgressEvaluator returns ratio=0 when no milestones | `service/ProgressEvaluatorTest.java` | PASS |
| 15 | LastActivityRefresher skips when planId null | `service/LastActivityRefresherTest.java` | PASS |
| 16 | LastActivityRefresher updates last_activity_at on plan | `service/LastActivityRefresherTest.java` | PASS |
| 17 | MissedMilestoneJob marks overdue PENDING as MISSED | `service/MissedMilestoneJobTest.java` | PASS |
| 18 | MissedMilestoneJob skips MISSED/CANCELLED milestones | `service/MissedMilestoneJobTest.java` | PASS |
| 19 | PlanStaleNotifyJob fires at 14-day threshold | `service/PlanStaleNotifyJobTest.java` | PASS |
| 20 | MilestoneTaskLinkService dedupes + validates cross-module | `service/MilestoneTaskLinkServiceTest.java` | PASS |
| 21 | MilestoneTaskLinkService throws CrossModuleTaskNotFound | `service/MilestoneTaskLinkServiceTest.java` | PASS |
| 22 | MilestoneTaskLinkService skips already linked | `service/MilestoneTaskLinkServiceTest.java` | PASS |
| 23 | TaskReadPortFacade.findById pins userId=1 | `service/TaskReadPortFacadeTest.java` | PASS |
| 24 | TaskReadPortFacade.findByPlanId projects to ID list | `service/TaskReadPortFacadeTest.java` | PASS |
| 25 | PlanReadPortAdapter.findMilestonesByTaskId | `port/PlanReadPortAdapterTest.java` | PASS |
| 26 | PlanReadPortAdapter.computeProgress | `port/PlanReadPortAdapterTest.java` | PASS |
| 27 | TaskCompletedConsumer triggers progress + refresh | `event/TaskCompletedConsumerTest.java` | PASS |
| 28 | TaskReopenedConsumer triggers progress + refresh | `event/TaskReopenedConsumerTest.java` | PASS |
| 29 | TaskChangedConsumer.refreshes activity for task.created | `event/TaskChangedConsumerTest.java` | PASS |
| 30 | TaskChangedConsumer.refreshes activity for task.updated | `event/TaskChangedConsumerTest.java` | PASS |
| 31 | PlanController POST/GET/PUT/DELETE/abandon/list | `controller/PlanControllerWebMvcTest.java` | PASS |
| 32 | PlanController rejects X-User-Id != 1 with 401 | `controller/PlanControllerWebMvcTest.java` | PASS |
| 33 | MilestoneController POST/GET/PUT/complete/reopen/DELETE | `controller/MilestoneControllerWebMvcTest.java` | PASS |
| 34 | ProgressController GET returns snake_case JSON | `controller/ProgressControllerWebMvcTest.java` | PASS |
| 35 | ProgressController rejects X-User-Id != 1 with 401 | `controller/ProgressControllerWebMvcTest.java` | PASS |

## 3. Validation Commands & Output

```bash
mvn -q test -Dtest='com.lifewise.plan.**'
# [INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

Coverage (JaCoCo, plan module aggregate):
- **Instructions: 80.2%** · Branches: 55.8% · Lines: **83.3%** (≥ 80% ✓)
- Untested 0% classes: `PlanEventConsumerConfig` (Spring @Configuration wiring),
  `MilestoneTaskLink.PK` (JPA composite key inner class), `NoopPlanNotifier`
  (intentional noop default impl).
- `PlanGlobalExceptionHandler` low coverage (20% line) — exception mappers only fire
  under integration scenarios, not unit tests.

## 4. Known Gaps

- `PlanGlobalExceptionHandler` exception → ErrorEnvelope mapping has no dedicated
  tests; integration scenarios at controller level cover the happy paths but not
  every exception → status code. Deferred to integration test pass.
- No `SpringBootTest` integration test for plan module — `@SpringBootTest`
  currently fails for all modules due to a pre-existing
  `ConflictingBeanDefinitionException` between `expense.controller.StatsController`
  and `diet.controller.StatsController`. Out of scope for plan-05.

## 5. Pre-existing Issue Encountered (Fix Required)

`app/src/main/java/com/lifewise/task/web/CurrentUserArgumentResolver.java` had a
corrupted class body from a bad merge (duplicate class definitions with mojibake
non-ASCII comment bytes). Without rewriting the file, no module compiles.

**Resolution**: Replaced with the v1.0 whitelist pattern documented in
`CLAUDE.md §7.3.1`. This unblocks the entire build for plan, task, and
downstream modules.

## 6. Merge Evidence

Branch: `backend-plan` (uncommitted as of this report).
- New plan module: `app/src/main/java/com/lifewise/plan/` + tests
- Modified: `shared/integration/dto/ErrorCode.java` (added plan error codes)
- Modified: `task/web/CurrentUserArgumentResolver.java` (merge artifact fix)

Per global CLAUDE.md, no auto-commit. Commit + push pending user approval.