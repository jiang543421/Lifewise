# plan-data-flyway 实施方案

## 参考资料

- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) §0 主键策略 + §1 P0 修订 + §2 P1 修订（V21~V29 Flyway 脚本）
- [`docs/lifewise/architecture/versions/data-model-design-v1.1.1.md`](../architecture/versions/data-model-design-v1.1.1.md) §1 概念数据模型 + §2 数据库基础设施 + §3 表结构 DDL
- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §1.2 db 容器规格
- `CLAUDE.md` §6.4 TDD 流程 + §7.2 输入验证 + §10 红线（DB 结构变更必须先 Flyway 迁移 + 评审）

## 参考目录

- backend：`app/src/main/resources/db/migration/`（V1~V29 Flyway 脚本）+ `app/src/main/java/com/lifewise/shared/jpa/`（基类）+ `src/test/resources/db/test-fixtures.sql`（Testcontainers 种子）
- frontend：—（无 UI）

## 1. 模块边界 / 包结构

| 文件 / 目录 | 作用 |
|---|---|
| `db/migration/V{1..35}__*.sql` | Flyway 顺序迁移脚本（35 个版本：V1~V20 主干 + V21~V25 v1.2 DDL + V26/V27 跨模块 + V28 auth 三表 + V29 observability 元数据 + V30 outbox_events 三列 + V31 ai_jobs 状态机扩展 + V32 daily_reports.ai_summary CHECK + V33 outbox 事件枚举扩展 + V34 export 6 模块 + V35 chat_messages 回填） |
| `db/migration/R__repeatable_mviews.sql` | 物化视图定义（可重复执行） |
| `db/migration/U__undo_*.sql` | 回滚脚本（每个破坏性变更配套） |
| `shared/jpa/BaseEntity.java` | JPA 基类：id / created_at / updated_at / deleted_at |
| `shared/jpa/AuditListener.java` | JPA Listener：自动写 outbox 审计字段 |
| `test/resources/db/test-fixtures.sql` | Testcontainers 启动种子数据 |
| `test/resources/db/br-checks.sql` | 41 条 BR 约束校验脚本 |

## 2. 表清单（26 张主干 + v1.2 新增 5 张 + V26/V27 跨模块 2 张 + V28 auth 三表 3 张 + V29 observability 元数据 2 张 = 共 38 张）

### 2.1 公共基础设施（5 张）

| 表 | 关键 | 来源 |
|---|---|---|
| `users` | 账号体系（已就绪，V1 复用） | v1.1.1 |
| `user_profiles` | 1:1 用户资料 + Push/AI 开关（H-3 新增） | v1.1.1 |
| `push_subscriptions` | Web Push 多设备订阅 | v1.1.1 |
| `outbox_events` | 事务性 Outbox（带 `version` 字段） | v1.1.1 L-7 |
| `job_runs` | 异步 Job 运行记录 | v1.1.1 |

### 2.2 任务模块 task（5 张）

| 表 | 关键 |
|---|---|
| `tasks` | 一次性任务（含 `parent_id` 自循环 + 索引 `idx_tasks_user_priority_status`） |
| `task_tags` | 私有标签 |
| `task_tag_links` | 任务↔标签 N:M |
| `habits` | 习惯定义（frequency ∈ {DAILY, WEEKLY}） |
| `habit_logs` | 打卡记录（带补卡窗口校验） |

### 2.3 计划模块 plan（3 张）

| 表 | 关键 |
|---|---|
| `plans` | 长期目标（含 `last_activity_at`，BR-30 刷新） |
| `milestones` | 里程碑（含 `due_at_tz` 时区快照） |
| `milestone_task_links` | 里程碑↔任务 N:M |

### 2.4 日报模块 daily_report（3 张，按月分区 1 张）

| 表 | 关键 |
|---|---|
| `daily_reports` | **按月分区**，`content_md ≤ 50000`（M-1 / BR-25） |
| `daily_report_highlights` | ≤3 条 / 日报（BR-08） |
| `ai_summaries` | 1:1 日报，`daily_report_id NOT NULL`（BR-21） |

### 2.5 消费模块 expense（3 张，按月分区 1 张）

