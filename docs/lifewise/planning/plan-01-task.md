# plan-01-task 实施方案

## 参考资料

- [`docs/lifewise/specs/PRD/01-task-management.md`](../specs/PRD/01-task-management.md) — 产品 PRD
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.2 task 模块边界
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V3 + V10（tasks / habits 索引）
- [`docs/lifewise/designs/01-task-ui/2026-07-26-task-ui-design.md`](../designs/01-task-ui/2026-07-26-task-ui-design.md) — UI 设计契约
- [`docs/lifewise/architecture/versions/data-model-design-v1.1.1.md`](../architecture/versions/data-model-design-v1.1.1.md) §1.1.2 任务模块字段

## 参考目录

- backend：`app/src/main/java/com/lifewise/task/`
  - `controller/` — TaskController / HabitController / TagController
  - `service/` — TaskService / HabitService / StreakService / TagService / TaskQueryService
  - `domain/` — Task / Habit / HabitLog / TaskTag / TaskTagLink
  - `repository/` — TaskRepository / HabitRepository / HabitLogRepository / TaskTagRepository
  - `port/` — TaskReadPort（暴露给其他模块）
  - `event/` — TaskCompleted / TaskReopened / TaskChanged / HabitLogged
  - `dto/` — TaskCreateRequest / TaskUpdateRequest / TaskView / HabitView
- frontend：`docs/lifewise/designs/01-task-ui/`
  - `new-01-task-ui.html` — 主界面原型
  - 设计 token：复用 `--primary / --bg-soft / --ink-1..3` 全局别名

## 1. 模块边界 / 包结构

task 是**6 业务模块的核心基础**，被 daily / plan 直接引用（daily 引用 task 作为亮点来源，plan 通过 milestone_task_links 关联 task）。

```
task/
├── controller/
│   ├── TaskController.java            /api/tasks CRUD + 完成/重开
│   ├── HabitController.java           /api/habits CRUD + 打卡 + streak
│   └── TagController.java             /api/task-tags CRUD
├── service/
│   ├── TaskService.java               创建/更新/软删/完成/重开，写 Outbox
│   ├── HabitService.java              创建/打卡/补卡/计算 streak
│   ├── StreakService.java             按 (habit, user, timezone) 计算连续天数
│   ├── TagService.java                标签绑定 ≤ 5（BR-03）
│   └── TaskQueryService.java          列表 / 搜索 / 筛选
├── domain/
│   ├── Task.java                      tasks 表实体（含 parent_id 自循环）
│   ├── Habit.java                     habits 表实体
│   ├── HabitLog.java                  habit_logs 表实体
│   ├── TaskTag.java                   task_tags 表实体
│   └── TaskTagLink.java               task_tag_links 关联实体
├── repository/
│   ├── TaskRepository.java
│   ├── HabitRepository.java
│   ├── HabitLogRepository.java
│   └── TaskTagRepository.java
├── port/
│   └── TaskReadPort.java              实现类 TaskReadPortAdapter，供其他模块注入
├── event/
│   ├── TaskCompletedEvent.java        payload: {task_id, user_id, plan_id?, completed_at}
│   ├── TaskReopenedEvent.java         payload: {task_id, user_id, plan_id?, previous_completed_at}
│   ├── TaskChangedEvent.java          payload: {task_id, user_id, change_type}
│   └── HabitLoggedEvent.java          payload: {habit_id, user_id, log_date, count}
└── dto/
    ├── TaskCreateRequest.java         {title, note, priority, due_at, parent_id?, tag_ids[]}
    ├── TaskUpdateRequest.java         同上 + id
    ├── TaskView.java                  完整视图
    ├── TaskListItem.java              列表简化视图
    ├── HabitCreateRequest.java        {title, icon, frequency, target_count}
    ├── HabitLogRequest.java           {log_date, count, source}
    └── HabitView.java                 含 streak 字段
```

## 2. API 契约

### 2.1 Task CRUD（7 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/tasks` | query: `?status=&priority=&tag_id=&page=&limit=` | `{data: TaskListItem[], meta}` | — |
| GET | `/api/tasks/{id}` | — | `{data: TaskView}` | `TASK_NOT_FOUND` |
| POST | `/api/tasks` | `TaskCreateRequest` | `{data: TaskView}` | `VALIDATION_FAILED` / `TAG_LIMIT_EXCEEDED` |
| PUT | `/api/tasks/{id}` | `TaskUpdateRequest` | `{data: TaskView}` | `TASK_NOT_FOUND` / `PARENT_USER_MISMATCH`（BR-27） |
| DELETE | `/api/tasks/{id}` | — | `{message: "ok"}` | `TASK_NOT_FOUND`（软删） |
| POST | `/api/tasks/{id}/complete` | — | `{data: TaskView}` | `TASK_NOT_FOUND` / `ALREADY_COMPLETED` |
| POST | `/api/tasks/{id}/reopen` | — | `{data: TaskView}` | `TASK_NOT_FOUND` / `ALREADY_OPEN` |

