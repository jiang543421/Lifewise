# plan-notify 实施方案

## 参考资料

- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.3 通知模块边界 + §5 事件契约（`notification.requested`）
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V24（notification_requests + notification_deliveries，P1-NOTIFY-01/02）
- `docs/lifewise/specs/PRD/` — 各模块通知需求汇总
  - `01-task-management.md` §DR-NOTIFY — 任务到期提醒
  - `02-daily-report.md` §DR-NOTIFY — 日报提醒
  - `03-expense.md` §DR-NOTIFY — 预算超阈值
  - `04-diet.md` §DR-NOTIFY — 餐次提醒
  - `05-plan-management.md` §DR-NOTIFY — 计划 14 天未更新
  - `06-ai.md` §DR-NOTIFY — AI 报告完成
- `CLAUDE.md` §7 安全规范 + §10 红线

## 状态

> **占位骨架（v1.0 收口）**：本文件为 v1.2 新增通知模块的**实施骨架**，定义包结构、端点契约、事件契约、数据表复用与 TDD 种子。**v1.0 收口阶段不实现完整业务逻辑**——通知请求由各业务模块直接 INSERT `notification_requests` 表，Web Push 投递由 plan-shared-integration 的 OutboxWorker 监听 `notification.requested` 事件后异步触发（避免新引入跨模块横切）。
>
> v1.1+ 演进：抽出独立的 `notify` 模块，负责通知聚合、用户偏好、模板渲染、静默规则。

## 参考目录

- backend：`app/src/main/java/com/lifewise/notify/`（v1.1+ 预留）
  - `controller/` — NotificationController（v1.1+ 列表 / 已读 / 批量操作）
  - `service/` — NotificationService / WebPushDeliveryService / PreferenceResolver
  - `domain/` — NotificationRequest / NotificationDelivery
  - `repository/` — NotificationRequestRepository / NotificationDeliveryRepository
  - `port/` — NotificationRequestPort（暴露给其他模块写通知）
- infra：
  - `deploy/notify/` — VAPID 私钥 + 投递脚本（v1.0 由 plan-deploy-nginx 配 VAPID）

## 1. 模块边界 / 包结构

v1.0 阶段通知能力是**数据库表 + 事件投递**，无独立 Java 模块；其他业务模块直接 INSERT `notification_requests` 表，由 OutboxWorker 订阅 `notification.requested` 事件完成 Web Push 投递。

```
notify/（v1.1+ 抽取）
├── controller/
│   └── NotificationController.java     /api/notifications 列表 / 已读（v1.1+）
├── service/
│   ├── NotificationService.java        创建 / 状态机（PENDING → DELIVERED → READ）
│   ├── WebPushDeliveryService.java     调 Web Push API（VAPID 签名）
│   └── PreferenceResolver.java         解析 user_profiles.notification_enabled / quiet_hours
├── domain/
│   ├── NotificationRequest.java
│   └── NotificationDelivery.java
├── repository/
│   └── ...
├── port/
│   └── NotificationRequestPort.java    业务模块用此 Port 写通知（不直接 INSERT）
├── event/
│   ├── NotificationRequestedEvent.java
│   └── NotificationDeliveredEvent.java
└── dto/
    ├── NotificationCreateRequest.java
    └── NotificationView.java
```

## 2. API 契约

### 2.1 v1.0 阶段（直接 INSERT，无 HTTP API）

| 来源 | 操作 | 路径 |
|---|---|---|
| task 模块 | 到期任务 → `INSERT notification_requests` | DB 直写（v1.0） |
| plan 模块 | 14 天未更新 → `INSERT notification_requests` | DB 直写（v1.0） |
| expense 模块 | 预算超阈值 → `INSERT notification_requests` | DB 直写（v1.0） |
| ai 模块 | AI 报告完成 → `INSERT notification_requests` | DB 直写（v1.0） |

### 2.2 v1.1+ HTTP API（占位）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/notifications` | query: `?status=&type=&page=&limit=` | `{data: NotificationView[], meta}` | — |
| GET | `/api/notifications/unread-count` | — | `{data: {count: int}}` | — |
| POST | `/api/notifications/{id}/read` | — | `{message: "ok"}` | `NOTIFICATION_NOT_FOUND` |
| POST | `/api/notifications/read-batch` | `{ids: long[]}` | `{data: {updated: int}}` | — |
| PUT | `/api/notifications/preferences` | `{push_enabled, quiet_hours_start, quiet_hours_end}` | `{data: PreferenceView}` | `VALIDATION_FAILED` |
| GET | `/api/notifications/preferences` | — | `{data: PreferenceView}` | — |

## 3. 数据模型（V24 由本文件引入，复用）

