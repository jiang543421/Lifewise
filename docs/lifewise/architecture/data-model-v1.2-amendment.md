# 数据模型 v1.2 修订清单

> 文档代号：`data-model-v1.2-amendment`
> 版本：v1.2（基于 v1.1.1）
> 修订日期：2026-07-26
> 修订人：架构组
> 修订依据：三套文档对照分析（`check` 分支）
> 配套 Flyway 脚本：`V21__*.sql` ~ `V25__*.sql`（本文档权威范围）+ `V26~V29__*.sql`（plan-data-flyway.md 扩展）
> - **V21~V25**：本文档定义（同步 DDL — 表创建 / 列变更 / CHECK 扩展 / CHECK 收紧）
> - **V26~V29**：以 [`plan-data-flyway.md`](../planning/plan-data-flyway.md) §3 为权威（跨模块 + observability 元数据 + 回填）
>   - V26 = `operation_logs`（plan-shared-infra）
>   - V27 = `outbox_dead_letter`（plan-shared-integration）
>   - V28 = `chat_messages → conversations` 异步回填（**原本文档 V26 顺延**）
>   - V29 = `scheduled_jobs` + `backup_manifests`（plan-observability-backup）
> - **V30/V31**：以 [`plan-data-flyway.md`](../planning/plan-data-flyway.md) §3.32 / §3.33 为权威（v1.2 收口新增）
>   - V30 = `outbox_events` 加列（event_version / correlation_id / causation_id）
>   - V31 = `ai_jobs.status` CHECK 扩展（DONE_NO_LLM / DONE_PARTIAL）

> **修订说明**：本文档原草案使用 `V24_1 / V24_2 / V25_1 ~ V25_4` 子版本号，但 Flyway **不支持** `V24_1` 这类带下划线子版本号（会被解析为全新版本 `V241`），落地时统一合并到主版本号（V24 / V25 / V26）。

### 0.0 主键策略（v1.2 显式声明）

本修订与 v1.1.1 一致：

- **主键类型**：`BIGINT GENERATED ALWAYS AS IDENTITY`（PG 10+ 标准，取代 `SERIAL`）
- **跨表引用**：所有外键统一 `BIGINT`
- **不使用 UUID**：v1.1.1 ~ v1.2 显式选择 `BIGINT` 而非 UUID
- **UUID 演进路径**：UUID 主键（含 `gen_random_uuid()`）列入 v2.0 候选方向；v1.2 不切换以保持与现有 26 张表的最大兼容性

新人在看到 `BIGINT` 主键时如有"为什么不用 UUID"的疑问，请参考本节：当前阶段数据规模 ≤ 1 万用户/年（见 data-model v1.1.1 §8），`BIGINT` 足够；UUID 的优势（去中心化生成、跨系统合并友好）在当前单体架构下未充分体现；如未来要拆服务或上多区域，再统一切换 UUID。

---

## 0. 本次修订范围速览

| 类型 | 数量 | 对象 |
|---|---|---|
| 新增表 | 5 | `export_requests` / `export_artifacts` / `notification_requests` / `notification_deliveries` / `conversations` |
| 表结构修改 | 1 | `chat_messages`（加 `conversation_id`） |
| Outbox 事件补录 | 3 | `export.completed` / `export.failed` / `notification.requested` |
| BR 新增/修改 | 11 | BR-21 追加；BR-31 ~ BR-43 新增 |
| 业务架构实体补齐 | 5 | EXPORT_REQUEST / EXPORT_ARTIFACT / NOTIFICATION_REQUEST / NOTIFICATION_DELIVERY / CONVERSATION |

---

## 1. P0 修订

### 1.1 新增 `export_requests`

**Flyway**：`V21__create_export_requests.sql`

```sql
-- ============================================================
-- V21__create_export_requests.sql
-- 导出请求表（多模块 / 多格式 / 异步作业）
-- 修订编号：P0-EXPORT-01
-- 关联业务架构：§4.1 导出与数据携带 / §6.9 流程 9 / §3.1 EXPORT_REQUEST
-- 关联 PRD：DR-030/031, EXP-030, MEAL-030, AI-009
-- ============================================================

CREATE TABLE export_requests (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 租户与归属
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 导出源模块（X2 决策 B：扩 6 模块；task/plan 由 V34 迁移扩展，v1.0 首版仅 4 模块上线，V34 在 v1.0.1 增量升级）
    module          TEXT NOT NULL
                    CHECK (module IN ('task','daily_report','expense','meal','plan','ai')),

    -- 导出格式（X2 决策 B：扩 5 格式；json 由 V34 迁移扩展）
    format          TEXT NOT NULL
                    CHECK (format IN ('csv','json','markdown','zip','pdf')),

    -- 筛选参数（日期范围 / 分类 / 心情区间 等自由结构）
    filters         JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 状态机：pending → processing → done / failed / cancelled
    status          TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PROCESSING','DONE','FAILED','CANCELLED')),

    -- 进度
    progress        INT NOT NULL DEFAULT 0
                    CHECK (progress BETWEEN 0 AND 100),

    -- 重试
    attempts     INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts     INT NOT NULL DEFAULT 3
                    CHECK (max_attempts BETWEEN 1 AND 10),

    -- 错误信息
    error           TEXT NULL,
    last_error_at   TIMESTAMPTZ NULL,

    -- 产物统计（done 时为实际生成文件数）
    artifact_count  INT NOT NULL DEFAULT 0 CHECK (artifact_count >= 0),

    -- 时间戳
    scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ NULL,
    finished_at     TIMESTAMPTZ NULL,

    -- 下载过期：done 后 7 天（应用层常量 EXPORT_ARTIFACT_TTL_DAYS）
    expires_at      TIMESTAMPTZ NULL,

    -- 幂等键：客户端去重
    idempotency_key TEXT NULL,

    -- 通用审计
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ NULL,

    -- BR-31：status=DONE 时 finished_at + expires_at 必填且 expires_at > finished_at
    CHECK ((status <> 'DONE') OR
           (finished_at IS NOT NULL AND expires_at IS NOT NULL
            AND expires_at > finished_at)),

    -- BR-32：status=FAILED/CANCELLED 时 finished_at 必填
    CHECK ((status NOT IN ('FAILED','CANCELLED')) OR (finished_at IS NOT NULL)),

    -- BR-33：attempts <= max_attempts
    CHECK (attempts <= max_attempts),

    -- BR-34：status=DONE 时 artifact_count >= 1
    CHECK (status <> 'DONE' OR artifact_count >= 1),

    -- 幂等键全局唯一（包含 NULL，PostgreSQL UNIQUE 允许多个 NULL）
    UNIQUE (user_id, idempotency_key)
);

COMMENT ON TABLE  export_requests IS
    '导出请求：多模块（task/daily_report/expense/meal/plan/ai，X2 决策 B）多格式（csv/json/markdown/zip/pdf）异步导出作业入口（BR-31~34）';
COMMENT ON COLUMN export_requests.id              IS '主键';
COMMENT ON COLUMN export_requests.user_id         IS '请求用户；多租户隔离';
COMMENT ON COLUMN export_requests.module          IS '导出源模块：task | daily_report | expense | meal | plan | ai';
COMMENT ON COLUMN export_requests.format          IS '导出格式：csv | json | markdown | zip | pdf（v1.0 投产 csv/json/markdown，zip/pdf 预留 v1.1+）';
COMMENT ON COLUMN export_requests.filters         IS '筛选参数 JSONB：日期范围 / 分类 / 心情区间 等';
COMMENT ON COLUMN export_requests.status          IS '状态机：PENDING→PROCESSING→DONE/FAILED/CANCELLED';
COMMENT ON COLUMN export_requests.progress        IS '进度 0~100；EXPORT_ARTIFACT 写入时累加';
COMMENT ON COLUMN export_requests.attempts     IS '已重试次数（BR-33）';
COMMENT ON COLUMN export_requests.max_attempts     IS '最大重试次数 1~10（默认 3，对齐 ai_jobs 策略）';
COMMENT ON COLUMN export_requests.error           IS '最后一次失败错误摘要（截断 ≤2000 字符，应用层保证）';
COMMENT ON COLUMN export_requests.last_error_at   IS '最后一次失败时间';
COMMENT ON COLUMN export_requests.artifact_count  IS '已生成的 export_artifacts 数量';
COMMENT ON COLUMN export_requests.scheduled_at    IS '计划执行时间（默认 NOW()）';
COMMENT ON COLUMN export_requests.started_at      IS 'Worker 实际开始时间';
COMMENT ON COLUMN export_requests.finished_at     IS 'Worker 实际结束时间（DONE/FAILED/CANCELLED 时必填）';
COMMENT ON COLUMN export_requests.expires_at      IS '下载链接过期时间（DONE 时必填，finished_at + 7 天）';
COMMENT ON COLUMN export_requests.idempotency_key IS '客户端幂等键（user_id+idempotency_key 唯一）';

-- Worker 拉取索引
CREATE INDEX idx_export_requests_user_status_created
    ON export_requests(user_id, status, created_at DESC);

-- 待处理作业扫描
CREATE INDEX idx_export_requests_status_scheduled
    ON export_requests(status, scheduled_at)
    WHERE status IN ('PENDING','PROCESSING');

-- 过期清理（cron / job_runs）
CREATE INDEX idx_export_requests_done_expires
    ON export_requests(expires_at)
    WHERE status = 'DONE';
```