### 2.2 Habit（5 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/habits` | — | `{data: HabitView[]}`（含 streak） | — |
| POST | `/api/habits` | `HabitCreateRequest` | `{data: HabitView}` | `VALIDATION_FAILED` |
| PUT | `/api/habits/{id}` | `HabitCreateRequest` | `{data: HabitView}` | `HABIT_NOT_FOUND` |
| DELETE | `/api/habits/{id}` | — | `{message: "ok"}` | `HABIT_NOT_FOUND`（软删） |
| POST | `/api/habits/{id}/logs` | `HabitLogRequest` | `{data: HabitLogView}` | `BACKFILL_OUT_OF_RANGE`（BR-05）/ `BACKFILL_RATE_LIMIT`（5 次/天） |

### 2.3 Tag（4 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/task-tags` | — | `{data: TaskTag[]}` |
| POST | `/api/task-tags` | `{name, color}` | `{data: TaskTag}` |
| PUT | `/api/task-tags/{id}` | `{name, color}` | `{data: TaskTag}` |
| DELETE | `/api/task-tags/{id}` | — | `{message: "ok"}` |

### 2.4 TaskReadPort（跨模块只读契约）

```java
public interface TaskReadPort {
    Optional<TaskSnapshot> findById(Long userId, Long taskId);
    List<TaskSnapshot> findByIds(Long userId, List<Long> taskIds);
    List<TaskSnapshot> findByPlanId(Long userId, Long planId);
    long countCompletedSince(Long userId, Instant since);
}
```

`TaskSnapshot` 为 record，不可变；不暴露 JPA entity。

### 2.5 TaskInternalQueryService（task 模块内部 service，**不**走 Port）

```java
// 任务内部高级查询 / 统计，不暴露给其他模块
@Service
public class TaskInternalQueryService {
    public List<TaskSnapshot> findCompletedSince(Long userId, Instant since, int limit); // 完成事件回溯（v1.1+ AI 报表用）
    public long countOpenByPriority(Long userId, TaskPriority priority);                 // 按优先级统计（v1.1+ AI 洞察用）
    public Map<Long, Long> countGroupByStatus(Long userId);                               // 按状态聚合（v1.1+）
}
```

> **M1 调整说明**：`findCompletedSince` / `countOpenByPriority` 等仅 task 内部使用的统计方法，从 TaskReadPort（跨模块契约）收回至 `TaskInternalQueryService`，避免共享层 API 膨胀。消费方（plan/ai/daily）当前未调用，v1.1+ 真有需求时再以**新方法名**暴露到 TaskReadPort。

## 3. 数据模型（V3 已在 plan-data-flyway）

| 表 | 关键字段 | BR |
|---|---|---|
| `tasks` | `priority ∈ {P0,P1,P2,P3}` / `status ∈ {OPEN,DONE}` / `parent_id` 自循环禁自身 | BR-01/02/27 |
| `task_tags` | `(user_id, name)` UNIQUE | — |
| `task_tag_links` | `(task_id, tag_id)` PK + UNIQUE | BR-03 任务 ≤ 5 标签 |
| `habits` | `frequency ∈ {DAILY,WEEKLY}` / `target_count ≥ 1` | BR-04 |
| `habit_logs` | `backfill_for_date ∈ [today-3, today)` | BR-05 |

索引：
- `idx_tasks_user_priority_status`（L-2）
- `idx_tasks_user_due`（按到期排序）
- `idx_habit_logs_user_date`（streak 计算）

## 4. Outbox 事件（5 条）

| event_type | 触发 | payload | 消费方 |
|---|---|---|---|
| `task.completed` | `tasks.status → DONE` + `completed_at` 写入 | `{task_id, user_id, plan_id?, completed_at}` | plan（milestone 评估） |
| `task.reopened` | `tasks.status → OPEN` | `{task_id, user_id, plan_id?, previous_completed_at}` | plan（milestone 重评估） |
| `task.created` | tasks INSERT（CU，**无 task.deleted**） | `{task_id, user_id, plan_id?, created_at}` | plan（BR-30 刷新 last_activity_at） |
| `task.updated` | tasks UPDATE（含 status 变更） | `{task_id, user_id, change_type}` | plan（BR-30） |
| `habit.logged` | habit_logs INSERT | `{habit_id, user_id, log_date, count}` | daily_report（相关性） / ai（统计） |

注：`milestone_task_links` 中 task 完成时 plan 评估走 `task.completed` 事件。

## 5. 关键验收场景（TDD 种子）

### 5.1 Task CRUD

- `task_create_should_reject_when_title_blank`：`title.trim()` 空 → 400
- `task_create_should_reject_when_priority_invalid`：非 P0~P3 → 400
- `task_create_should_reject_when_more_than_5_tags`：标签 > 5 → `TAG_LIMIT_EXCEEDED`
- `task_create_should_set_default_status_open`：未指定 status → OPEN
- `task_update_should_reject_when_parent_self`：parent_id == id → 400（BR-27）
- `task_update_should_reject_when_parent_user_mismatch`：parent.userId != current → 400
- `task_complete_should_set_completed_at`：调用 `/complete` → status=DONE + completed_at=NOW
- `task_complete_should_reject_already_done`：重复 complete → 409 `ALREADY_COMPLETED`
- `task_reopen_should_clear_completed_at`：调用 `/reopen` → status=OPEN + completed_at=null
- `task_delete_should_soft_delete`：deleted_at 写入
- `task_query_should_filter_by_status`：query 参数生效
- `task_query_should_paginate`：第 2 页返回正确切片 + meta.has_next