### 3.1 notification_requests

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | IDENTITY |
| `user_id` | BIGINT NOT NULL | 所有权 |
| `source_module` | TEXT | task / habit / plan / milestone / expense / budget / ai_report / export_request / system（与 V24 source_module CHECK 对齐） |
| `source_aggregate_type` | TEXT | Task / Milestone / Expense / Meal / AiReport |
| `source_aggregate_id` | BIGINT | 关联业务实体 ID |
| `type` | TEXT | 7 类（N2/N3 修正）：`task.due_soon` / `habit.missed` / `plan.stale` / `budget.threshold.80` / `budget.threshold.100` / `ai.report.done` / `milestone.due_soon`（与 §5 触发源清单对齐；V24 type CHECK 草案见 references/shared-strings.md §2，待评审后落库） |
| `title` | TEXT | 推送标题（≤ 80 字符） |
| `body` | TEXT | 推送正文（≤ 500 字符） |
| `data_json` | JSONB | 跳转链接 / 业务参数 |
| `priority` | SMALLINT | 0=LOW 1=NORMAL 2=HIGH |
| `status` | TEXT | PENDING / DELIVERED / FAILED / READ / DISMISSED |
| `scheduled_at` | TIMESTAMPTZ | 定时发送（NULL=立即） |
| `delivered_at` | TIMESTAMPTZ NULL | 投递完成时间 |
| `read_at` | TIMESTAMPTZ NULL | 用户已读时间 |
| `attempts` | SMALLINT DEFAULT 0 | 投递尝试次数 |
| `last_error` | TEXT | 最后一次失败原因 |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | — |

索引：
- `idx_notify_user_status_created` ON `notification_requests(user_id, status, created_at DESC)`（列表查询）
- `idx_notify_scheduled_pending` ON `notification_requests(scheduled_at) WHERE status = 'PENDING'`（定时投递扫描）
- `UNIQUE (source_module, source_aggregate_type, source_aggregate_id, type)`（去重，避免重复推送）

