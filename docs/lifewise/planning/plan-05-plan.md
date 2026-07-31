# plan-05-plan 实施方案

## 参考资料

- [`docs/lifewise/specs/PRD/05-plan-management.md`](../specs/PRD/05-plan-management.md) — 产品 PRD
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.3 plan 模块边界 + 引用 task 关系
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V4（plans / milestones / milestone_task_links）
- [`docs/lifewise/designs/05-plan-ui/05-plan-ui-design.md`](../designs/05-plan-ui/05-plan-ui-design.md) — UI 设计契约
- [`docs/lifewise/architecture/versions/data-model-design-v1.1.1.md`](../architecture/versions/data-model-design-v1.1.1.md) §1.1.3 计划模块字段

## 参考目录

- backend：`app/src/main/java/com/lifewise/plan/`
  - `controller/` — PlanController / MilestoneController / ProgressController
  - `service/` — PlanService / MilestoneService / ProgressEvaluator / LastActivityRefresher / MissedMilestoneJob
  - `domain/` — Plan / Milestone / MilestoneTaskLink
  - `repository/` — PlanRepository / MilestoneRepository / MilestoneTaskLinkRepository
  - `port/` — PlanReadPort（暴露给其他模块）
  - `event/` — PlanCreated / MilestoneCreated / MilestoneUpdated / MilestoneCompleted / MilestoneMissed
  - `dto/` — PlanCreateRequest / PlanView / MilestoneRequest / MilestoneView / ProgressView
- frontend：`docs/lifewise/designs/05-plan-ui/`
  - `new-05-plan-ui.html` — 主界面原型（计划列表 + 里程碑时间轴 + 进度条）

## 1. 模块边界 / 包结构

plan 模块是**长期目标计划**的入口，依赖 task 模块（`milestone_task_links` 关联 + `TaskReadPort` 读 task 状态）。

```
plan/
├── controller/
│   ├── PlanController.java            /api/plans CRUD
│   ├── MilestoneController.java       /api/plans/{id}/milestones CRUD
│   └── ProgressController.java        /api/plans/{id}/progress（聚合进度）
├── service/
│   ├── PlanService.java               创建/更新/软删（写 outbox）
│   ├── MilestoneService.java          创建/更新/完成/标记 MISSED（写 outbox）
│   ├── ProgressEvaluator.java         监听 task.completed / task.reopened → 评估 milestone
│   ├── LastActivityRefresher.java     监听 task.* / milestone.* → 刷新 plans.last_activity_at（BR-30）
│   └── MissedMilestoneJob.java        @Scheduled 每日检查到期未完成 milestone → 标记 MISSED
├── domain/
│   ├── Plan.java                      plans 表实体
│   ├── Milestone.java                 milestones 表实体
│   └── MilestoneTaskLink.java         milestone_task_links 关联实体
├── repository/
│   ├── PlanRepository.java
│   ├── MilestoneRepository.java
│   └── MilestoneTaskLinkRepository.java
├── port/
│   └── PlanReadPort.java              实现 PlanReadPortAdapter
├── event/
│   ├── PlanCreatedEvent.java          payload: {plan_id, user_id, start_at, end_at, category}
│   ├── MilestoneCreatedEvent.java     payload: {milestone_id, plan_id, user_id, due_at}
│   ├── MilestoneUpdatedEvent.java     payload: {milestone_id, plan_id, user_id, change_type}
│   ├── MilestoneCompletedEvent.java   payload: {milestone_id, plan_id, user_id, completed_at}
│   └── MilestoneMissedEvent.java      payload: {milestone_id, plan_id, user_id, due_at}
└── dto/
    ├── PlanCreateRequest.java         {title, description, category, start_at, end_at}
    ├── PlanView.java                  完整视图（含 milestones + progress）
    ├── MilestoneRequest.java          {title, due_at, task_ids[]}
    ├── MilestoneView.java
    └── ProgressView.java              {completed_milestones, total_milestones, pct, last_activity_at}
```

## 2. API 契约