### 5.2 Habit

- `habit_create_should_reject_frequency_invalid`：非 DAILY/WEEKLY → 400（BR-04）
- `habit_create_should_reject_target_count_zero`：target_count < 1 → 400
- `habit_log_should_calculate_streak`：连续 7 天打卡 → streak=7
- `habit_log_should_reset_streak_on_miss`：中间漏 1 天 → streak 归零重计
- `habit_log_should_reject_backfill_out_of_range`：backfill_for_date < today-3 → 400（BR-05）
- `habit_log_should_rate_limit_backfill`：同习惯当天补卡 > 5 次 → 429
- `habit_log_should_respect_user_timezone`：streak 按 user.timezone 判定自然日
- `habit_delete_should_soft_delete`：habit_logs 不动，可查询历史

### 5.3 Tag

- `tag_create_should_reject_duplicate_name`：同用户下 name 重复 → 409
- `tag_delete_should_detach_tasks`：删除标签 → task_tag_links 清理

### 5.4 Outbox

- `task_should_emit_completed_event`：完成 → outbox_events 写入 task.completed
- `task_should_emit_reopened_event`：重开 → outbox_events 写入 task.reopened
- `task_should_emit_changed_event`：update → outbox_events 写入 task.updated
- `task_should_emit_created_event`：INSERT → outbox_events 写入 task.created（A1：原 plan-01-task §5.4 漏 task.created 事件测试，事件清单在 §4.1 已声明需消费方 plan 刷新 BR-30 last_activity_at；测试名对齐 §4 事件枚举）
- `outbox_should_rollback_on_business_failure`：service 异常 → outbox 也不写入
- `habit_should_emit_logged_event`：打卡 → outbox_events 写入 habit.logged

### 5.5 Port（其他模块集成）

- `port_should_resolve_snapshot_from_id`：plan 模块调 `findById` → 返回 TaskSnapshot
- `port_should_reject_cross_user`：userId 不匹配 → empty
- `port_should_count_completed_since`：daily 模块统计今日完成数

### 5.6 UI（浏览器手动验证）

- `ui_task_list_should_render_priority_badge`：P0 红色 / P3 灰色
- `ui_task_complete_should_collapse`：完成后从 OPEN 列表移除
- `ui_habit_streak_should_show_fire`：连续 7 天 → 显示 🔥7
- `ui_responsive_mobile`：移动端 task 卡片堆叠

## 6. 验收标准

- [ ] 16 个 API 端点全部实现 + Swagger 文档生成
- [ ] 5 张表 Repository 单测覆盖率 ≥ 85%
- [ ] 5 条 Outbox 事件全部注册到 EventType 枚举
- [ ] TaskReadPort 暴露给其他模块（plan/daily 集成测试）
- [ ] streak 计算跨时区正确（夏令时 / 跨年）
- [ ] 关键路径 100% 覆盖（完成 / 重开 / 打卡 / streak）
- [ ] UI 主界面在浏览器手动验证（Chrome + Firefox + Safari）
- [ ] PRD 01 §BR 全部覆盖（BR-01 ~ BR-05 + BR-27）

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| streak 计算跨时区错误 | 高 | 强制 `user.timezone` 入参；夏令时单测覆盖 |
| 任务软删后 Outbox 仍触发 | 中 | Outbox 写入前查 `deleted_at IS NULL` |
| 自循环任务 parent_id 死循环 | 中 | BR-27 + 应用层校验 + 限深 1 层 |
| 标签并发绑定超过 5 个 | 中 | `task_tag_links` UNIQUE + 应用层计数 |
| habit 补卡被滥用 | 中 | BR-05 + Redis 限流 + 日终审计 |
| TaskReadPort 暴露 JPA entity | 低 | 严格 record snapshot + code review |
| 大量任务列表性能 | 低 | 索引 + 分页 + 物化视图候选 |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（nginx 入口）
  - `plan-data-flyway.md`（V3 tasks / habits / tags 5 张表）
  - `plan-shared-infra.md`（@RequireAuth / @RateLimit / @Auditable）
  - `plan-shared-integration.md`（OutboxWriter + TaskReadPort 注册）
  - `plan-auth.md`（JWT userId 来源）
  - `plan-observability-backup.md`（HabitMissedJob 调度 + task.* 事件流监控 + operation_logs 90 天清理）
- 下游：
  - `plan-02-daily.md`（消费 habit.logged + TaskReadPort）
  - `plan-05-plan.md`（消费 task.completed / task.* + TaskReadPort + milestone_task_links）
  - `plan-03-expense.md`（独立，无依赖）
  - `plan-04-diet.md`（独立，无依赖）
  - `plan-06-ai.md`（最后做，消费 TaskReadPort 统计）