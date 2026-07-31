# plan-observability-backup 实施方案（v1.0 收口阶段）

## 参考资料

- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §6 监控告警 + §7 灾备
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §6 运维（5+1 容器 / 备份策略）
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V26（operation_logs）+ V27（outbox_dead_letter）
- [`deploy/`](../../deploy/) — 备份 / 迁移 / 运维脚本目录
- `CLAUDE.md` §7 安全 + §10 红线（备份与密钥）

## 参考目录

- backend：`app/src/main/java/com/lifewise/observability/`
  - `controller/` — HealthController（自定义健康检查端点）
  - `service/` — OperationLogService / ScheduledJobTracker / BackupService / AuditArchiveService
  - `job/` — MaterializedViewRefreshJob / OutboxDeliveryJob / AuditArchiveJob / PushSubscriptionCleanupJob / BackupJob
  - `domain/` — ScheduledJob / BackupManifest
  - `repository/` — ScheduledJobRepository / BackupManifestRepository
  - `health/` — RedisHealthIndicator / OllamaHealthIndicator / DatabaseHealthIndicator
  - `config/` — PrometheusConfig / BackupProperties
- infra：
  - `deploy/backup/` — `pg_dump.sh` / `restore.sh` / `.pgpass` 模板
  - `deploy/cron/` — `crontab` 模板（与 BackupJob 双保险）
  - `deploy/monitoring/` — `prometheus.yml` / `alert.rules.yml`
  - `nginx/conf/` — `/actuator/*` 内部限流配置
- frontend：`docs/lifewise/designs/`
  - `health-ui.html` — 系统状态页（可选 v1.1+）

## 1. 模块边界 / 包结构

observability + backup 是**横切 + 收口**模块，覆盖运行时可见性、灾备、调度任务跟踪。本文件复用 plan-shared-infra 的 operation_logs（V26）+ plan-shared-integration 的 outbox_dead_letter（V27），新增 2 张元数据表。

```
observability/
├── controller/
│   └── HealthController.java          GET /api/system/health（综合健康）
├── service/
│   ├── OperationLogService.java       异步写 operation_logs（V26）
│   ├── ScheduledJobTracker.java       @Scheduled Job 执行元数据（last_run/status）
│   ├── BackupService.java             触发 + 校验 pg_dump 产物
│   └── AuditArchiveService.java       归档 chat_messages（role=SYSTEM）审计消息到冷存储
├── job/
│   ├── MaterializedViewRefreshJob.java    每日 02:00 / 02:30 刷 mv_expense / mv_meal
│   ├── MissedMilestoneJob.java            每日 03:30（plan-05-plan 已定义，此处只调度）
│   ├── BackupJob.java                     每日 03:00 触发 pg_dump（双保险：与 cron 同步）
│   ├── OutboxDeliveryJob.java             每分钟轮询 outbox_events PENDING
│   ├── OutboxDeadLetterJob.java           每日 04:00 清理超 7 天 DLQ
│   ├── AuditArchiveJob.java               每月 1 日归档上月的 chat_messages（role=SYSTEM）
│   └── PushSubscriptionCleanupJob.java    每日清理过期 push_subscriptions
├── domain/
│   ├── ScheduledJob.java             scheduled_jobs 元数据表
│   └── BackupManifest.java           backup_manifests 元数据表
├── repository/
│   ├── ScheduledJobRepository.java
│   └── BackupManifestRepository.java
├── health/
│   ├── RedisHealthIndicator.java       /actuator/health/redis
│   ├── OllamaHealthIndicator.java      /actuator/health/ollama
│   └── DatabaseHealthIndicator.java    /actuator/health/db
└── config/
    ├── PrometheusConfig.java           暴露 /actuator/prometheus
    └── BackupProperties.java           备份保留天数/路径/PG 凭据来源
```

## 2. API 契约（运维接口）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/actuator/health` | — | `{status: UP/DOWN, components: {...}}` | — |
| GET | `/actuator/prometheus` | — | Prometheus 文本格式 metrics | — |
| GET | `/actuator/info` | — | build/git/版本信息 | — |
| GET | `/api/system/health` | — | `{data: {db, redis, ollama, backup_last_at, outbox_pending_count}}` | — |
| GET | `/api/system/jobs` | — | `{data: ScheduledJobView[]}` | — |
| POST | `/api/system/backup/trigger` | — | `{data: {manifest_id, size_bytes}}` | `BACKUP_FAILED` |

注意：`/actuator/*` 走 nginx 内部子网白名单（仅 127.0.0.1 / 监控网段可访问），**不暴露公网**。

