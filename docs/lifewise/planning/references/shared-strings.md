# Shared Strings Reference

> 跨 `plan-*.md` 共享的「魔法字符串」单一 source of truth。
> 文档禁止硬编码，新写段落用 `[[ref:xxx]]` 引用本文件锚点；已写文档暂不全量迁移，避免一次改动过大。

---

## 1. Outbox 事件名（25 条，V33 CHECK 已注册）

| event_type | 触发源 | 消费方 |
|---|---|---|
| `task.completed` | tasks.status → DONE | plan |
| `task.reopened` | tasks.status → OPEN | plan |
| `task.created` | tasks INSERT | plan（BR-30 刷新 last_activity_at） |
| `task.updated` | tasks UPDATE | plan |
| `milestone.created` | milestones INSERT | plan |
| `milestone.updated` | milestones UPDATE | plan |
| `milestone.completed` | milestones.status → DONE | ai, daily_report |
| `milestone.missed` | MissedMilestoneJob | ai |
| `habit.logged` | habit_logs INSERT | daily_report, ai |
| `daily_report.created` | daily_reports INSERT | ai |
| `daily_report.updated` | daily_reports UPDATE | ai |
| `ai.summary.generated` | ai_summaries INSERT | user（SSE） |
| `meal.created` | meals INSERT | ai |
| `expense.created` | expenses INSERT | ai, **notify**（X7：原 push_subscriptions 直接消费已废弃，统一走 notify 模块） |
| `budget.threshold` | BudgetEvaluator | **notify**（X7） |
| `plan.created` | plans INSERT | ai |
| `ai.job.completed` | ai_jobs.status → DONE / DONE_NO_LLM / DONE_PARTIAL | user（SSE） + notify |
| `ai.report.feedback` | chat_feedbacks INSERT | — |
| `export.completed` | export_artifacts INSERT | user |
| `export.failed` | export_requests.status → FAILED | user |
| `notification.requested` | notification_requests INSERT | notification_deliveries |
| `auth.user.registered` | users INSERT（X7） | notify + ai |
| `auth.user.logged_in` | refresh_tokens 写入（X7） | notify |
| `auth.user.password_reset_requested` | password_resets INSERT（X7） | notify |
| `auth.token.reuse_detected` | JwtRefreshService 检测到 reuse（X7） | notify + 全家族失效 |

来源：`plan-data-flyway.md §3.35 V33` + `plan-shared-integration.md §4`。

## 2. Notification type（7 条，V24 CHECK 草案）

> **TODO(评审后)**：plan-data-flyway V24 SQL 当前只声明 `notification_requests` 表，未列 `type` 字段 CHECK 约束；本节作为评审后的目标值。

| type | 触发源 | 来源模块 |
|---|---|---|
| `task.due_soon` | TaskDueSoonJob（每日 09:00 扫描 due_at ∈ [NOW, NOW+24h]） | task |
| `habit.missed` | HabitMissedJob（每日 00:00 扫描昨日/上周 habit_logs 缺失） | task |
| `plan.stale` | PlanStaleNotifyJob（每日 09:00 扫描 last_activity_at < NOW - 14d, status=ACTIVE） | plan |
| `budget.threshold.80` | BudgetEvaluator 检测到 ≥ 80% | expense |
| `budget.threshold.100` | BudgetEvaluator 检测到 ≥ 100% | expense |
| `ai.report.done` | 监听 `ai.job.completed`（status=DONE / DONE_NO_LLM / DONE_PARTIAL） | ai |
| `milestone.due_soon` | MilestoneDueSoonJob（每日 09:00 扫描 due_at ∈ [NOW, NOW+3d]） | plan |

来源：`plan-notify.md §5`。

## 3. Export 模块 / 格式（V34 CHECK）

- **模块 6 项**：`task` / `daily_report` / `expense` / `meal` / `plan` / `ai`
- **格式 5 项**：`csv` / `json` / `markdown` / `zip` / `pdf`
  - v1.0 投产：`csv` / `json` / `markdown`
  - v1.1+ 预留：`zip` / `pdf`

来源：`plan-export.md §3.1` + `plan-data-flyway.md §3.36 V34`。

## 4. 物化视图唯一索引（CONCURRENTLY 前置条件）