### 2.1 Plan CRUD（6 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/plans` | query: `?status=&category=&page=&limit=` | `{data: PlanListItem[], meta}` | — |
| GET | `/api/plans/{id}` | — | `{data: PlanView}` | `PLAN_NOT_FOUND` |
| POST | `/api/plans` | `PlanCreateRequest` | `{data: PlanView}` | `VALIDATION_FAILED` / `END_BEFORE_START` |
| PUT | `/api/plans/{id}` | `PlanCreateRequest` | `{data: PlanView}` | `PLAN_NOT_FOUND` |
| DELETE | `/api/plans/{id}` | — | `{message: "ok"}` | `PLAN_NOT_FOUND`（软删 + 级联软删 milestones） |
| POST | `/api/plans/{id}/abandon` | — | `{data: PlanView}` | `PLAN_NOT_FOUND`（status → ABANDONED） |

### 2.2 Milestone（7 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/plans/{id}/milestones` | — | `{data: MilestoneView[]}` | — |
| POST | `/api/plans/{id}/milestones` | `MilestoneRequest` | `{data: MilestoneView}` | `PLAN_NOT_FOUND` / `VALIDATION_FAILED` |
| PUT | `/api/milestones/{mid}` | `MilestoneRequest` | `{data: MilestoneView}` | `MILESTONE_NOT_FOUND` / `MILESTONE_DONE_READONLY`（BR-14） |
| DELETE | `/api/milestones/{mid}` | — | `{message: "ok"}` | `MILESTONE_NOT_FOUND`（软删） |
| POST | `/api/milestones/{mid}/complete` | — | `{data: MilestoneView}` | `MILESTONE_NOT_FOUND` / `ALREADY_DONE`（BR-14 幂等） |
| POST | `/api/milestones/{mid}/reopen` | — | `{data: MilestoneView}` | `MILESTONE_NOT_FOUND` / `NOT_DONE` |
| POST | `/api/milestones/{mid}/link-tasks` | `{task_ids[]}` | `{data: MilestoneView}` | `TASK_NOT_FOUND` / `CROSS_USER` |

### 2.3 Progress（1 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/plans/{id}/progress` | — | `{data: ProgressView}` |

返回：完成里程碑数、总里程碑数、百分比、`last_activity_at`。

### 2.4 PlanReadPort（跨模块只读契约）

```java
public interface PlanReadPort {
    Optional<PlanSnapshot> findById(Long userId, Long planId);
    List<PlanSnapshot> findActiveByUser(Long userId);
    List<PlanSnapshot> findStale(Long userId, int days);      // last_activity_at < NOW - days（PRD 14 天未更新提醒）
    ProgressSnapshot computeProgress(Long userId, Long planId);
    List<MilestoneSnapshot> findMilestonesByTaskId(Long userId, Long taskId);
}
```

## 3. 数据模型（V4）

| 表 | 关键字段 | BR |
|---|---|---|
| `plans` | `status ∈ {ACTIVE,DONE,ABANDONED}` / `last_activity_at` / `start_at/end_at` | BR-15 / BR-30 / H-4 |
| `milestones` | `status ∈ {PENDING,DONE,MISSED}` / `due_at` / `due_at_tz` / `completed_at` / `plan_id` | BR-14 / L-5 |
| `milestone_task_links` | `(milestone_id, task_id)` PK | — |

索引：
- `idx_plans_user_status` ON `plans(user_id, status)`
- `idx_plans_user_last_activity` ON `plans(user_id, last_activity_at DESC)`（BR-30 + PRD 14 天提醒）
- `idx_milestones_plan_due` ON `milestones(plan_id, due_at)`
- `idx_milestones_user_status_due` ON `milestones(user_id, status, due_at)`（MissedMilestoneJob 扫描）

## 4. Outbox 事件（5 条 + 消费 task 4 条）

### 4.1 plan 模块发布（5 条）

| event_type | 触发 | payload | 消费方 |
|---|---|---|---|
| `plan.created` | plans INSERT | `{plan_id, user_id, start_at, end_at, category}` | ai（计划合理性评估） |
| `milestone.created` | milestones INSERT | `{milestone_id, plan_id, user_id, due_at}` | ai + daily_report（可选触发摘要） |
| `milestone.updated` | milestones UPDATE | `{milestone_id, plan_id, user_id, change_type}` | ai（重新评估） |
| `milestone.completed` | milestones.status → DONE + completed_at | `{milestone_id, plan_id, user_id, completed_at}` | ai + daily_report（触发日报摘要） |
| `milestone.missed` | MissedMilestoneJob 标记 status → MISSED | `{milestone_id, plan_id, user_id, due_at}` | ai（跨模块洞察） |