#### BR-31 ~ BR-34

| 编号 | 规则描述 | 触发条件 | 预期行为 |
|---|---|---|---|
| BR-31 | `export_requests.status='DONE'` 时 `finished_at` 与 `expires_at` 必填且 `expires_at > finished_at` | Worker 标记 DONE | CHECK 约束保证；DONE 时必须写齐时间 |
| BR-32 | `export_requests.status` 为 `FAILED` 或 `CANCELLED` 时 `finished_at` 必填 | Worker 标记终态 | CHECK 约束保证；终态必须留痕 |
| BR-33 | `attempts <= max_attempts` | Worker 准备重试 | CHECK 约束保证；超过上限自动 FAILED |
| BR-34 | `export_requests.status='DONE'` 时 `artifact_count >= 1` | Worker 标记 DONE | CHECK 约束保证；零产物的 DONE 是数据错误 |

---

### 1.2 新增 `export_artifacts`

**Flyway**：`V22__create_export_artifacts.sql`

```sql
-- ============================================================
-- V22__create_export_artifacts.sql
-- 导出产物表（一次请求可生成多文件，如 ZIP 内含多 CSV）
-- 修订编号：P0-EXPORT-02
-- 关联业务架构：§3.1 EXPORT_ARTIFACT / §6.9 流程 9
-- ============================================================

CREATE TABLE export_artifacts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 归属
    export_request_id   BIGINT NOT NULL
                        REFERENCES export_requests(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 文件元数据
    file_name           TEXT NOT NULL
                        CHECK (length(file_name) BETWEEN 1 AND 255),
    file_path           TEXT NOT NULL
                        CHECK (length(file_path) BETWEEN 1 AND 1024),
    file_size           BIGINT NOT NULL CHECK (file_size > 0),
    mime_type           TEXT NOT NULL
                        CHECK (mime_type IN (
                            'text/csv',
                            'text/markdown',
                            'application/zip',
                            'application/pdf'
                        )),
    checksum_sha256     CHAR(64) NOT NULL,

    -- 同请求多产物的顺序
    sort_order          INT NOT NULL DEFAULT 0,

    -- 过期
    expires_at          TIMESTAMPTZ NOT NULL,

    -- 通用审计
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- BR-37：expires_at > created_at
    CHECK (expires_at > created_at)
);

COMMENT ON TABLE  export_artifacts IS
    '导出产物元数据；1:N 关联 export_requests（ZIP 内含多 CSV 时多行）';
COMMENT ON COLUMN export_artifacts.id                IS '主键';
COMMENT ON COLUMN export_artifacts.export_request_id IS '所属导出请求（ON DELETE CASCADE：请求删除则产物删除）';
COMMENT ON COLUMN export_artifacts.user_id           IS '冗余 user_id 用于按用户查询（避免 JOIN）';
COMMENT ON COLUMN export_artifacts.file_name         IS '下载文件名（如 expense_2026-07.csv）';
COMMENT ON COLUMN export_artifacts.file_path         IS '对象存储路径（S3 key / MinIO path / 本地路径，由存储适配层解析）';
COMMENT ON COLUMN export_artifacts.file_size         IS '文件字节数（BR-35：> 0）';
COMMENT ON COLUMN export_artifacts.mime_type         IS 'MIME 类型（BR-36：4 种枚举之一）';
COMMENT ON COLUMN export_artifacts.checksum_sha256   IS 'SHA-256 校验和（64 字符 hex）';
COMMENT ON COLUMN export_artifacts.sort_order        IS '同请求多产物的展示顺序';
COMMENT ON COLUMN export_artifacts.expires_at        IS '下载过期时间（通常 = export_requests.expires_at）';

CREATE INDEX idx_export_artifacts_request_sort
    ON export_artifacts(export_request_id, sort_order);

CREATE INDEX idx_export_artifacts_user_expires
    ON export_artifacts(user_id, expires_at);

-- 过期清理 Job 用普通 B-Tree 索引
-- v1.2 修订：去掉 partial index（`WHERE expires_at > NOW()` 在索引创建时 NOW() 即固化，
-- 索引永远不包含新插入行，也无法自动清理过期行 → 索引语义误导 + 永远不会膨胀的"假阳性"）。
-- 由 `PurgeExpiredArtifactsJob`（日终 03:00）按 expires_at < NOW() 全表扫描后物理删除。
CREATE INDEX idx_export_artifacts_expires
    ON export_artifacts(expires_at);
```