| 表 | 关键 |
|---|---|
| `expense_categories` | 系统 + 自定义 + 「其他」预置（BR-24） |
| `expenses` | **按月分区**，`amount_cents > 0`（BR-09） |
| `budgets` | 月度预算，`notify_enabled` + `notify_muted_until`（H-5） |

### 2.6 饮食模块 meal（3 张，按月分区 1 张）

| 表 | 关键 |
|---|---|
| `foods` | 食物库（`owner_user_id NULL=系统`） |
| `meals` | **按月分区**（H-2），`type ∈ {BREAKFAST, LUNCH, DINNER, SNACK}` |
| `meal_items` | 餐次条目（`servings > 0`，L-1 语义注释） |

### 2.7 AI 模块 ai（4 张，按月分区 1 张）

| 表 | 关键 |
|---|---|
| `ai_jobs` | 异步 Job（`attempts ≤ max_attempts`，M-2 / BR-28） |
| `ai_reports` | 已生成报告 |
| `chat_messages` | **按月分区**，30 天清理（BR-18 / BR-26），v1.2 加 `conversation_id` |
| `chat_feedbacks` | 点赞 / 点踩 |

### 2.8 v1.2 新增 5 张（P0 修订）

| 表 | 触发源 |
|---|---|
| `export_requests` | PRD DR-030/031, EXP-030, MEAL-030, AI-009 |
| `export_artifacts` | 配套导出产物 |
| `notification_requests` | 应用内通知请求 |
| `notification_deliveries` | 通知投递记录（含 Web Push 状态） |
| `conversations` | AI 会话聚合根（chat_messages 加 `conversation_id`） |

## 3. Flyway 迁移脚本序列 V1~V35（V28 归属 plan-auth；V33/V34/V35 顺序追加）