### 3.2 notification_deliveries

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | IDENTITY |
| `notification_request_id` | BIGINT NOT NULL REFERENCES notification_requests(id) ON DELETE CASCADE |
| `channel` | TEXT | `PUSH` / `IN_APP` / `EMAIL`（与 V24 channel CHECK 对齐；v1.0 默认 PUSH） |
| `device_endpoint` | TEXT NULL | Web Push endpoint URL（推送订阅表关联） |
| `status` | TEXT | SUCCESS / FAILED / EXPIRED |
| `http_status` | SMALLINT NULL | Web Push API HTTP 状态码 |
| `error_message` | TEXT NULL | 失败原因（410 GONE → 清理 push_subscription） |
| `delivered_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | — |

索引：
- `idx_notify_deliveries_request_id` ON `notification_deliveries(notification_request_id)`

## 4. Outbox 事件（1 条消费）

| event_type | 触发源 | 消费方 | 行为 |
|---|---|---|---|
| `notification.requested` | notification_requests INSERT（status=PENDING） | shared-integration / WebPushDeliveryService（v1.1+）/ push_subscriptions（v1.0 简化） | 读取 user_profiles.notification_enabled + quiet_hours → 选择 push_subscriptions → 调 Web Push API → 写 notification_deliveries → 更新 notification_requests.status |

> **v1.0 简化路径**：v1.0 不引入独立 WebPushDeliveryService，由 OutboxDispatcher 在 shared-integration 模块中**直接调用** push_subscriptions 表 + Web Push API（http_send.sh / Java HttpClient）。v1.1+ 抽出后改为 `notify.WebPushDeliveryService`。

## 5. v1.0 触发源清单（写 `notification_requests`）

| 触发时机 | 来源模块 | type | 触发器 |
|---|---|---|---|
| 任务到期前 24h | task | `task.due_soon` | `TaskDueSoonJob`（每日 09:00，扫描 tasks WHERE due_at BETWEEN NOW() AND NOW()+24h） |
| 习惯昨日/上周未打卡 | task | `habit.missed` | `HabitMissedJob`（见 plan-observability-backup §4） |
| 计划 14 天未更新 | plan | `plan.stale` | `PlanStaleNotifyJob`（见 plan-observability-backup §4） |
| 月预算累计 ≥ 80% | expense | `budget.threshold.80` | `BudgetEvaluatorJob`（每日 01:00，emit `budget.threshold` 事件由 notify 消费；X7 对齐） |
| 月预算累计 ≥ 100% | expense | `budget.threshold.100` | `BudgetEvaluatorJob`（emit `budget.threshold` 事件由 notify 消费；X7 对齐） |
| AI 报告生成完成 | ai | `ai.report.done` | 监听 `ai.job.completed` 事件（status=DONE / DONE_NO_LLM / DONE_PARTIAL） |
| 里程碑即将到期（3 天内） | plan | `milestone.due_soon` | `MilestoneDueSoonJob`（每日 09:00，v1.1+ 引入；v1.0 由 MissedMilestoneJob 顺带） |

## 6. 关键验收场景（TDD 种子）

### 6.1 notification_requests 写入

- `notify_should_dedupe_by_source_unique`：UNIQUE 约束防重复（同一 task.due_soon 同 task 不写两次）
- `notify_should_set_default_status_pending`：未指定 status → PENDING
- `notify_should_capture_source_aggregate`：必填 source_module + aggregate_id
- `notify_should_validate_title_length`：title > 80 字符 → 400 `VALIDATION_FAILED`
- `notify_should_validate_body_length`：body > 500 字符 → 400 `VALIDATION_FAILED`
- `notify_should_respect_scheduled_at`：scheduled_at > NOW → 状态保持 PENDING 等到时间

### 6.2 Web Push 投递（v1.0 简化 / v1.1+ 完整）

- `webpush_should_skip_when_notification_disabled`：user_profiles.notification_enabled=false → 跳过 + status=DISMISSED
- `webpush_should_respect_quiet_hours`：quiet_hours_start ≤ NOW < quiet_hours_end → 跳过（status=PENDING，等待到 quiet 结束）
- `webpush_should_pick_all_user_subscriptions`：user 多设备 → 多条 notification_deliveries
- `webpush_should_handle_410_gone`：Web Push 返回 410 → 删除 push_subscriptions + status=FAILED
- `webpush_should_retry_with_backoff`：5xx → 重试 3 次（1s / 5s / 30s）→ 3 次失败 status=FAILED
- `webpush_should_mark_delivered_on_2xx`：2xx → status=DELIVERED + delivered_at=NOW
- `webpush_should_write_delivery_record`：每次投递 → INSERT notification_deliveries

### 6.3 用户偏好（v1.1+）

- `preference_should_default_push_enabled`：新用户默认 push_enabled=true
- `preference_should_validate_quiet_hours`：start < end（不允许跨天配置 v1.1）
- `preference_should_update_on_put`：PUT /api/notifications/preferences → 持久化

### 6.4 列表 / 已读（v1.1+）

- `list_should_filter_by_status`：query.status 生效
- `list_should_exclude_dismissed_by_default`：默认 status != DISMISSED
- `read_should_set_read_at`：POST /:id/read → read_at=NOW + status=READ
- `read_batch_should_validate_ids_exist`：ids 不存在 → 400

## 7. 验收标准

- [ ] V24 notification_requests + notification_deliveries 表落地
- [ ] `notification.requested` 事件在 EventType 枚举注册
- [ ] 7 类触发源（task.due_soon / habit.missed / plan.stale / budget.threshold.80 / budget.threshold.100 / ai.report.done / milestone.due_soon）至少跑通 3 类（v1.0 milestone.due_soon 触发器未上线但 type CHECK 已注册，V24 SQL 落库待评审，与 references/shared-strings.md §2 + §3 表对齐）
- [ ] Web Push 投递链路（VAPID 签名 → 推送 → 记录 delivery）单测覆盖 ≥ 85%
- [ ] 410 GONE 场景 → push_subscriptions 自动清理（防泄漏）
- [ ] quiet_hours + notification_enabled 关闭时跳过投递（v1.0 至少支持 notification_enabled）
- [ ] 关键路径 100% 覆盖（去重 / 投递 / 失败重试 / 410 GONE）
- [ ] v1.1+ 完整 HTTP API（列表 / 已读 / 偏好）作为 backlog，不在 v1.0 范围

## 8. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| Web Push endpoint 失效（410 GONE） | 高 | 投递时检测 + 自动清理 push_subscriptions |
| 推送轰炸（用户高频操作） | 高 | UNIQUE 去重 + quiet_hours + 优先级过滤 |
| VAPID 私钥泄露 | 高 | deploy/notify/.env 注入，权限 600，90 天轮换（CLAUDE.md §7.1） |
| 通知堆积（用户离线） | 中 | 30 天未读清理（PurgeNotificationJob 预留）+ status=DISMISSED |
| 投递失败重试放大 | 中 | 指数退避 + 最多 3 次 → status=FAILED 后人工介入 |
| v1.0 简化路径与 v1.1+ 重构冲突 | 低 | v1.0 INSERT + Outbox + Web Push；v1.1+ 抽取独立 service，事件契约不变 |

## 9. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（VAPID 公钥 / 私钥生成）
  - `plan-data-flyway.md`（V24 notification_requests + notification_deliveries 表）
  - `plan-shared-integration.md`（OutboxWorker 监听 notification.requested + push_subscriptions）
  - `plan-01-task.md`（TaskDueSoonJob + HabitMissedJob）
  - `plan-03-expense.md`（BudgetEvaluatorJob）
  - `plan-05-plan.md`（PlanStaleNotifyJob + MissedMilestoneJob）
  - `plan-06-ai.md`（ai.job.completed 事件触发）
- 下游：
  - `plan-observability-backup.md`（监控 notification_requests 堆积 + 投递失败率）
- 后续：
  - v1.1+ 完整化（HTTP API + 偏好 + 模板渲染）→ 独立 notify 模块