#### BR-35 ~ BR-37

| 编号 | 规则描述 | 触发条件 | 预期行为 |
|---|---|---|---|
| BR-35 | `export_artifacts.file_size > 0` | Worker 写入产物 | CHECK 约束保证；零字节文件不写库 |
| BR-36 | `export_artifacts.mime_type` 必须为 4 种导出格式对应 MIME 之一 | Worker 写入产物 | CHECK 约束保证；避免应用层拼写错 MIME |
| BR-37 | `export_artifacts.expires_at > created_at` | Worker 写入产物 | CHECK 约束保证；过期时间错配立即报错 |

---

### 1.3 Outbox 事件补录（P0）

在数据模型 §1.5「跨模块事件清单」中追加以下三条。`outbox_events` 表结构（§3.1.4）已支持，无需 DDL 变更；新增 CHECK 约束以收紧 `event_type` 枚举。

#### 1.3.1 `outbox_events.event_type` 枚举扩展

```sql
-- V23__alter_outbox_add_export_notification_events.sql
-- 追加 outbox event_type 允许值
ALTER TABLE outbox_events
    DROP CONSTRAINT IF EXISTS outbox_events_event_type_check;

-- 新 CHECK 包含原值 + 新增
-- 原值：task.completed / task.reopened / task.created / task.updated /
--       milestone.created / milestone.updated / milestone.completed / milestone.missed /
--       habit.logged / meal.created / expense.created / budget.threshold /
--       plan.created / ai.job.completed / ai.report.feedback
-- 新增：export.completed / export.failed / notification.requested
ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        'task.completed','task.reopened','task.created','task.updated',
        'milestone.created','milestone.updated','milestone.completed','milestone.missed',
        'habit.logged','meal.created','expense.created','budget.threshold',
        'plan.created','ai.job.completed','ai.report.feedback',
        'export.completed','export.failed',
        'notification.requested'
    ));
```

#### 1.3.2 事件清单（v1.2 追加行）

| event_type | 触发源 | 消费方 | 用途 |
|---|---|---|---|
| **`export.completed`** | `export_requests.status → DONE` | UI（前端 SSE/通知）、EXPORT | 触发 ExportCompleted.v1 通知 UI、提供限时下载入口 |
| **`export.failed`** | `export_requests.status → FAILED`（重试耗尽） | UI、EXPORT | 触发 ExportFailed.v1 通知 UI、记录失败原因 |
| **`notification.requested`** | 各源域业务事务提交后 | NOTIFY | 通知模块按 dedupeKey + scheduledAt 调度投递；**取代 v1.1 散落的 Job 扫表模式**（P1 落地） |

#### 1.3.3 事件 payload 示例

**`export.completed`**

```json
{
  "eventId": "5d3a1c2e-7b8f-4a0d-9e1c-2f3a4b5c6d7e",
  "eventType": "export.completed",
  "eventVersion": 1,
  "occurredAt": "2026-07-26T08:30:00.123Z",
  "userId": 1042,
  "aggregateType": "export_request",
  "aggregateId": 8821,
  "correlationId": "req-7a2b-corr",
  "causationId": null,
  "payload": {
    "exportRequestId": 8821,
    "module": "expense",
    "format": "csv",
    "artifactCount": 1,
    "totalSizeBytes": 245678,
    "artifacts": [
      {
        "artifactId": 9911,
        "fileName": "expense_2026-07.csv",
        "fileSize": 245678,
        "mimeType": "text/csv",
        "checksumSha256": "a3f5b8c2d1e9f0a4b6c8d0e2f4a6b8c0d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2"
      }
    ],
    "expiresAt": "2026-08-02T08:30:00.000Z"
  }
}
```

**`export.failed`**

```json
{
  "eventId": "6e4b2d3f-8c9a-5b1e-af2d-3a4b5c6d7e8f",
  "eventType": "export.failed",
  "eventVersion": 1,
  "occurredAt": "2026-07-26T08:31:15.456Z",
  "userId": 1042,
  "aggregateType": "export_request",
  "aggregateId": 8822,
  "correlationId": "req-7a2b-corr",
  "causationId": null,
  "payload": {
    "exportRequestId": 8822,
    "module": "daily_report",
    "format": "markdown",
    "retryCount": 3,
    "maxRetries": 3,
    "error": "DailyReportExportProvider: source data fetch timeout after 30s",
    "errorCode": "EXPORT_SOURCE_TIMEOUT",
    "failStage": "FETCH_DATA"
  }
}
```

**`notification.requested`**（P1 预留事件定义，P0 阶段仅入 outbox 枚举）

```json
{
  "eventId": "7f5c3e4a-9d0b-6c2f-b03e-4b5c6d7e8f9a",
  "eventType": "notification.requested",
  "eventVersion": 1,
  "occurredAt": "2026-07-26T08:32:00.000Z",
  "userId": 1042,
  "aggregateType": "task",
  "aggregateId": 554433,
  "correlationId": "tx-9a2b-corr",
  "causationId": null,
  "payload": {
    "templateCode": "TASK_DUE_IN_1H",
    "subjectRef": { "kind": "task", "id": 554433, "title": "回复 X 项目邮件" },
    "scheduledAt": "2026-07-26T15:00:00.000Z",
    "deepLink": "/tasks/554433",
    "dedupeKey": "task:554433:due_at:2026-07-26T15:00:00Z",
    "channel": "PUSH",
    "priority": "NORMAL"
  }
}
```

---

## 1.4 Outbox 事件补录（plan-03-expense Phase B-3，P0）

plan-03 code review 发现 `ExpenseService.update()` / `softDelete()` / `restore()` 未发射 outbox 事件，导致下游消费者（ai 模块月度聚合、StatsService 实时投影）感知不到状态变更。Phase A 仅发射 `EXPENSE_CREATED`，不一致。本次 P0 修订追加 3 条事件。

修订编号：**P0-EXPENSE-EVENTS-01**（含 P0-EXPENSE-EVENTS-02 修订：restore 走独立 `expense.restored` 事件而非复用 `expense.updated`）
关联文档：`docs/lifewise/architecture/known-limitations-v1.md` §B-3
关联代码：`com.lifewise.expense.service.ExpenseService`（`update` / `softDelete` / `restore`）