| 版本 | 内容 | 关键约束 |
|---|---|---|
| **V1** | `users`（约定复用） + `set_updated_at()` 触发器函数 | 全局基线 |
| **V2** | `user_profiles`（H-3）/ `push_subscriptions` / `outbox_events` / `job_runs` | BR-22 outbox.user_id NOT NULL + 部分索引 |
| **V3** | `tasks` / `task_tags` / `task_tag_links` / `habits` / `habit_logs` | BR-01/02/04/05/27 |
| **V4** | `plans` / `milestones` / `milestone_task_links` | BR-14/15/29 |
| **V5** | `daily_reports` / `daily_report_highlights` / `ai_summaries` | BR-06/07/08/21/25 |
| **V6** | `expense_categories` / `expenses` / `budgets` | BR-09/10/20/23/24 |
| **V7** | `foods` / `meals` / `meal_items` | BR-11/12/13 |
| **V8** | `ai_jobs` / `ai_reports` / `chat_messages` / `chat_feedbacks` | BR-17/26/28 |
| **V9** | 通用索引补齐（`idx_tasks_user_priority_status` 等 L-2） | 性能 |
| **V10** | BR 关键 CHECK 约束补齐（30 条，BR-01~BR-30 主干；v1.2 新增 11 条由 V25 落库） | 数据完整性 |
| **V11** | **5 个分区表改造**（`daily_reports` / `expenses` / `meals` / `chat_messages` / `outbox_events` 按月分区，H-1/H-2） | 分区 + 自动预建 3 月 + 保留 12 月 |
| **V12** | 物化视图 `mv_expense_monthly_category`（M-6） | CONCURRENTLY 刷新 |
| **V13** | 物化视图 `mv_meal_nutrition_weekly` | CONCURRENTLY 刷新 |
| **V14** | 通用种子数据：系统消费分类 / 默认食物 / 默认「其他」分类 | BR-24 |
| **V15** | 预留扩展位 | — |
| **V16~V20** | 预留扩展位（5 个版本） | — |
| **V21** | `export_requests`（P0-EXPORT-01） | v1.2 新增 |
| **V22** | `export_artifacts`（P0-EXPORT-02） | v1.2 新增 |
| **V23** | outbox_events event_type CHECK 扩展（`export.completed` / `export.failed` / `notification.requested`） | v1.2 事件枚举 |
| **V24** | `notification_requests`（P1-NOTIFY-01）+ `notification_deliveries`（P1-NOTIFY-02） | v1.2 新增 |
| **V25** | `conversations`（P1-CONV-01）+ `chat_messages` 加 `conversation_id`（P1-CONV-02）+ `ai_summaries` CHECK 收紧（P1-BR-21） | v1.2 表结构 + 约束 |
| **V26** | `operation_logs`（plan-shared-infra）— 鉴权 + 速率限制 + 审计横切 | v1.2 横切基础设施 |
| **V27** | `outbox_dead_letter`（plan-shared-integration）— Outbox 死信表 | v1.2 事件投递兜底 |
| **V28** | `refresh_tokens` + `email_verifications` + `password_resets`（plan-auth §3.2，X7）— Refresh rotation + 邮箱验证 + 密码重置 | auth 模块依赖前置 |
| **V29** | `scheduled_jobs` + `backup_manifests`（plan-observability-backup）— 调度任务元数据 + 备份产物清单 | observability 元数据 |
| **V30** | `outbox_events` 加列：`event_version` INTEGER NOT NULL DEFAULT 1 / `correlation_id` UUID NOT NULL DEFAULT gen_random_uuid() / `causation_id` UUID NULL（plan-shared-integration §3.1 链路追踪） | v1.2 Outbox 增强 |
| **V31** | `ai_jobs.status` CHECK 扩展：`DONE` / `FAILED` / `RUNNING` / `PENDING` / `CANCELLED` → 加入 `DONE_NO_LLM` / `DONE_PARTIAL`（plan-06-ai §2.4 PARTIAL 降级）+ V25 ai_summaries NOT NULL 部署前预检查 | v1.2 AI 状态机扩展 |
| **V32** | `daily_reports.ai_summary` NOT NULL CHECK（除 DRAFT 外）+ 部署前预检查（H5 / BR-21.b） | v1.2 daily 终态兜底 |
| **V33** | `outbox_events.event_type` CHECK 扩展：加入 4 条新事件（X7：auth.user.registered / auth.user.logged_in / auth.user.password_reset_requested / auth.token.reuse_detected）+ ai.job.completed 三态（DONE_NO_LLM / DONE_PARTIAL 走同 event_type 已含 DONE）+ 部署前预检查（原 V23 仅 3 条事件，不足覆盖新 4 条 → INSERT 直接被拒） | v1.2 事件枚举扩展 |
| **V34** | `export_requests.module` CHECK 扩 6 项（加 `task` `plan`，X2 决策 B）+ `export_requests.format` CHECK 扩 5 项（加 `json`）+ 部署前预检查（X2：原 V21 4+4 CHECK 拒 task/plan 导出与 json 格式） | v1.2.1 export 范围扩展 |
| **V35** | `chat_messages → conversations` 异步回填（P1-CONV-03，独立版本避免长事务，挪到 V35 避开与 plan-auth V28 三表冲突） | v1.2 数据回填 |

### 3.32 V30 — outbox_events 加列（链路追踪）

```sql
-- V30__outbox_events_add_tracing_columns.sql
-- 修订编号：C3
-- 关联：plan-shared-integration §3.1 event_version / correlation_id / causation_id
-- EventEnvelope.java 已含三字段，但 V1~V29 未落迁移会导致 JPA SchemaManagementException

ALTER TABLE outbox_events
    ADD COLUMN event_version  INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN correlation_id UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN causation_id   UUID        NULL;

-- 给已有 outbox_dead_letter 也补 event_version（一致性）
ALTER TABLE outbox_dead_letter
    ADD COLUMN event_version INTEGER;

CREATE INDEX idx_outbox_correlation ON outbox_events(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_outbox_causation   ON outbox_events(causation_id)   WHERE causation_id   IS NOT NULL;
```

### 3.33 V31 — ai_jobs 状态机扩展（PARTIAL 降级）