## 3. 数据模型（V26 复用 + V27 复用 + 新增 2 表）

### 3.1 复用

- `operation_logs`（V26，plan-shared-infra）— `@Auditable` 自动写
- `outbox_dead_letter`（V27，plan-shared-integration）— 超 7 天未投递的事件

### 3.2 新增 scheduled_jobs（V29 本文件引入）

```sql
CREATE TABLE scheduled_jobs (
    name                TEXT PRIMARY KEY,           -- 'materialized_view_refresh' 等
    cron_expression     TEXT NOT NULL,             -- '0 0 2 * * *'
    last_run_at         TIMESTAMPTZ,
    last_status         TEXT,                       -- SUCCESS / FAILED / RUNNING
    last_error_message  TEXT,
    next_run_at         TIMESTAMPTZ,
    avg_duration_ms     INT,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 3.3 新增 backup_manifests（V29 本文件引入）

```sql
CREATE TABLE backup_manifests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    backup_type         TEXT NOT NULL,             -- 'pg_dump_full' / 'audit_archive'
    file_path           TEXT NOT NULL,
    file_size_bytes     BIGINT,
    sha256              TEXT,                      -- 文件校验
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    status              TEXT,                      -- RUNNING / SUCCESS / FAILED
    error_message       TEXT,
    retention_until     TIMESTAMPTZ,               -- 保留到期时间
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_backup_manifests_type_created ON backup_manifests(backup_type, created_at DESC);
CREATE INDEX idx_backup_manifests_retention ON backup_manifests(retention_until) WHERE status = 'SUCCESS';
```

## 4. 调度任务清单（13 个 @Scheduled Job）

| Job | Cron | 触发 | 失败处理 | 归属 |
|---|---|---|---|---|
| `MaterializedViewRefreshJob` | `0 0 2 * * *` | `REFRESH MATERIALIZED VIEW CONCURRENTLY mv_expense_monthly_category` | 失败重试 3 次 + 告警 | observability |
| `MaterializedViewRefreshJob.meal` | `0 30 2 * * *` | `REFRESH MATERIALIZED VIEW CONCURRENTLY mv_meal_nutrition_weekly` | 失败重试 3 次 + 告警 | observability |
| `EnsurePartitionJob` | `0 30 1 * * *` | 预建下 3 月分区 + DROP 超龄分区（5 个分区表：daily_reports / expenses / meals / chat_messages / outbox_events） | 失败告警（事务包裹） | observability |
| `MissedMilestoneJob` | `0 30 3 * * *` | 标记到期 milestone → MISSED（plan-05-plan） | 失败重试 3 次 + 告警 | plan |
| `BackupJob` | `0 0 3 * * *` | 调 `deploy/backup/pg_dump.sh` + 写 backup_manifests | 失败重试 + 告警（**双保险**：deploy/cron 也跑） | observability |
| `OutboxDeliveryJob` | `0 * * * * *`（每分钟） | 扫描 outbox_events PENDING → 投递 | 失败 → outbox_dead_letter（V27） | shared-integration |
| `OutboxDeadLetterJob` | `0 0 4 * * *` | 删除超 7 天 DLQ 记录 | 失败告警 | shared-integration |
| `AuditArchiveJob` | `0 0 0 1 * *`（每月 1 日） | 归档上月 chat_messages（role=SYSTEM）→ 冷存储 | 失败重试 + 告警 | observability |
| `PushSubscriptionCleanupJob` | `0 0 4 * * *` | 删除 expires_at < NOW() - 30 天的 push_subscriptions | 失败告警 | observability |
| `HabitMissedJob` | `0 0 0 * * *` | 扫描 habits（frequency=DAILY/WEEKLY）+ 检查昨日/上周 habit_logs 缺失 → 写 `habit.logged` 缺失事件 | 失败重试 3 次 | task |
| `PlanStaleNotifyJob` | `0 0 9 * * *` | 扫描 plans（last_activity_at < NOW() - 14 days, status=ACTIVE）→ 推送 Web Push 提醒（PRD 05 §BR-30） | 失败告警 | plan |
| `PurgeSoftDeletedJob` | `0 30 4 * * *` | 删除 `deleted_at < NOW() - 90 days` 的软删记录（tasks / plans / milestones / expenses / meals / habits 等所有带 `deleted_at` 的表） | 失败告警 | observability |
| `PurgeChatMessagesJob` | `0 30 3 * * *` | DROP 超 30 天的 chat_messages / outbox_events 分区（BR-18 / BR-22） | 失败告警 | observability |

每个 Job 启动时更新 `scheduled_jobs.last_run_at = NOW, last_status = RUNNING`，结束更新为 SUCCESS/FAILED。

> **补齐说明（与 architecture §6.6 对齐）**：v1.2 PRD 中明确定义了 5 个调度任务未在原 v1.0 调度清单中——`HabitMissedJob`（task）/ `PlanStaleNotifyJob`（plan）/ `EnsurePartitionJob`（observability）/ `PurgeSoftDeletedJob`（observability）/ `PurgeChatMessagesJob`（observability）。本文件 §4 已补齐。预算评估由 `expense.created` 事件驱动（plan-03-expense §1），不在 cron 调度清单内。
>
> **H2 cron 错峰（避免锁竞争）**：`PurgeChatMessagesJob` 原计划 02:30 与 `MaterializedViewRefreshJob.meal`（02:30）同时跑——两者都要对 `chat_messages` / `outbox_events` 持 ACCESS EXCLUSIVE 锁（DROP PARTITION + MV REFRESH），并发会触发 lock timeout 并连锁阻塞 03:00 pg_dump（业务 RPO 24h 风险）。**已调整为 03:30**，与 03:00 BackupJob + 03:30 MissedMilestoneJob 错峰；MV REFRESH 仍在 02:00/02:30 不变。
>
> **M5 cron 错峰（02:00 新冲突修复）**：`PurgeSoftDeletedJob` 原计划 02:00 与 `MaterializedViewRefreshJob`（02:00）并发——DELETE 软删记录虽不带分区级 ACCESS EXCLUSIVE，但批量 UPDATE/DELETE 会持行锁 + 索引扫描，02:00 业务低峰期同时跑两个 Job 会拖慢 MV REFRESH。**已调整为 04:30**，与 04:00 PushSubscriptionCleanupJob / 04:00 OutboxDeadLetterJob 完全错峰。

## 5. 备份策略

### 5.1 PostgreSQL pg_dump

- **频率**：每日 03:00
- **工具**：`prodrigestivill/postgres-backup-local`（CLAUDE.md §2.3 指定）+ 自研 `pg_dump.sh` 双写
- **保留**：7 天滚动（容器内）+ 30 天滚动（宿主机副本，deploy/cron 同步）
- **校验**：写 backup_manifests.sha256 + 启动时校验最近一次备份完整性
- **路径**：`/backups/pgdump-lifewise-{YYYYMMDD-HHMM}.sql.gz`
- **凭据**：`.env` 注入 `POSTGRES_USER` / `POSTGRES_PASSWORD` / `PGPASSFILE`

### 5.2 Audit 日志归档

- 频率：每月 1 日 00:00
- 操作：把上月的 chat_messages（WHERE role='SYSTEM'）复制到 `/backups/audit-archive-{YYYYMM}.json.gz`，从主库删除（保留 30 天在线）
- 目的：长期合规审计 + 主库性能

### 5.3 Redis 持久化

- 启用 AOF + RDB（容器默认）
- 备份作为缓存层，不参与 pg_dump

### 5.4 恢复演练

- 每季度一次：随机抽取一次备份 → `pg_restore` 到临时库 → 关键表行数对比
- 记录 RPO（24h）/ RTO（< 30min）实测值

## 6. 监控与告警

### 6.1 Prometheus 指标

| 指标 | 类型 | 来源 |
|---|---|---|
| `jvm_memory_used_bytes{area="heap"}` | Gauge | Spring Actuator |
| `hikaricp_connections_active` | Gauge | HikariCP |
| `http_server_requests_seconds_count{uri}` | Counter | Spring Actuator |
| `outbox_events_pending_total` | Gauge | OutboxDeliveryJob 每分钟更新 |
| `ai_jobs_running_count` | Gauge | ai 模块 |
| `chat_messages_system_inserted_total` | Counter | ai 模块（role=SYSTEM 审计消息） |
| `budget_threshold_triggered_total` | Counter | expense 模块 |
| `materialized_view_refresh_duration_seconds` | Histogram | 本模块 |
| `backup_last_success_timestamp_seconds` | Gauge | 本模块 |
| `ollama_up` | Gauge | OllamaHealthIndicator |

### 6.2 告警规则（prometheus alert.rules.yml）

| 规则 | 触发条件 | 严重度 |
|---|---|---|
| `BackupJobFailed` | 连续 2 天 backup_manifests.status = FAILED | critical |
| `OutboxPendingHigh` | outbox_events_pending > 1000 持续 5min | warning |
| `AiJobsStuck` | ai_jobs.RUNNING 超过 5min 未结束 | warning |
| `OllamaDown` | ollama_up = 0 持续 2min | critical |
| `DiskSpaceLow` | 备份磁盘剩余 < 20% | warning |
| `HighErrorRate` | http_server_requests_seconds_count{status="5xx"} > 1% | warning |
| `LoginFlooding` | 同一 IP 1min 内 auth.login 失败 > 10 次 | critical |

### 6.3 健康检查端点

```java
// RedisHealthIndicator
@Override
protected void doHealthCheck(Health.Builder builder) {
    try {
        String pong = redisTemplate.execute((RedisCallback<String>) conn -> conn.ping());
        builder.up().withDetail("ping", pong);
    } catch (Exception e) {
        builder.down(e);
    }
}