### 1.4.1 `outbox_events.event_type` 枚举扩展（V39）

```sql
-- ============================================================
-- V39__add_expense_updated_deleted_events.sql
-- plan-03-expense Phase B-3：outbox 事件扩展（27 → 30 条）
-- 修订编号：P0-EXPENSE-EVENTS-01
-- 关联：known-limitations-v1 §B-3；business-architecture §5.3 事件契约
-- ============================================================

ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_event_type_check;

ALTER TABLE outbox_events ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        -- task / habit（5）
        'task.created','task.updated','task.completed','task.reopened',
        'habit.logged',
        -- plan / milestone（5）
        'plan.created',
        'milestone.created','milestone.updated','milestone.completed','milestone.missed',
        -- daily_report / ai.summary（3）
        'daily_report.created','daily_report.updated',
        'ai.summary.generated',
        -- meal / expense / budget（6，v1.2 +3）
        'meal.created',
        'expense.created','expense.updated','expense.restored','expense.deleted',
        'budget.threshold',
        -- ai（2）
        'ai.job.completed','ai.report.feedback',
        -- export（2）
        'export.completed','export.failed',
        -- notification（1）
        'notification.requested',
        -- auth canonical（4）
        'auth.user.registered','auth.user.logged_in',
        'auth.user.password_reset_requested','auth.token.reuse_detected',
        -- auth legacy（2）
        'auth.login','auth.logout'
    ));

COMMENT ON CONSTRAINT outbox_events_event_type_check ON outbox_events IS
    'v1.2 P0-EXPENSE-EVENTS-01：30 条事件白名单（plan-03-expense Phase B-3 追加 expense.updated + expense.restored + expense.deleted）';
```

### 1.4.2 事件清单（plan-03-expense Phase B-3 追加行）

| event_type | 触发源 | 消费方 | 用途 |
|---|---|---|---|
| **`expense.updated`** | `ExpenseService.update()` | AI（v1.1 月度聚合）、StatsService 实时投影 | 字段变更（金额 / 分类 / 时间 / note），触发重算与审计同步 |
| **`expense.restored`** | `ExpenseService.restore()` | AI（v1.1 月度聚合）、StatsService 实时投影 | 软删记录被恢复，触发重新加入累计 + 审计 `EXPENSE_RESTORED` 留痕 |
| **`expense.deleted`** | `ExpenseService.softDelete()` | AI（v1.1 月度聚合）、StatsService 实时投影 | 软删生效，触发月度聚合剔除 + 审计 `EXPENSE_DELETED` 留痕 |

> **P0-EXPENSE-EVENTS-02 修订说明**：v1.0 初稿曾考虑让 `restore()` 复用 `expense.updated`，但 review 发现两个语义不同的操作（字段修改 vs 记录恢复）共享事件类型会让下游无法区分。`update` 与 `restore` 对 ai/Stats 投影的处理路径不同（restore 需重加入累计，update 仅修正），必须独立事件类型。EventType 中已有 `TASK_REOPENED` 先例（task 模块）作为范式参考。

### 1.4.3 事件 payload 示例

**`expense.updated`**

```json
{
  "eventId": "8a6d4f5b-0e1c-7d3a-c14f-5c6d7e8f9a0b",
  "eventType": "expense.updated",
  "eventVersion": 1,
  "occurredAt": "2026-08-03T12:34:56.789Z",
  "userId": 7,
  "aggregateType": "expense",
  "aggregateId": 100,
  "correlationId": null,
  "causationId": null,
  "payload": {
    "expenseId": 100,
    "userId": 7,
    "categoryId": 11,
    "amountCents": 3500,
    "currency": "CNY",
    "occurredAt": "2026-08-03T09:30:00.000Z"
  }
}
```

**`expense.restored`**

```json
{
  "eventId": "c8e9f0a1-2b3c-4d5e-9f8a-7b6c5d4e3f2a",
  "eventType": "expense.restored",
  "eventVersion": 1,
  "occurredAt": "2026-08-03T12:36:00.000Z",
  "userId": 7,
  "aggregateType": "expense",
  "aggregateId": 100,
  "correlationId": null,
  "causationId": null,
  "payload": {
    "expenseId": 100,
    "userId": 7,
    "categoryId": 11,
    "amountCents": 3500,
    "currency": "CNY",
    "occurredAt": "2026-08-03T09:30:00.000Z",
    "restoredAt": "2026-08-03T12:36:00.000Z"
  }
}
```

**`expense.deleted`**

```json
{
  "eventId": "9b7e5a6c-1f2d-8e4b-d25a-6d7e8f9a0b1c",
  "eventType": "expense.deleted",
  "eventVersion": 1,
  "occurredAt": "2026-08-03T12:35:10.123Z",
  "userId": 7,
  "aggregateType": "expense",
  "aggregateId": 100,
  "correlationId": null,
  "causationId": null,
  "payload": {
    "expenseId": 100,
    "userId": 7,
    "deletedAt": "2026-08-03T12:35:09.987Z"
  }
}
```

### 1.4.4 BudgetEvaluator 接入修订（P0-EXPENSE-EVAL-01）

code review 发现 `ExpenseService.update()` 未调 `BudgetEvaluator.evaluate()`——金额 / 分类 / 日期变更直接影响分类周期累计，漏调会漏报阈值事件。修订后行为：

| 服务方法 | 是否调 BudgetEvaluator | 理由 |
|---|---|---|
| `create` | ✓ | 累计上升，可能触阈 |
| `update` | ✓（**v1.0 P0 修订**） | 字段变更直接影响累计（金额 / 分类 / 时间） |
| `restore` | ✓（**v1.0 P0 修订**） | 软删记录恢复后重新计入累计 |
| `softDelete` | ✗（明确不调） | 累计只可能下降；evaluator 仅在 `pct ≥ threshold` 时 emit，下降不会触发新事件。「回落复位」通知属 v1.1 范围（evaluator 当前无 reset 状态）。 |

dedupe 由 `BudgetEvaluator` 内部按 `(budgetId, period, threshold)` 保证，无需调用方去重。

---

## 2. P1 修订

### 2.1 新增 `notification_requests`

**Flyway**：`V24__create_notification_requests.sql`（与 `notification_deliveries` 同脚本）