```sql
-- V31__ai_jobs_status_expand_partial.sql
-- 修订编号：C1
-- 关联：plan-06-ai §2.4 结构化报告 + PARTIAL 降级（业务 §6.6）
--
-- ⚠️ M2 部署前预检查（**必须**先执行，全部为 0 才可继续）：
-- SELECT COUNT(*) AS invalid FROM ai_summaries WHERE model_version IS NULL OR generated_at IS NULL;
--   期望结果：0（v1.2 收紧前的存量数据必须先回填，否则 V25 ai_summaries 已有 NOT NULL 会卡住启动）

ALTER TABLE ai_jobs DROP CONSTRAINT IF EXISTS ai_jobs_status_check;
ALTER TABLE ai_jobs
    ADD CONSTRAINT ai_jobs_status_check
    CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED',
                      'DONE_NO_LLM', 'DONE_PARTIAL'));

-- 注释：新增状态语义
-- DONE_NO_LLM  = 结构化数据完成，LLM 跳过（健康/超时/不同意）
-- DONE_PARTIAL = 任一源数据缺失，生成部分报告
COMMENT ON COLUMN ai_jobs.status IS 'PENDING/RUNNING/DONE/FAILED/CANCELLED/DONE_NO_LLM/DONE_PARTIAL；DONE_NO_LLM=LLM 跳过，DONE_PARTIAL=源数据缺失';
```

### 3.34 V32 — daily_reports.ai_summary NOT NULL CHECK（H5）

```sql
-- V32__daily_reports_ai_summary_not_null.sql
-- 修订编号：H5 / B4 / X1
-- 关联：plan-02-daily §3 daily_reports 字段 + BR-21.b
-- X1 修正：原 SQL 引用 status 列（daily_reports 表**没有**该列），改为加 is_draft 列；DRAFT 状态由应用层显式写入 is_draft=true 而非 status 字段
--
-- ⚠️ M2 部署前预检查（**必须**先执行，全部为 0 才可继续）：
-- SELECT COUNT(*) FROM daily_reports WHERE ai_summary IS NULL AND is_draft = FALSE;
--   期望结果：0（非 draft 终态日报必须有 ai_summary）
-- 如有存量 NULL 终态记录，先 UPDATE：
--   UPDATE daily_reports SET ai_summary = '(系统回填：原 ai_summary 缺失，请重新生成)' WHERE ai_summary IS NULL AND is_draft = FALSE;

ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS is_draft BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE daily_reports DROP CONSTRAINT IF EXISTS daily_reports_ai_summary_required;
ALTER TABLE daily_reports
    ADD CONSTRAINT daily_reports_ai_summary_required
    CHECK (ai_summary IS NOT NULL OR is_draft = TRUE);

CREATE INDEX idx_daily_reports_draft ON daily_reports(user_id, is_draft) WHERE is_draft = TRUE;

COMMENT ON COLUMN daily_reports.is_draft  IS '草稿态：TRUE 时允许 ai_summary NULL；提交后置 FALSE 必填（BR-21.b）';
COMMENT ON COLUMN daily_reports.ai_summary IS 'AI 摘要；非 draft 必填（BR-21.b）';
```

### 3.35 V33 — outbox_events event_type CHECK 扩展（X7 4 条 auth 事件）

```sql
-- V33__outbox_events_extend_event_type_check.sql
-- 修订编号：X7

-- 部署前预检查：现有 event_type 列表与 V33 目标列表比对
-- SELECT event_type, COUNT(*) FROM outbox_events GROUP BY 1 ORDER BY 1;
-- 确认无未知 event_type 后执行 DROP CONSTRAINT + ADD CONSTRAINT

ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_event_type_check;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        -- V23 已含（3 条）
        'export.completed',
        'export.failed',
        'notification.requested',
        -- V23 隐含已含（其余 16 条见 plan-shared-integration §4）
        'task.completed','task.reopened','task.created','task.updated',
        'milestone.created','milestone.updated','milestone.completed','milestone.missed',
        'habit.logged','daily_report.created','daily_report.updated','ai.summary.generated',
        'meal.created','expense.created','budget.threshold','plan.created',
        'ai.job.completed','ai.report.feedback',
        -- V33 新增（X7 4 条 auth 事件）
        'auth.user.registered',
        'auth.user.logged_in',
        'auth.user.password_reset_requested',
        'auth.token.reuse_detected'
    ));

COMMENT ON CONSTRAINT outbox_events_event_type_check ON outbox_events IS
    'V33 扩展 4 条 auth.* 事件（X7）；INSERT 事件名不在此 25 项内会被拒；
     ai.job.completed 三态（DONE / DONE_NO_LLM / DONE_PARTIAL）共用同一 event_type 已覆盖（X3）';
```