// OllamaHealthIndicator（business §6.6：30s 主动探测 + 连续 2 次失败置红色态）

```java
@Component("ollama")
public class OllamaHealthIndicator implements HealthIndicator {
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final RestTemplate restTemplate;
    private final String ollamaUrl;

    @Scheduled(fixedRate = 30_000)   // 30s 探测一次
    public void probe() {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                ollamaUrl + "/api/tags", String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                consecutiveFailures.set(0);   // 成功重置
            } else {
                consecutiveFailures.incrementAndGet();
            }
        } catch (Exception e) {
            consecutiveFailures.incrementAndGet();
        }
    }

    @Override
    public Health health() {
        int fails = consecutiveFailures.get();
        if (fails == 0) {
            return Health.up().withDetail("consecutive_failures", 0).build();
        }
        if (fails < 2) {
            return Health.up()                                  // 1 次失败仍 UP（容忍瞬时抖动）
                .withDetail("consecutive_failures", fails)
                .withDetail("state", "YELLOW")
                .build();
        }
        // 连续 ≥2 次失败 → DOWN（红色态）
        return Health.down()
            .withDetail("consecutive_failures", fails)
            .withDetail("state", "RED")
            .withDetail("impact", "ai_jobs WILL SKIP LLM (DONE_NO_LLM); chat LLM path returns AI_UNAVAILABLE")
            .build();
    }
}
```