- `mv_expense_monthly_category`：`UNIQUE INDEX uq_mv_expense_user_month_category(user_id, period_year, period_month, category_id)`
- `mv_meal_nutrition_weekly`：`UNIQUE INDEX uq_mv_meal_user_week(user_id, period_year, period_week)`

来源：`plan-data-flyway.md §5`。

## 5. cron 表达式（10 个 cron，对应 13 个 @Scheduled Job）

| cron | Job | 来源 |
|---|---|---|
| `0 * * * * *` | OutboxDeliveryJob（每分钟） | observability |
| `0 30 1 * * *` | EnsurePartitionJob（01:30） | observability |
| `0 0 2 * * *` | MaterializedViewRefreshJob.expense（02:00） | observability |
| `0 30 2 * * *` | MaterializedViewRefreshJob.meal（02:30） | observability |
| `0 0 3 * * *` | BackupJob（03:00） | observability |
| `0 30 3 * * *` | MissedMilestoneJob + PurgeChatMessagesJob（**H2 错峰**：03:30 与 02:30 MV REFRESH + 03:00 BackupJob 三方错峰） | plan + observability |
| `0 0 4 * * *` | OutboxDeadLetterJob + PushSubscriptionCleanupJob（04:00） | observability |
| `0 30 4 * * *` | PurgeSoftDeletedJob（**M5 错峰**：04:30 与 04:00 完全错峰） | observability |
| `0 0 0 * * *` | HabitMissedJob（00:00） | task |
| `0 0 9 * * *` | PlanStaleNotifyJob（09:00） | plan |

> 注：`BudgetEvaluator` 由 `expense.created` 事件驱动（plan-03-expense.md §1），不在 cron 调度清单内。

来源：`plan-observability-backup.md §4` + `plan-03-expense.md §1`。

## 6. nginx limit_req_zone

| zone | rate | 用途 | 维度 |
|---|---|---|---|
| `login:10m` | `rate=1r/m` | IP 级 5 req/15min（`burst=5 nodelay`） | per IP |
| `api:10m` | `rate=60r/m` | IP 级 60 req/min | per IP |
| `ai_chat:10m` | `rate=10r/m` | IP 级 10 req/min（`burst=2 nodelay`，与 app scope=ai 双重防护） | per IP |

来源：`plan-deploy-nginx.md §3`。

## 7. @RateLimit scope（5 项）

| scope | 限制 | key | 来源 |
|---|---|---|---|
| `api` | 60 req/min/user | userId | 通用 |
| `login` | 5 req/15min/IP | ip | 与 nginx IP 限流对齐 |
| `ai` | 10 req/min/user + 60 req/h/user + 100 req/min/global（三重） | userId + global | plan-06-ai §防 OOM |
| `export` | 5 req/min/user | userId | v1.2 export 模块 |
| `webpush` | 20 req/min/user | userId | v1.0 Web Push 投递 |

来源：`plan-shared-infra.md §2.2` + `plan-deploy-nginx.md §3`（login scope）。

## 8. BR / L / H 编号

- **BR 编号**：BR-01 ~ BR-30（v1.1.1 主干）+ BR-31 ~ BR-41（v1.2 新增 11 条）
- **L 编号**：L-1 ~ L-7（语义注释，如 L-1 食物软删兜底 / L-5 时区快照 / L-7 事件 version）
- **H 编号**：H-1 ~ H-6（架构硬约束，如 H-3 user_profiles / H-5 notify_muted_until）

来源：`docs/lifewise/architecture/data-model-v1.2-amendment.md` §0 + §1。

## 9. nginx location 块（精确匹配）

- `location = /api/auth/login`：IP 级 5/15min（`burst=5 nodelay`）+ `proxy_pass http://app/api/auth/login;`
- `location = /api/ai/chat`：IP 级 10r/m（`burst=2 nodelay`）+ SSE keepalive（`proxy_buffering off` + `proxy_read_timeout 24h`）+ `proxy_pass http://app/api/ai/chat;`
- `location /api/ai/`（通用，非精确）：`proxy_pass http://app;` 透传

来源：`plan-deploy-nginx.md §3`。

---

## 维护规则

1. 新增跨文件字符串 → 先在本文件登记 → 再在 plan-*.md 用 `[[ref:xxx]]` 引用
2. 修改已注册字符串 → 同步更新所有引用 + 加断言脚本覆盖
3. 文档 review checklist 必查本文件一致性