### 3.36 V34 — export_requests CHECK 扩展（X2 决策 B）

```sql
-- V34__export_requests_extend_module_format_check.sql
-- 修订编号：X2 决策 B

-- 部署前预检查：当前 module / format 实际使用分布，确认扩展后无脏数据
-- SELECT module, COUNT(*) FROM export_requests WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 1;
-- SELECT format, COUNT(*) FROM export_requests WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 1;
-- 预期现有数据均落在 4 模块 + 4 格式内（csv / markdown；zip / pdf 应无历史数据）

ALTER TABLE export_requests DROP CONSTRAINT IF EXISTS export_requests_module_check;
ALTER TABLE export_requests DROP CONSTRAINT IF EXISTS export_requests_format_check;

ALTER TABLE export_requests
    ADD CONSTRAINT export_requests_module_check
    CHECK (module IN ('task','daily_report','expense','meal','plan','ai'));

ALTER TABLE export_requests
    ADD CONSTRAINT export_requests_format_check
    CHECK (format IN ('csv','json','markdown','zip','pdf'));

COMMENT ON CONSTRAINT export_requests_module_check ON export_requests IS
    'V34 扩展：6 模块导出（task/daily_report/expense/meal/plan/ai）；v1.0 投产 6 模块（X2 决策 B，与 plan-export §5.1 / 验收项 §251 task/expense/plan 3 类导出跑通对齐）';
COMMENT ON CONSTRAINT export_requests_format_check ON export_requests IS
    'V34 扩展：5 格式（csv/json/markdown/zip/pdf）；v1.0 投产 csv/json/markdown，zip/pdf 预留 v1.1+';
```

## 4. 分区策略

| 分区表 | 分区键 | 分区粒度 | 自动维护 |
|---|---|---|---|
| `daily_reports` | `report_date` | 按月 | Job 预建下 3 月 + DROP 12 月前 |
| `expenses` | `occurred_at` | 按月 | 同上 |
| `meals` | `occurred_at` | 按月 | 同上 |
| `chat_messages` | `created_at` | 按月 | 同上 + 30 天物理清理（BR-18） |
| `outbox_events` | `occurred_at` | 按月 | 同上 + 30 天清理 |

- 命名：`{原表名}_YYYY_MM`（如 `daily_reports_2026_07`）
- 索引：每个分区自动继承父表索引
- 日终 Job（02:30）：`CREATE` 下月分区 + `DROP` 超龄分区（事务包裹）

## 5. 物化视图

| 物化视图 | 刷新策略 | 用途 |
|---|---|---|
| `mv_expense_monthly_category` | 每日 02:00 `REFRESH CONCURRENTLY` | PRD 03 §8 消费饼图性能 |
| `mv_meal_nutrition_weekly` | 每日 02:00 `REFRESH CONCURRENTLY` | PRD 04 §8 周营养聚合 |

- 定义放 `R__repeatable_mviews.sql`，可重复执行不冲突
- 唯一索引：`UNIQUE INDEX uq_mv_expense_user_month_category ON mv_expense_monthly_category(user_id, period_year, period_month, category_id)`（CONCURRENTLY 前置条件）
- 唯一索引：`UNIQUE INDEX uq_mv_meal_user_week ON mv_meal_nutrition_weekly(user_id, period_year, period_week)`（X5：CONCURRENTLY 前置条件；缺失时 `REFRESH MATERIALIZED VIEW CONCURRENTLY mv_meal_nutrition_weekly` 直接报错 "cannot refresh materialized view concurrently without unique index"，每日 02:00 刷新任务将持续失败）

## 6. 关键 BR 规则（30 条 + v1.2 新增 11 条 = 41 条）

> 完整 BR 列表见 `data-model-design-v1.1.1.md` §1.4，本节只列**容易在 Flyway 脚本中被遗漏的约束**