**红色态业务行为**（business §6.6）：
- `POST /api/ai/reports/generate` 仍受理 → 创建 PENDING ai_jobs → 优先尝试结构化数据；**不创建 LLM 作业**，最终 status=`DONE_NO_LLM`
- `POST /api/ai/chat` 规则路径继续可用；LLM 路径返回 `AI_UNAVAILABLE` + 提示降级
- 恢复后下一次 30s 探测成功 → 立即转 UP → LLM 路径恢复

**Prometheus 指标**：`ollama_up`（0/1）+ `ollama_consecutive_failures` Gauge，告警规则 `OllamaDown = ollama_up == 0 持续 2min`。

## 7. 关键验收场景（TDD 种子）

### 7.1 ScheduledJob 元数据

- `job_should_update_last_run_at_on_start`：Job 开始 → scheduled_jobs.last_run_at = NOW, status = RUNNING
- `job_should_record_success`：Job 成功 → status = SUCCESS, last_error_message = null
- `job_should_record_failure_with_error`：Job 抛异常 → status = FAILED + 异常 message
- `job_should_calculate_avg_duration`：连续 5 次执行 → avg_duration_ms 更新

### 7.2 MaterializedViewRefreshJob

- `mv_job_should_refresh_expense_view`：执行后 `mv_expense_monthly_category` 数据已更新
- `mv_job_should_refresh_meal_view`：执行后 `mv_meal_nutrition_weekly` 已更新
- `mv_job_should_use_concurrently`：REFRESH CONCURRENTLY 不阻塞读
- `mv_job_should_retry_on_failure`：失败重试 3 次
- `mv_job_should_alert_after_3_failures`：3 次全败 → Prometheus alert

### 7.3 BackupJob

- `backup_job_should_run_pg_dump`：执行后 `/backups/pgdump-lifewise-*.sql.gz` 存在
- `backup_job_should_write_manifest`：写 backup_manifests + sha256
- `backup_job_should_handle_pgpass_missing`：缺 .pgpass → 失败告警（不静默）
- `backup_job_should_cleanup_old_files`：retention_until < NOW → 删除
- `backup_job_should_validate_restore`：季度演练接口（手动触发）

### 7.4 OutboxDeliveryJob