```sql
-- ============================================================
-- V24__create_notification_requests.sql
-- 通知请求表：源域事务提交后写入，与 notification.requested 事件双写
-- 修订编号：P1-NOTIFY-01
-- 关联业务架构：§3.1 NOTIFICATION_REQUEST / §6.8 流程 8
-- ============================================================

CREATE TABLE notification_requests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 租户与归属
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 来源业务事实（保留 stable id 引用，不强 FK 跨域）
    source_module       TEXT NOT NULL
                        CHECK (source_module IN ('task','habit','plan','milestone','expense','budget','ai_report','export_request','system')),
    source_aggregate_id BIGINT NULL,
    correlation_id      TEXT NULL,

    -- 模板与内容引用
    template_code       TEXT NOT NULL
                        CHECK (length(template_code) BETWEEN 1 AND 100),
    subject_ref         JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 调度
    scheduled_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 去重键：源域按业务规则生成；同 user 内唯一
    dedupe_key          TEXT NOT NULL
                        CHECK (length(dedupe_key) BETWEEN 1 AND 255),

    -- 渠道与优先级
    channel             TEXT NOT NULL DEFAULT 'PUSH'
                        CHECK (channel IN ('PUSH','IN_APP','EMAIL')),
    priority            TEXT NOT NULL DEFAULT 'NORMAL'
                        CHECK (priority IN ('LOW','NORMAL','HIGH')),

    -- 状态机
    status              TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SCHEDULED','DISPATCHED','DELIVERED','FAILED','SUPPRESSED','EXPIRED','CANCELLED')),

    -- 重试与错误
    max_attempts        INT NOT NULL DEFAULT 3
                        CHECK (max_attempts BETWEEN 1 AND 10),
    attempts            INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error          TEXT NULL,
    last_error_at       TIMESTAMPTZ NULL,

    -- 时间戳
    dispatched_at       TIMESTAMPTZ NULL,
    delivered_at        TIMESTAMPTZ NULL,
    suppressed_reason   TEXT NULL,

    -- 关联 Outbox 事件 ID（写请求同时写事件）
    outbox_event_id     BIGINT NULL,

    -- 通用审计
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- BR-38：同 user 内 dedupe_key 唯一（覆盖未软删记录）
    UNIQUE (user_id, dedupe_key),

    -- BR-39：status=DELIVERED 时 delivered_at 必填
    CHECK (status <> 'DELIVERED' OR delivered_at IS NOT NULL),

    -- BR-40：status=DISPATCHED 时 dispatched_at 必填
    CHECK (status <> 'DISPATCHED' OR dispatched_at IS NOT NULL),

    -- BR-41：attempts <= max_attempts
    CHECK (attempts <= max_attempts)
);

COMMENT ON TABLE  notification_requests IS
    '通知请求：源域事务内 INSERT，Worker 按 dedupeKey 调度投递（BR-38~41）；本表不支持软删除（无 deleted_at 字段，by design：通知是一次性事件，无恢复语义；过期清理走 partition drop）';
COMMENT ON COLUMN notification_requests.id                  IS '主键';
COMMENT ON COLUMN notification_requests.user_id             IS '目标用户';
COMMENT ON COLUMN notification_requests.source_module       IS '来源业务域（task/plan/expense/...）';
COMMENT ON COLUMN notification_requests.source_aggregate_id IS '来源业务聚合 ID（弱引用，跨域不强 FK）';
COMMENT ON COLUMN notification_requests.correlation_id      IS '链路追踪 ID（与 outbox 一致）';
COMMENT ON COLUMN notification_requests.template_code       IS '通知模板代码（与前端 i18n key 对齐）';
COMMENT ON COLUMN notification_requests.subject_ref         IS '模板变量引用（如 task.title / plan.title）';
COMMENT ON COLUMN notification_requests.payload             IS '模板变量值（JSONB）';
COMMENT ON COLUMN notification_requests.scheduled_at        IS '计划投递时间';
COMMENT ON COLUMN notification_requests.dedupe_key          IS '去重键（user 内唯一，BR-38）';
COMMENT ON COLUMN notification_requests.channel             IS '渠道：PUSH / IN_APP / EMAIL';
COMMENT ON COLUMN notification_requests.priority            IS '优先级：LOW / NORMAL / HIGH';
COMMENT ON COLUMN notification_requests.status              IS '状态机（详见 BR-38~41）';
COMMENT ON COLUMN notification_requests.outbox_event_id     IS '关联的 outbox_events.id（同事务写）';

-- Worker 扫描索引
CREATE INDEX idx_notification_requests_pending_scheduled
    ON notification_requests(status, scheduled_at)
    WHERE status IN ('PENDING','SCHEDULED');

-- 用户视角
CREATE INDEX idx_notification_requests_user_created
    ON notification_requests(user_id, created_at DESC);

-- dedupe_key 反查（虽然 UNIQUE 已有索引，但用 partial 优化软删场景）
CREATE INDEX idx_notification_requests_user_status
    ON notification_requests(user_id, status);
```

#### BR-38 ~ BR-41

| 编号 | 规则描述 | 触发条件 | 预期行为 |
|---|---|---|---|
| BR-38 | `(notification_requests.user_id, dedupe_key)` 全局唯一 | 源域 INSERT | 重复请求直接报错；应用层用 `ON CONFLICT DO NOTHING` 实现幂等 |
| BR-39 | `status='DELIVERED'` 时 `delivered_at` 必填 | Worker 标记 DELIVERED | CHECK 约束保证；送达时间必须留痕 |
| BR-40 | `status='DISPATCHED'` 时 `dispatched_at` 必填 | Worker 标记 DISPATCHED | CHECK 约束保证；分发时间必须留痕 |
| BR-41 | `attempts <= max_attempts` | Worker 重试 | CHECK 约束保证；超过上限自动 FAILED |

---

### 2.2 新增 `notification_deliveries`

**Flyway**：`V24__create_notification_deliveries.sql`（与 `notification_requests` 同 V24 脚本内）