| BR | 表 | 落地 |
|---|---|---|
| BR-08 亮点 ≤3 | `daily_report_highlights` | `position ∈ 1..3` + UNIQUE |
| BR-16 Outbox 去重 | `outbox_events` | `UNIQUE (aggregate_type, aggregate_id, event_type)` |
| BR-21 摘要必挂日报 | `ai_summaries` | `daily_report_id NOT NULL` |
| BR-22 Outbox user_id NOT NULL | `outbox_events` | `user_id NOT NULL` + 部分索引 |
| BR-24 「其他」分类不可删 | `expense_categories` | `is_user_default` 不可改 + 应用层守卫 |
| BR-25 `content_md` ≤ 50000 | `daily_reports` | `CHECK (length(content_md) <= 50000)` |
| BR-26 `sql_executed` ≤ 10000 | `chat_messages` | `CHECK (length(sql_executed) <= 10000)` |
| BR-27 `tasks.parent_id` 自循环 | `tasks` | `CHECK (parent_id <> id)` |
| BR-28 `attempts ≤ max_attempts` | `ai_jobs` | `CHECK (attempts >= 0 AND attempts <= max_attempts)` |
| BR-30 `plans.last_activity_at` 刷新 | `plans` | Outbox 消费方更新（应用层） |
| BR-31~BR-41 | v1.2 新增（11 条） | 见 `data-model-v1.2-amendment.md` §1 |

## 7. Outbox 事件清单（25 条 = 13 业务 + 3 daily_report 补 + 2 task/milestone CU 拆 + 1 plan + 2 ai 补 + 2 export + 1 notification + 4 auth.* = 25 条）

| event_type | 触发源 | 消费方 |
|---|---|---|
| `task.completed` | `tasks.status → DONE` | plan |
| `task.reopened` | `tasks.status → OPEN` | plan |
| `task.created` | tasks INSERT（CU，**无 task.deleted**） | plan（BR-30 刷新 last_activity_at） |
| `task.updated` | tasks UPDATE | plan |
| `milestone.created` | milestones INSERT（CU，**无 milestone.deleted**） | plan（BR-30） |
| `milestone.updated` | milestones UPDATE | plan |
| `milestone.completed` | milestones.status → DONE | ai, daily_report |
| `milestone.missed` | MissedMilestoneJob | ai |
| `habit.logged` | habit_logs INSERT | daily_report, ai |
| `meal.created` | meals INSERT | ai |
| `expense.created` | expenses INSERT | ai, **notify**（X7：原 push_subscriptions 直接消费已废弃，统一走 notify 模块；plan-shared-integration §4 为权威） |
| `budget.threshold` | BudgetEvaluatorJob | **notify**（X7） |
| `plan.created` | plans INSERT | ai |
| `ai.job.completed` | ai_jobs.status → **DONE / DONE_NO_LLM / DONE_PARTIAL**（X3：三态均触发） | user（SSE） + notify |
| `ai.report.feedback` | chat_feedbacks INSERT | — |
| **`export.completed`** | export_artifacts INSERT | user |
| **`export.failed`** | export_requests.status → FAILED | user |
| **`notification.requested`** | notification_requests INSERT | notification_deliveries |
| **`auth.user.registered`** | users INSERT（auth 模块，X7） | notify + ai |
| **`auth.user.logged_in`** | refresh_tokens 写入（auth 模块，X7） | notify（异地登录告警） |
| **`auth.user.password_reset_requested`** | password_resets INSERT（auth 模块，X7） | notify（邮件 + Web Push 双通道） |
| **`auth.token.reuse_detected`** | JwtRefreshService 检测到 reuse（auth 模块，X7） | notify + JwtRefreshTokenService.revokeFamily |

## 8. 关键验收场景（TDD 种子）

### 迁移完整性

- `flyway_should_apply_all_migrations_clean`：Testcontainers 启动 → V1~V35 全部成功
- `flyway_should_reject_duplicate_version`：版本号冲突时报错
- `flyway_should_compute_checksum`：checksum 改动时校验失败
- `flyway_should_reject_subversion_format`：禁止 `V24_1` / `V25_2` 等带下划线子版本号（Flyway 不支持，会被解析为 V241/V252）