- `outbox_should_deliver_pending`：每分钟扫描 PENDING → 调用订阅者
- `outbox_should_mark_dead_letter_on_retry_exhausted`：重试 > 3 → outbox_dead_letter（F1：plan-shared-integration §5.1 + §7.4 定调 3 次重试 1s/5s/30s，原文 > 5 为 typo）
- `outbox_should_skip_when_no_pending`：无 PENDING → 不报错
- `outbox_should_record_in_pending_metric`：outbox_events_pending_total 准确

### 7.5 HealthIndicator

- `redis_health_should_be_up_on_ping`：Redis 在线 → UP
- `redis_health_should_be_down_on_timeout`：timeout → DOWN
- `ollama_health_should_check_api_tags`：HTTP 200 → UP, 5xx → DOWN
- `db_health_should_check_connection`：SELECT 1 → UP

### 7.6 AuditArchiveJob

- `archive_should_copy_last_month_records`：复制上月 chat_messages（role=SYSTEM）→ JSON.gz
- `archive_should_delete_after_copy`：主库软删/物理删
- `archive_should_verify_row_count`：复制前后行数对比

### 7.7 UI / API（运维）

- `api_system_health_should_return_components`：db / redis / ollama / backup / outbox
- `api_system_jobs_should_list_all_jobs`：列出全部 scheduled_jobs 元数据
- `api_backup_trigger_should_require_admin`：admin 角色（CLAUDE.md §7.3）

## 8. 验收标准

- [ ] Spring Actuator `/actuator/health` + `/actuator/prometheus` 跑通
- [ ] 13 个 @Scheduled Job 全部按 cron 跑通（含 OutboxDeliveryJob 每分钟）
- [ ] 2 张元数据表（scheduled_jobs + backup_manifests）Repository 单测覆盖率 ≥ 85%
- [ ] pg_dump 每日 03:00 成功 + 7 天滚动 + 校验 sha256
- [ ] 双保险（BackupJob + deploy/cron）至少一个成功
- [ ] Prometheus 10+ 指标暴露 + 7 条告警规则就位
- [ ] Redis / Ollama / DB 三个 HealthIndicator 跑通（Ollama 三态：GREEN=连续 0 次失败 / YELLOW=连续 1 次失败容忍瞬时抖动 / RED=连续 ≥2 次失败触发业务降级，详见 §6.4 红色态行为 + plan-06-ai §2.4 Ollama 健康探测）
- [ ] 关键路径 100% 覆盖（备份 / 物化视图 / outbox 投递 / 健康检查）
- [ ] 季度恢复演练文档化
- [ ] 灾备 RPO ≤ 24h / RTO ≤ 30min 实测达标

## 9. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 备份磁盘满 | 高 | retention_until 自动清理 + 监控告警 + 宿主机副本 |
| Ollama 容器宕机 AI 报告堆积 | 中 | OllamaHealthIndicator + ai_jobs FAILED 兜底 |
| outbox 堆积（订阅方宕机） | 中 | DLQ 兜底 + 告警 + 重试退避 |
| 备份脚本 silent failure | 高 | 必须校验 sha256 + manifest 写入 + 失败告警（CLAUDE.md §10） |
| scheduled_jobs 元数据不一致 | 中 | Job 启动/结束同一事务 + audit log |
| 误触发手动 backup 占用磁盘 | 低 | admin 鉴权 + 自动清理 |
| 恢复演练失败未发现 | 中 | 季度强制演练 + 记录 RTO 实测 |
| 监控指标过多导致 Prometheus 性能 | 低 | 只暴露 10+ 关键指标 + histogram 桶收敛 |

## 10. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（nginx 内部子网白名单 + `/actuator` 路由）
  - `plan-data-flyway.md`（V26 + V27 复用）
  - `plan-shared-infra.md`（operation_logs 复用 + @Auditable）
  - `plan-shared-integration.md`（outbox_dead_letter 复用）
  - `plan-auth.md`（admin 角色 + JWT 来源）
  - `plan-01-task.md`（schedule 事件消费）
  - `plan-02-daily.md`（物化视图统计 + ai.summary.generated）
  - `plan-03-expense.md`（物化视图 `mv_expense_monthly_category`）
  - `plan-04-diet.md`（物化视图 `mv_meal_nutrition_weekly`）
  - `plan-05-plan.md`（MissedMilestoneJob + last_activity_at 监控）
  - `plan-06-ai.md`（ai_jobs 状态 + chat_messages role=SYSTEM 审计归档 + Ollama 健康）
- 下游：
  - **无**（v1.0 项目交付收口）
  - 后续 v1.1+ 跨模块洞察 / 通知整合以此为基线