```sql
-- ============================================================
-- V24__create_notification_deliveries.sql
-- 通知投递尝试：每次 channel 投递一条记录，支持重试与渠道降级
-- 修订编号：P1-NOTIFY-02
-- 关联业务架构：§3.1 NOTIFICATION_DELIVERY / §6.8 流程 8
-- ============================================================

CREATE TABLE notification_deliveries (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 归属
    notification_request_id     BIGINT NOT NULL
                                REFERENCES notification_requests(id) ON DELETE CASCADE,
    user_id                     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 投递渠道
    channel                     TEXT NOT NULL
                                CHECK (channel IN ('PUSH','IN_APP','EMAIL')),

    -- 目标设备/收件人
    push_subscription_id        BIGINT NULL
                                REFERENCES push_subscriptions(id) ON DELETE SET NULL,
    target_ref                  TEXT NULL,    -- 邮箱地址 / 设备 ID

    -- 状态
    status                      TEXT NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','SUCCESS','FAILED','SKIPPED')),

    -- HTTP 通道结果（PUSH / EMAIL）
    http_status                 INT NULL
                                CHECK (http_status IS NULL OR (http_status BETWEEN 100 AND 599)),
    response_body               TEXT NULL
                                CHECK (response_body IS NULL OR length(response_body) <= 2000),

    -- 重试
    attempt_number              INT NOT NULL DEFAULT 1 CHECK (attempt_number >= 1),
    error_code                  TEXT NULL,
    error_message               TEXT NULL
                                CHECK (error_message IS NULL OR length(error_message) <= 2000),

    -- 时间戳
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at                 TIMESTAMPTZ NULL,

    -- BR-42：status=SUCCESS 时 finished_at 必填
    CHECK (status <> 'SUCCESS' OR finished_at IS NOT NULL),

    -- BR-43：status=SUCCESS 时 http_status 必填 2xx
    CHECK (status <> 'SUCCESS' OR (http_status IS NOT NULL AND http_status BETWEEN 200 AND 299))
);

COMMENT ON TABLE  notification_deliveries IS
    '通知投递尝试：每次 channel 投递一条；一次通知可有多条（重试 + 渠道降级，BR-42~43）';
COMMENT ON COLUMN notification_deliveries.id                      IS '主键';
COMMENT ON COLUMN notification_deliveries.notification_request_id IS '所属通知请求';
COMMENT ON COLUMN notification_deliveries.user_id                 IS '冗余 user_id（避免 JOIN）';
COMMENT ON COLUMN notification_deliveries.channel                 IS '投递渠道';
COMMENT ON COLUMN notification_deliveries.push_subscription_id    IS '使用的 Web Push 订阅（PUSH 渠道时必填）';
COMMENT ON COLUMN notification_deliveries.target_ref              IS '渠道目标（邮箱地址 / 设备 ID / 应用内收件人）';
COMMENT ON COLUMN notification_deliveries.status                  IS '本次投递结果：PENDING/SUCCESS/FAILED/SKIPPED';
COMMENT ON COLUMN notification_deliveries.http_status             IS 'HTTP 响应码（PUSH/EMAIL 渠道）';
COMMENT ON COLUMN notification_deliveries.response_body           IS 'HTTP 响应体（截断 ≤2000 字符）';
COMMENT ON COLUMN notification_deliveries.attempt_number          IS '本次为第几次尝试（1-based）';

CREATE INDEX idx_notification_deliveries_request
    ON notification_deliveries(notification_request_id, attempt_number);

CREATE INDEX idx_notification_deliveries_user_created
    ON notification_deliveries(user_id, started_at DESC);

-- 成功率监控
CREATE INDEX idx_notification_deliveries_status_started
    ON notification_deliveries(status, started_at DESC)
    WHERE status IN ('SUCCESS','FAILED');
```

#### BR-42 ~ BR-43

| 编号 | 规则描述 | 触发条件 | 预期行为 |
|---|---|---|---|
| BR-42 | `status='SUCCESS'` 时 `finished_at` 必填 | Worker 标记 SUCCESS | CHECK 约束保证；成功投递必留时间戳 |
| BR-43 | `status='SUCCESS'` 时 `http_status` 必填且 ∈ [200, 299] | Worker 标记 SUCCESS | CHECK 约束保证；HTTP 通道必须 2xx 才算成功 |

---

### 2.3 新增 `conversations` + `chat_messages` 演进 + `ai_summaries` 加固

#### 2.3.1 新增 `conversations` 表

**Flyway**：`V25__create_conversations.sql`（与 chat_messages 加列 + ai_summaries 加固 同 V25 脚本）

```sql
-- ============================================================
-- V25__create_conversations.sql
-- AI 对话会话表：一会话多消息（chat_messages）
-- 修订编号：P1-CONV-01
-- 关联业务架构：§3.1 CONVERSATION / §5.4 Conversation 域
-- ============================================================

CREATE TABLE conversations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 会话元数据
    title               TEXT NULL
                        CHECK (title IS NULL OR length(title) BETWEEN 1 AND 200),
    source              TEXT NOT NULL DEFAULT 'WEB'
                        CHECK (source IN ('WEB','MOBILE','API','BACKFILL')),

    -- 统计字段（由 chat_messages 写入触发器更新）
    message_count       INT NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    last_message_at     TIMESTAMPTZ NULL,
    last_message_preview TEXT NULL
                        CHECK (last_message_preview IS NULL OR length(last_message_preview) <= 200),

    -- 归档
    is_archived         BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at         TIMESTAMPTZ NULL,

    -- 通用审计
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ NULL,

    -- BR-44：is_archived=TRUE 时 archived_at 必填
    CHECK ((is_archived = FALSE) OR (archived_at IS NOT NULL)),

    -- BR-45：last_message_at IS NULL OR >= created_at
    CHECK (last_message_at IS NULL OR last_message_at >= created_at)
);

COMMENT ON TABLE  conversations IS
    'AI 对话会话；一会话多消息（chat_messages.conversation_id），30 天保留策略对齐 chat_messages 分区';
COMMENT ON COLUMN conversations.id                  IS '主键';
COMMENT ON COLUMN conversations.user_id             IS '所属用户';
COMMENT ON COLUMN conversations.title               IS '会话标题（首条 USER 消息自动生成或用户手动命名）';
COMMENT ON COLUMN conversations.source              IS '会话来源：WEB / MOBILE / API';
COMMENT ON COLUMN conversations.message_count       IS '消息总数（不含 SYSTEM 提示）';
COMMENT ON COLUMN conversations.last_message_at     IS '最后一条消息时间';
COMMENT ON COLUMN conversations.last_message_preview IS '最后一条消息预览（≤200 字符，用于侧边栏）';
COMMENT ON COLUMN conversations.is_archived         IS '是否已归档';

CREATE INDEX idx_conversations_user_active
    ON conversations(user_id, last_message_at DESC)
    WHERE deleted_at IS NULL AND is_archived = FALSE;

CREATE INDEX idx_conversations_user_created
    ON conversations(user_id, created_at DESC)
    WHERE deleted_at IS NULL;
```

#### 2.3.2 `chat_messages` 加 `conversation_id` 外键

**Flyway**：`V25__alter_chat_messages_add_conversation.sql`（与 V25 同脚本）