### 结构正确性

- `flyway_should_create_38_tables`：表数核对（26 业务主干 + 5 v1.2 新增 + 2 V26/V27 跨模块 + 3 V28 auth + 2 V29 observability）
- `flyway_should_use_bigint_identity`：所有主键为 `BIGINT GENERATED ALWAYS AS IDENTITY`
- `flyway_should_set_updated_at_trigger`：UPDATE 自动刷新 `updated_at`
- `flyway_should_partition_5_tables`：5 个分区表存在且分区命名规范
- `flyway_should_create_2_materialized_views`：2 个物化视图存在且唯一索引就绪

### 约束正确性

- `flyway_should_enforce_br_unique_constraints`：BR-06 / BR-10 / BR-16 / BR-23 抽查
- `flyway_should_enforce_br_check_constraints`：BR-01/02/04/07/09/11/12/13/15/25/26/27/28 抽查
- `flyway_should_enforce_br_not_null`：BR-21/22 抽查
- `flyway_should_enforce_soft_delete_nullable`：`deleted_at` 可空

### v1.2 修订

- `flyway_should_have_7_new_tables`：V21/V22/V24/V25 创建 5 张 v1.2 表 + V29 创建 2 张 observability 元数据（scheduled_jobs / backup_manifests）
- `flyway_should_have_chat_messages_conversation_id`：V25 加列成功
- `flyway_should_have_3_new_outbox_events`：V23 事件清单补齐（export.completed / export.failed / notification.requested）
- `flyway_should_have_ai_summaries_not_null`：V25 收紧 ai_summaries.model_version + generated_at NOT NULL
- `flyway_should_backfill_async`：V35 回填脚本独立异步执行（不阻塞 V25 DDL 事务；V26/V27 已被 plan-shared-infra / plan-shared-integration 占用；V28 已归属 plan-auth 三表）

## 9. 验收标准

- [ ] V1~V35 全部执行成功（无 checksum 错误；V26/V27 已被其他模块占用；V30 加列 + V31/V32 CHECK 扩展均幂等；V33 事件枚举扩展；V34 export 6 模块 CHECK；V35 chat_messages 异步回填）
- [ ] 38 张表全部就位
- [ ] 5 个分区表分区正确（每月一个分区，自动预建 3 月）
- [ ] 2 个物化视图存在且 CONCURRENTLY 刷新成功
- [ ] 41 条 BR 全覆盖（30 旧 + 11 新）
- [ ] Testcontainers 集成测试通过
- [ ] Repository 层测试覆盖率 ≥ 80%
- [ ] 关键路径 100% 覆盖（auth / 金额计算 / outbox 写入）

## 10. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| Flyway 迁移失败阻塞启动 | 高 | CI 跑 Testcontainers + 本地 `mvn flyway:validate` dry-run |
| 分区表查询性能 | 中 | 双区（user_id + 时间）+ 索引覆盖（V9） |
| 物化视图刷新阻塞读 | 低 | 唯一索引 + CONCURRENTLY（凌晨 02:00） |
| outbox_events 表膨胀 | 中 | 日终 Job DROP 30 天前分区（BR-19 / BR-22） |
| chat_messages 30 天清理（BR-18） | 中 | 分区 DROP 而非 DELETE |
| 大版本切换（v1.1 → v1.2）数据兼容 | 中 | V21~V25 + V28 + V29 + V33~V35 全部 `ALTER TABLE ADD COLUMN NULL` 或新建表，零破坏 |
| seed 数据与单元测试冲突 | 低 | seed 走 `test-fixtures.sql`，prod 走 V14 且幂等 |

## 11. 关联文档

- 上游：`plan-deploy-nginx.md`（db 容器就绪）
- 下游：
  - `plan-shared-infra.md`（基于 user / user_profiles 鉴权）
  - `plan-shared-integration.md`（outbox_events + outbox 事件投递器）
  - `plan-auth.md`（users / refresh_tokens 复用）
  - `plan-01-task.md` ~ `plan-06-ai.md`（6 模块 Repository）
  - `plan-observability-backup.md`（pg_dump 备份对象）