### 4.2 plan 模块订阅（4 条 task.* 事件）

| 来源 | 用途 |
|---|---|
| `task.completed` | ProgressEvaluator 评估关联 milestone 是否可标 DONE（所有 task 都完成） |
| `task.reopened` | ProgressEvaluator 重评估 milestone 是否回到 PENDING |
| `task.created` | LastActivityRefresher 刷新 plans.last_activity_at（BR-30） |
| `task.updated` | LastActivityRefresher 刷新 plans.last_activity_at（BR-30） |

## 5. 关键验收场景（TDD 种子）

### 5.1 Plan CRUD

- `plan_create_should_reject_when_end_before_start`：end_at <= start_at → `END_BEFORE_START`
- `plan_create_should_set_default_status_active`：未指定 status → ACTIVE
- `plan_update_should_preserve_status_active`：update 不允许改 status（DONE/ABANDONED 走专门接口）
- `plan_delete_should_soft_delete_and_cascade_milestones`：软删 plan → milestones 软删
- `plan_abandon_should_set_status_abandoned`：abandon 接口 → status=ABANDONED
- `plan_query_should_filter_by_status`：query 参数生效
- `plan_query_should_exclude_abandoned_by_default`：默认 status != ABANDONED

### 5.2 Milestone

- `milestone_create_should_set_default_status_pending`：未指定 status → PENDING
- `milestone_create_should_capture_user_timezone`：due_at_tz = user.timezone（L-5 / BR-29）
- `milestone_update_should_reject_when_done`：status=DONE 后改 title/due_at → `MILESTONE_DONE_READONLY`（BR-14）
- `milestone_complete_should_set_completed_at`：调用 `/complete` → status=DONE + completed_at=NOW
- `milestone_complete_should_be_idempotent`：重复 complete → 409 `ALREADY_DONE`（BR-14 幂等）
- `milestone_reopen_should_clear_completed_at`：调用 `/reopen` → status=PENDING + completed_at=null
- `milestone_link_tasks_should_validate_user`：task.userId != current → `CROSS_USER`
- `milestone_link_tasks_should_reject_duplicate`：同 task 重复关联同一 milestone → 400（D1：原文 `_allow_duplicate` 与断言 400 矛盾，方法名应为 `_reject_duplicate`；业务语义：同一 milestone 下 task 重复 link 由 UNIQUE (milestone_id, task_id) 拒绝）
- `milestone_delete_should_soft_delete`：deleted_at 写入

### 5.3 ProgressEvaluator（核心）

- `evaluator_should_complete_milestone_when_all_tasks_done`：关联 task 全部 DONE → milestone.status=DONE + 写 milestone.completed
- `evaluator_should_reopen_milestone_when_task_reopened`：任一 task 回到 OPEN → milestone.status=PENDING（BR-14）
- `evaluator_should_not_complete_when_partial`：部分 task 仍 OPEN → milestone 保持 PENDING
- `evaluator_should_skip_done_milestone`：milestone 已 DONE 不再接受 task 完成事件（BR-14）
- `evaluator_should_handle_task_withdrawn_from_milestone`：task 从 milestone 解绑 → 重评估
- `evaluator_should_use_idempotency_key`：BR-14 幂等键（milestone_id + task_id + event_type）

### 5.4 LastActivityRefresher（BR-30）

- `refresher_should_update_on_task_completed`：task 完成 → plans.last_activity_at = NOW
- `refresher_should_update_on_task_created`：task 新增 → plans.last_activity_at = NOW
- `refresher_should_update_on_milestone_changed`：milestone CUD → plans.last_activity_at = NOW
- `refresher_should_target_only_related_plan`：只刷新 task/milestone 所属 plan
- `refresher_should_be_idempotent`：连续多次触发只更新一次

### 5.5 MissedMilestoneJob（@Scheduled 每日 03:30）