```sql
-- ============================================================
-- V25__alter_chat_messages_add_conversation.sql
-- chat_messages 加 conversation_id 外键，关联 conversations
-- 修订编号：P1-CONV-02
-- 兼容性：nullable + ON DELETE SET NULL，存量消息不强制迁移
-- ============================================================

ALTER TABLE chat_messages
    ADD COLUMN conversation_id BIGINT NULL
        REFERENCES conversations(id) ON DELETE SET NULL;

-- 历史消息回填：每个 user 取最早的 chat_messages 归到一个"默认会话"
-- 由 V28__backfill_chat_conversations.sql 异步处理（避免长事务；V 编号以 plan-data-flyway.md 为权威）
-- 索引在回填完成后建（partial index: conversation_id IS NOT NULL）

CREATE INDEX idx_chat_messages_conversation_created
    ON chat_messages(conversation_id, created_at DESC)
    WHERE conversation_id IS NOT NULL;
```

**Flyway**：`V28__backfill_chat_conversations.sql`（**异步回填脚本，独立 V28 版本**——避免与 V25 DDL 同事务长事务阻塞；V 编号以 plan-data-flyway.md 为权威）

```sql
-- ============================================================
-- V28__backfill_chat_conversations.sql
-- 历史 chat_messages 回填到 conversations
-- 策略：每个 user 按月度聚合（与 chat_messages 按月分区对齐）
-- 注意：本脚本为异步回填，独立 V28 版本，与 V25 DDL 分离
-- ============================================================

-- Step 1：建临时映射表（事务结束自动 DROP，避免长期残留）
CREATE TEMP TABLE tmp_chat_backfill_map (
    user_id         BIGINT NOT NULL,
    period_start    DATE   NOT NULL,
    conversation_id BIGINT NOT NULL
) ON COMMIT DROP;

-- Step 2：批量插入回填会话，source='BACKFILL' 标识与用户手动创建区分
--         同时写入临时映射表，避免用 title 字符串 JOIN 导致同名会话误关联
WITH inserted_convs AS (
    INSERT INTO conversations (user_id, title, source, message_count, last_message_at, created_at)
    SELECT
        cm.user_id,
        '历史会话 ' || to_char(date_trunc('month', cm.created_at), 'YYYY-MM') AS title,
        'BACKFILL' AS source,
        COUNT(*) AS message_count,
        MAX(cm.created_at) AS last_message_at,
        date_trunc('month', cm.created_at) AS created_at
    FROM chat_messages cm
    WHERE cm.conversation_id IS NULL
    GROUP BY cm.user_id, date_trunc('month', cm.created_at)
    RETURNING id, user_id, date_trunc('month', created_at) AS period_start
)
INSERT INTO tmp_chat_backfill_map (user_id, period_start, conversation_id)
SELECT user_id, period_start, id FROM inserted_convs;

-- Step 3：用 (user_id, period_start) 严格 JOIN 临时映射表，不用 title
UPDATE chat_messages cm
SET conversation_id = m.conversation_id
FROM tmp_chat_backfill_map m
WHERE cm.conversation_id IS NULL
  AND cm.user_id = m.user_id
  AND date_trunc('month', cm.created_at) = m.period_start;
```

#### BR-44 ~ BR-45

| 编号 | 规则描述 | 触发条件 | 预期行为 |
|---|---|---|---|
| BR-44 | `conversations.is_archived=TRUE` 时 `archived_at` 必填 | 用户归档 | CHECK 约束保证；归档时间必留痕 |
| BR-45 | `conversations.last_message_at` 为 NULL 或 ≥ `created_at` | chat_messages 触发器更新 | CHECK 约束保证；统计字段不出现"未来时间" |

---

### 2.4 BR-21 追加：AI 写入 `ai_summaries` 的边界约束

#### 修订说明

`ai_summaries` 表归属日报域（data-model §1.1.4 / §3.4.3 / BR-21），但 AI 模块是实际写入方。v1.1.1 仅在表注释中说明"日报软删 → 级联软删摘要（应用层）"，未约束 AI 写入路径。v1.2 在 BR-21 末尾追加以下三条子规则：

#### 修订后的 BR-21 完整定义

| 编号 | 规则描述 | 触发条件 | 预期行为 |
|---|---|---|---|
| BR-21 | `ai_summaries.daily_report_id NOT NULL`；日报软删 → 级联软删摘要（应用层） | INSERT / 软删 | UNIQUE(daily_report_id) + NOT NULL；软删走应用层事务 |
| **BR-21.a** | **AI 模块只能通过日报域的 `CreateSummarySnapshot` 应用端口写入 `ai_summaries`；不得绕过端口直接 INSERT** | AI 作业完成 | 应用层在 `CreateSummarySnapshot` 入口校验 `caller == 'ai_worker'` + JWT scope；越权调用返回 `FORBIDDEN` |
| **BR-21.b** | **AI 写入 `ai_summaries` 时必须同时写 `model_version`（如 `ollama:deepseek:8b`）与 `generated_at`；不得为空或占位** | AI 作业完成 | CHECK 约束：`length(model_version) BETWEEN 1 AND 100`；`generated_at IS NOT NULL` |
| **BR-21.c** | **用户编辑摘要后（`user_edited=TRUE`），AI 不得自动覆盖**；必须由用户手动触发重新生成 | 用户编辑 / AI 重新生成 | 应用层：`UPDATE ai_summaries SET summary_md=?, model_version=?, generated_at=NOW(), user_edited=FALSE WHERE daily_report_id=? AND user_edited=TRUE` 必须先确认（弹窗） |

#### 配套 DDL 加固（V25__tighten_ai_summaries.sql）

```sql
-- ============================================================
-- V25__tighten_ai_summaries.sql
-- 强化 ai_summaries 字段约束，落地 BR-21.b
-- （与 conversations + chat_messages 加列 同 V25 脚本）
-- ============================================================

ALTER TABLE ai_summaries
    ALTER COLUMN model_version SET NOT NULL,
    ALTER COLUMN generated_at SET NOT NULL,
    ADD CONSTRAINT ai_summaries_model_version_length
        CHECK (length(model_version) BETWEEN 1 AND 100);

-- 索引：用户视角查询最新摘要
CREATE INDEX idx_ai_summaries_user_generated
    ON ai_summaries(daily_report_id, generated_at DESC)
    WHERE user_edited = FALSE;
```

---

## 3. 修订摘要

### 3.1 变更总表

| 变更类型 | 对象 | 标识 | 影响范围 | 配套迁移 |
|---|---|---|---|---|
| 新增表 | `export_requests` | P0-EXPORT-01 | EXPORT 模块、UI、4 源域 Provider | V21 |
| 新增表 | `export_artifacts` | P0-EXPORT-02 | EXPORT 模块、对象存储适配层 | V22 |
| CHECK 扩展 | `outbox_events.event_type` | P0-EVT-01 | 所有消费 `outbox_events` 的 Worker | V23 |
| 事件补录 | `export.completed` / `export.failed` / `notification.requested` | P0-EVT-02 | UI、EXPORT、NOTIFY | — |
| 新增表 | `notification_requests` | P1-NOTIFY-01 | NOTIFY 模块、5 源域、所有 Push 触发点 | V24 |
| 新增表 | `notification_deliveries` | P1-NOTIFY-02 | NOTIFY 模块、监控 / SLA 报表 | V24 |
| 新增表 | `conversations` | P1-CONV-01 | AI 模块、UI 会话侧边栏 | V25 |
| 表结构修改 | `chat_messages`（加 `conversation_id`） | P1-CONV-02 | AI 模块；存量数据异步回填 | V25 |
| 数据回填 | `chat_messages → conversations` | P1-CONV-03 | 一次性历史数据迁移 | V28 |
| CHECK 收紧 | `ai_summaries`（NOT NULL / 长度） | P1-BR-21 | AI 模块、ReportGenerator | V25 |
| 业务规则追加 | BR-21.a / 21.b / 21.c | P1-BR-21 | AI 写入路径、用户编辑流程 | — |

### 3.2 BR 变更清单

| 编号 | 类型 | 描述 | 落地位置 |
|---|---|---|---|
| BR-21 | 修改 | `ai_summaries` 字段约束 + 新增 21.a / 21.b / 21.c 三条写入边界 | §2.4 + V25 |
| BR-31 | 新增 | `export_requests` DONE 终态时间完整性 | §1.1 |
| BR-32 | 新增 | `export_requests` FAILED/CANCELLED 终态时间必填 | §1.1 |
| BR-33 | 新增 | `export_requests.attempts <= max_attempts` | §1.1 |
| BR-34 | 新增 | `export_requests` DONE 时 `artifact_count >= 1` | §1.1 |
| BR-35 | 新增 | `export_artifacts.file_size > 0` | §1.2 |
| BR-36 | 新增 | `export_artifacts.mime_type` 4 种枚举 | §1.2 |
| BR-37 | 新增 | `export_artifacts.expires_at > created_at` | §1.2 |
| BR-38 | 新增 | `notification_requests(user_id, dedupe_key)` UNIQUE | §2.1 |
| BR-39 | 新增 | `notification_requests.status=DELIVERED` 时 `delivered_at` 必填 | §2.1 |
| BR-40 | 新增 | `notification_requests.status=DISPATCHED` 时 `dispatched_at` 必填 | §2.1 |
| BR-41 | 新增 | `notification_requests.attempts <= max_attempts` | §2.1 |
| BR-42 | 新增 | `notification_deliveries.status=SUCCESS` 时 `finished_at` 必填 | §2.2 |
| BR-43 | 新增 | `notification_deliveries.status=SUCCESS` 时 `http_status ∈ [200, 299]` | §2.2 |
| BR-44 | 新增 | `conversations.is_archived=TRUE` 时 `archived_at` 必填 | §2.3.1 |
| BR-45 | 新增 | `conversations.last_message_at ≥ created_at` | §2.3.1 |

### 3.3 业务架构 §3.1 实体图需更新的实体

| 业务架构 §3.1 实体 | v1.1.1 状态 | v1.2 状态 | 备注 |
|---|---|---|---|
| `EXPORT_REQUEST` | ❌ 实体在 §3.1 但无表 | ✅ `export_requests` | 状态机、流式重试、产物统计 |
| `EXPORT_ARTIFACT` | ❌ 实体在 §3.1 但无表 | ✅ `export_artifacts` | 1:N 关系、过期清理 |
| `NOTIFICATION_REQUEST` | ❌ 实体在 §3.1 但无表 | ✅ `notification_requests` | dedupeKey 状态机、Outbox 双写 |
| `NOTIFICATION_DELIVERY` | ❌ 实体在 §3.1 但无表 | ✅ `notification_deliveries` | 渠道 + 重试 + HTTP 状态 |
| `EVIDENCE` | ❌ 实体在 §3.1 但无表 | ⚠️ 仍无独立表 | 保持 JSONB 嵌入 `ai_reports.structured_data`；v1.3 再评估独立化 |
| `CONVERSATION` | ❌ 实体在 §3.1 但无表 | ✅ `conversations` | 关联 `chat_messages.conversation_id` |
| `HIGHLIGHT` | ✅ `daily_report_highlights` | — | 命名合理，无变更 |
| `SUMMARY_SNAPSHOT` | ✅ `ai_summaries` | + BR-21.a/b/c | 命名合理，新增写入约束 |
| `OUTBOX_EVENT` | ✅ `outbox_events` | + 3 个 event_type | 事件枚举扩展 |
| `JOB_RUN` | ✅ `job_runs` | — | 无变更 |
| `USER_PREFERENCE` | ✅ `user_profiles` | — | 命名合理 |
| `NUTRITION_TARGET` | ✅ `user_profiles.daily_kcal_target` | — | 字段化保留 |

### 3.4 落地顺序建议

| 阶段 | 脚本 | 前置依赖 | 风险等级 |
|---|---|---|---|
| 阶段 1（P0） | V21 + V22 + V23 | 无 | 中（新增表 + CHECK 收紧） |
| 阶段 2（P1-EXPORT 配套） | 导出 Worker 改造、`ExportDataProvider` 实现 | V21/V22 | 中 |
| 阶段 3（P1-NOTIFY） | V24 | V23（事件枚举） | 中 |
| 阶段 4（P1-CONV） | V25（含 DDL）+ V28（异步回填） | 无 | 中（回填需控制并发） |
| 阶段 5（P1-BR-21） | V25（CHECK 收紧，与 CONV DDL 同脚本） | 无 | 低（仅 CHECK 收紧） |

### 3.5 验证清单（写库前必跑）

- [ ] V21/V22/V24/V25 在空库执行无错误
- [ ] V23 扩展 CHECK 后现有 outbox 数据不违反新约束（应自动满足）
- [ ] V25 加 `conversation_id` 列不阻塞 chat_messages 写入
- [ ] V28 回填脚本在生产数据量（百万级 chat_messages）下 < 30 分钟
- [ ] V25 收紧 ai_summaries 字段前，存量 `model_version`/`generated_at` 无 NULL
- [ ] 状态机 CHECK 触发边界用例：所有 `DONE`/`FAILED`/`DELIVERED`/`SUCCESS` 终态必须满足时间字段完整性
- [ ] `export_requests` 与 `export_artifacts` 1:N 关系在 ON DELETE CASCADE 下正确级联

---

*文档版本：v1.2-amendment*
*生成日期：2026-07-26*
*维护者：架构组*
*配套文档：data-model-design.md v1.1.1 / business-architecture.md v1.0 / 6 份 PRD v1.0*