- `job_should_mark_milestone_missed`：due_at < today + status=PENDING → status=MISSED
- `job_should_skip_when_done`：status=DONE 不处理
- `job_should_emit_missed_event`：标记后写 milestone.missed 事件
- `job_should_respect_user_timezone`：按 user.timezone 判定自然日（L-5 / BR-29）

### 5.6 Outbox / Port

- `plan_should_emit_created_event`：创建 → plan.created
- `milestone_should_emit_completed_event`：完成 → milestone.completed
- `port_should_find_stale_plans`：14 天未更新 → PRD 提醒
- `port_should_find_milestones_by_task`：task → milestone 反向查询

### 5.6.1 PlanStaleNotifyJob（@Scheduled 每日 09:00，H-4 提醒）

- `stale_job_should_scan_last_activity_at_older_than_14d`：扫描 `plans.last_activity_at < NOW - 14d`
- `stale_job_should_filter_status_active_only`：只过滤 status=ACTIVE（DONE/ABANDONED 不提醒）
- `stale_job_should_emit_notification_requested`：每个命中 plan 写一条 `notification_requests(type='plan.stale')` 触发 Web Push（X7：经 outbox `notification.requested` → notify 模块统一投递；消费方映射见 references/shared-strings.md §2）
- `stale_job_should_be_idempotent_per_day`：同一 plan 同一自然日不重复发（user.timezone 决定自然日边界）

### 5.7 UI（浏览器手动验证）

- `ui_plan_list_should_render_status_badge`：ACTIVE 绿色 / DONE 灰色 / ABANDONED 红色
- `ui_milestone_timeline_should_show_progress`：时间轴 + 进度条
- `ui_progress_bar_should_show_pct`：百分比正确
- `ui_stale_plan_should_show_warning`：14 天未更新 → 黄色警告
- `ui_responsive_mobile`：移动端计划卡片

## 6. 验收标准

- [ ] 14 个 API 端点全部实现 + Swagger 文档
- [ ] 3 张表 Repository 单测覆盖率 ≥ 85%
- [ ] 5 条 Outbox 事件注册到 EventType 枚举
- [ ] 订阅 task.completed / task.reopened / task.created / task.updated 共 4 条事件
- [ ] PlanReadPort 暴露给其他模块（含 findStale）
- [ ] MissedMilestoneJob 每日 03:30 调度跑通
- [ ] PlanStaleNotifyJob 每日 09:00 调度跑通（last_activity_at < 14d 且 status=ACTIVE → notification）
- [ ] 关键路径 100% 覆盖（ProgressEvaluator / LastActivityRefresher / 14 天提醒）
- [ ] UI 主界面浏览器手动验证
- [ ] PRD 05 §BR 全部覆盖（BR-14/15/29/30 + H-4 + L-5）

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| ProgressEvaluator 死循环（事件→评估→事件） | 高 | 幂等键 + DONE milestone 不再接受事件（BR-14） |
| LastActivityRefresher 性能 | 中 | 索引 + 单事件批量更新（同一 plan 多 task 合并） |
| MissedMilestoneJob 时区偏差 | 中 | 强制 `user.timezone`（BR-29） |
| milestone 完成后误改 | 中 | BR-14 应用层守卫 + 状态机校验 |
| 14 天提醒误报 | 低 | last_activity_at 刷新准 + user 可手动 dismiss |
| plan 删除后 task 关联残留 | 中 | milestone_task_links ON DELETE 不级联（task 仍可见，但 plan 软删） |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`
  - `plan-data-flyway.md`（V4 plans / milestones / milestone_task_links）
  - `plan-shared-infra.md`
  - `plan-shared-integration.md`
  - `plan-auth.md`
  - `plan-01-task.md`（**强依赖**：消费 task.* 事件 + TaskReadPort + milestone_task_links）
  - `plan-02-daily.md`（无强依赖）
  - `plan-03-expense.md`（无强依赖）
  - `plan-04-diet.md`（无强依赖）
- 下游：
  - `plan-06-ai.md`（消费 plan.* / milestone.* + PlanReadPort + findStale 提醒）
  - `plan-observability-backup.md`（监控 last_activity_at 14 天未更新 + milestone.missed 事件流）