# plan-export 实施方案

## 参考资料

- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.3 导出模块边界 + §5 事件契约（`export.completed` / `export.failed`）
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V21（export_requests，P0-EXPORT-01）+ V22（export_artifacts，P0-EXPORT-02）
- `docs/lifewise/specs/PRD/` — 各模块导出需求
  - `01-task-management.md` §DR-030 — 任务列表导出
  - `02-daily-report.md` §DR-031 — 日报导出
  - `03-expense.md` §EXP-030 — 消费记录导出
  - `04-diet.md` §MEAL-030 — 饮食记录导出
  - `05-plan-management.md` §PLAN-030 — 计划进度导出
  - `06-ai.md` §AI-009 — AI 报告导出
- `CLAUDE.md` §7 安全规范 + §10 红线 + §7.4 速率限制（export 5 req/min/user）

## 状态

> **占位骨架（v1.0 收口）**：本文件为 v1.2 新增导出模块的**实施骨架**，定义包结构、端点契约、事件契约、数据表复用与 TDD 种子。**v1.0 收口阶段实现简化版同步导出**（即时生成 CSV / JSON，不引入后台异步作业）；v1.1+ 演进为大文件异步导出 + Web Push 通知完成。

## 参考目录

- backend：`app/src/main/java/com/lifewise/export/`
  - `controller/` — ExportController（异步导出请求 + 下载）
  - `service/` — ExportService / ExportJobRunner / CsvSerializer / JsonSerializer
  - `domain/` — ExportRequest / ExportArtifact
  - `repository/` — ExportRequestRepository / ExportArtifactRepository
  - `port/` — ExportReadPort（聚合各模块数据只读视图）
- frontend：`docs/lifewise/designs/` — 各模块导出按钮 UI（已嵌入对应原型）

## 1. 模块边界 / 包结构

export 模块是**只读跨域聚合**——不写业务数据，只读各模块快照并序列化输出。

```
export/
├── controller/
│   ├── ExportController.java          POST /api/exports 提交 / GET 下载
│   └── ExportAdminController.java     GET /api/exports（admin 看全部）
├── service/
│   ├── ExportService.java             创建 ExportRequest + 立即异步触发
│   ├── ExportJobRunner.java           @Async 拉取各模块数据 + 序列化 + 写 artifact
│   ├── ExportAggregator.java          通过 *ReadPort 拉 task/plan/expense/meal/daily/ai 快照
│   ├── CsvSerializer.java             CSV 序列化（RFC 4180 + UTF-8 BOM 兼容 Excel）
│   └── JsonSerializer.java            JSON 序列化（含 evidenceRefs）
├── domain/
│   ├── ExportRequest.java             export_requests 表实体
│   └── ExportArtifact.java            export_artifacts 表实体
├── repository/
│   ├── ExportRequestRepository.java
│   └── ExportArtifactRepository.java
├── port/
│   └── ExportAggregatorPort.java      聚合 6 模块只读快照
├── event/
│   ├── ExportRequestedEvent.java
│   ├── ExportCompletedEvent.java      → notification 模块
│   └── ExportFailedEvent.java         → notification 模块
└── dto/
    ├── ExportCreateRequest.java       {module, format, range_start, range_end, filters}
    ├── ExportView.java                {id, status, progress_pct, file_url, expires_at}
    └── ExportDownloadView.java        {download_url, expires_at, sha256, size_bytes}
```

## 2. API 契约

### 2.1 提交导出请求

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| POST | `/api/exports` | `ExportCreateRequest` | `{data: ExportView}` | `VALIDATION_FAILED` / `RATE_LIMITED`（5 req/min/user） |

请求示例：
```json
{
  "module": "expense",
  "format": "csv",
  "range_start": "2026-01-01",
  "range_end": "2026-01-31",
  "filters": { "category_ids": [1, 2, 3] }
}
```

> **X2 修正说明**：用户决策 B —— 放宽 V21 CHECK 回 6 模块 + JSON（覆盖 task / daily_report / expense / meal / plan / ai + csv / json / markdown）。zip / pdf 在 CHECK 中保留作为 v1.1+ 预留但 v1.0 不投产；字段表恢复 6 模块，§5 / §6 JSON 测试保持现状。

### 2.2 查询导出状态

| Method | Path | Response | 错误码 |
|---|---|---|---|
| GET | `/api/exports/{id}` | `{data: ExportView}` | `EXPORT_NOT_FOUND` |
| GET | `/api/exports` | `{data: ExportView[], meta}`（仅当前用户） | — |

### 2.3 下载导出产物

| Method | Path | Response | 错误码 |
|---|---|---|---|
| GET | `/api/exports/{id}/download` | `{data: {download_url, expires_at, sha256, size_bytes}}` | `EXPORT_NOT_READY` / `EXPORT_EXPIRED` |

- 下载 URL：`/api/exports/{id}/artifact`（需要 JWT 或一次性签名 URL）
- 有效期：7 天（`retention_until = NOW() + 7d`），过期 → `EXPORT_EXPIRED`

## 3. 数据模型（V21 + V22）

### 3.1 export_requests

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | IDENTITY |
| `user_id` | BIGINT NOT NULL | 所有权 |
| `module` | TEXT NOT NULL | **task / daily_report / expense / meal / plan / ai**（X2 决策 B：恢复 6 模块，与 §5 模块导出列表对齐；json 已加回 format） |
| `format` | TEXT NOT NULL | **csv / json / markdown / zip / pdf**（X2 决策 B：json 恢复 + markdown 加入；v1.0 投产 csv / json / markdown，zip / pdf 预留 v1.1+） |
| `range_start` | DATE NULL | 时间范围起 |
| `range_end` | DATE NULL | 时间范围止 |
| `filters_json` | JSONB NULL | 模块特定过滤（category_ids / status / type 等） |
| `status` | TEXT NOT NULL | PENDING / PROCESSING / DONE / FAILED / CANCELLED（与 V21 status 机对齐）；BR-31：DONE 时 finished_at + expires_at NOT NULL；BR-34：DONE 时存在 1 条 export_artifacts 记录（`export_request_id ... UNIQUE` 约束保证 1:1，导出与产物 1:1 绑定） |
| `progress_pct` | SMALLINT DEFAULT 0 | 0~100（v1.1+；v1.0 简化为 PENDING → SUCCESS 直跳） |
| `attempts` | SMALLINT DEFAULT 0 | 重试次数 |
| `max_attempts` | SMALLINT DEFAULT 3 | 上限 |
| `error_code` | TEXT NULL | 失败错误码（`EXPORT_TIMEOUT` / `EXPORT_TOO_LARGE` / `EXPORT_IO_ERROR`） |
| `error_message` | TEXT NULL | 失败详情 |
| `requested_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | — |
| `started_at` | TIMESTAMPTZ NULL | Job 开始处理时间 |
| `finished_at` | TIMESTAMPTZ NULL | Job 结束时间 |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | — |

索引：
- `idx_export_user_status_created` ON `export_requests(user_id, status, created_at DESC)`（列表查询）
- `idx_export_status_pending` ON `export_requests(status) WHERE status = 'PENDING'`（Job 扫描）

CHECK 约束（与 data-model-v1.2-amendment V21 对齐，X2 决策 B）：
- `module IN ('task', 'daily_report', 'expense', 'meal', 'plan', 'ai')`
- `format IN ('csv', 'json', 'markdown', 'zip', 'pdf')`（v1.0 投产 csv / json / markdown；zip / pdf 为 v1.1+ 预留，文档标注）
- `status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'CANCELLED')`
- `range_start <= range_end`（若两者均非空）
- `progress_pct BETWEEN 0 AND 100`
- BR-31：`status='DONE'` → `finished_at IS NOT NULL`（**H1 修正**：export_requests 表无 expires_at 列；expires_at 由 export_artifacts.expires_at 承担，应用层在 status → DONE 时同步写入 `NOW() + 7d`，见 §3.2 line 149；跨表一致性留 v1.1 再讨论）
- BR-32：`status IN ('FAILED','CANCELLED')` → `finished_at IS NOT NULL`
- BR-33：`attempts <= max_attempts`

### 3.2 export_artifacts

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | IDENTITY |
| `export_request_id` | BIGINT NOT NULL UNIQUE REFERENCES export_requests(id) ON DELETE CASCADE |
| `file_path` | TEXT NOT NULL | `/backups/exports/lifewise-{userId}-{requestId}-{YYYYMMDD-HHMM}.{csv\|json}.gz` |
| `file_size_bytes` | BIGINT | 压缩后大小 |
| `row_count` | INTEGER | 导出数据行数 |
| `sha256` | TEXT | 文件校验（下载时回传） |
| `compression` | TEXT DEFAULT 'gzip' | v1.0 仅 gzip；v1.1+ 可选 zip |
| `expires_at` | TIMESTAMPTZ NOT NULL | 默认 NOW() + 7 天 |
| `download_count` | INTEGER DEFAULT 0 | 下载次数（审计） |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | — |

索引：
- `idx_export_artifact_expires` ON `export_artifacts(expires_at)`（清理 Job）

## 4. Outbox 事件（2 条 export.* 发布 + notify 消费）

| event_type | 触发源 | 消费方 | 行为 |
|---|---|---|---|
| `export.requested`（v1.1+） | export_requests INSERT | shared-integration / ExportJobRunner | 大文件异步触发（v1.0 简化为 controller 内 @Async） |
| `export.completed` | export_artifacts INSERT | notification 模块 | 推送 Web Push（`ai.report.done` 类型通知；用户可在通知中心下载） |
| `export.failed` | export_requests.status → FAILED | notification 模块 | 推送 Web Push（`export.failed` 类型通知；附 error_code / message） |

> **v1.0 简化**：POST /api/exports 在 controller 内 `@Async` 直接处理（小数据集 1 万行内秒级完成）；大文件或失败重试场景留给 v1.1+。

## 5. 模块导出内容（v1.0 范围）

### 5.1 task 导出

字段：`id / title / status / priority / due_at / completed_at / created_at / tags[] / parent_id / habit_id`
过滤：`status` / `priority` / `due_at range` / `tag_ids[]`

### 5.2 daily_report 导出

字段：`report_date / mood / content_md / highlights[].text / highlights[].position / ai_summary`
过滤：`report_date range`

### 5.3 expense 导出

字段：`occurred_at / amount_cents / currency / category.name / merchant / note / payment_method / budget_id`
过滤：`occurred_at range` / `category_ids[]` / `min_amount / max_amount`

### 5.4 diet（meal）导出

字段：`occurred_at / type / items[].food.name / items[].servings / items[].nutrition / total_calories / total_protein_g / total_fat_g / total_carbs_g`
过滤：`occurred_at range` / `type[]`

### 5.5 plan 导出

字段：`title / description / status / start_at / end_at / last_activity_at / milestones[].title / milestones[].due_at / milestones[].status / milestones[].progress{completed_tasks, total_tasks, pct} / linked_tasks[]`（N4：progress 由 ProgressService 实时聚合注入，对齐 plan-05-plan.md §2.4 ProgressView）
过滤：`status` / `category`

### 5.6 ai 导出

字段：`report_type / generated_at / model_version / content_md / evidence_refs[] / linked_aggregates[]`
过滤：`report_type` / `generated_at range`

## 6. 关键验收场景（TDD 种子）

### 6.1 提交导出请求

- `export_should_create_pending_request`：POST → export_requests INSERT（status=PENDING）
- `export_should_reject_invalid_module`：module ∉ 6 模块 → 400 `VALIDATION_FAILED`
- `export_should_reject_invalid_format`：format ∉ {csv, json, markdown, zip, pdf} → 400（与 V34 format CHECK 对齐，v1.0 投产 csv / json / markdown，zip / pdf 预留 v1.1+）
- `export_should_accept_markdown_format`：format=markdown → 200（v1.0 投产第三种格式）
- `export_should_validate_range`：range_start > range_end → 400
- `export_should_enforce_rate_limit`：第 6 次 1min 内 → 429（@RateLimit scope=export 5/min/user）

### 6.2 执行导出（v1.0 简化）

- `export_should_run_async_for_large_data`：row_count > 10000 → @Async 后台执行
- `export_should_run_inline_for_small_data`：row_count ≤ 10000 → 同步处理（小数据集秒级）
- `export_should_invoke_correct_aggregator`：module=expense → ExpenseAggregatorPort 拉快照
- `export_should_apply_filters`：filters.category_ids 生效 → 只导出指定分类
- `export_should_validate_user_ownership`：跨用户访问 → 404 `EXPORT_NOT_FOUND`
- `export_should_increment_progress`（v1.1+）：每 25% 更新 progress_pct

### 6.3 序列化

- `csv_should_have_utf8_bom`：CSV 文件首字节 EF BB BF（Excel 中文兼容）
- `csv_should_escape_quotes`：字段含 `"` → 转为 `""`
- `csv_should_escape_newlines`：字段含换行 → 双引号包裹
- `csv_should_include_header`：首行字段名 + 数据行
- `json_should_be_valid_json`：JSON.parse 成功
- `json_should_include_metadata`：JSON 含 generated_at / row_count / filters

### 6.4 失败处理

- `export_should_retry_on_aggregator_failure`：aggregator 抛异常 → 重试 3 次（1s/5s/30s 退避）
- `export_should_mark_failed_after_3_retries`：3 次失败 → status=FAILED + 写 `export.failed` 事件
- `export_should_record_error_code`：失败时 export_requests.error_code + error_message 写入
- `export_should_handle_disk_full`：写 artifact 失败 → status=FAILED + `EXPORT_IO_ERROR`

### 6.5 下载与清理

- `download_should_require_auth`：未登录 → 401
- `download_should_validate_ownership`：跨用户下载 → 404
- `download_should_return_signed_url`：返回一次性签名 URL（HMAC + 7d 过期）
- `download_should_increment_count`：每次下载 download_count++
- `cleanup_should_remove_expired_artifacts`：每日 04:30 删 expires_at < NOW 的 export_artifacts（PurgeExportJob，v1.1+；v1.0 复用 PurgeSoftDeletedJob）

### 6.6 速率限制

- `export_should_throttle_5_per_min`：第 6 次 1min 内 → 429 + Retry-After
- `export_should_distinguish_user_key`：不同用户互不影响

## 7. 验收标准

- [ ] V21 export_requests + V22 export_artifacts 表落地
- [ ] `export.completed` / `export.failed` 事件在 EventType 枚举注册
- [ ] 6 模块导出功能至少跑通 task / expense / plan 3 类
- [ ] CSV / JSON 两种序列化均支持
- [ ] 速率限制 5 req/min/user（@RateLimit scope=export）
- [ ] 7 天过期清理（v1.1+ PurgeExportJob；v1.0 手动或 PurgeSoftDeletedJob 扩展）
- [ ] 跨用户访问 → 404（userId 校验）
- [ ] Repository + Service 单测覆盖率 ≥ 80%
- [ ] 关键路径 100% 覆盖（提交 / 聚合 / 序列化 / 下载 / 失败重试）
- [ ] 大文件（10w+ 行）性能：v1.0 ≤ 30s；v1.1+ ≤ 5s

## 8. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 大数据量导出 OOM | 高 | v1.0 限制 10w 行 + 流式序列化；v1.1+ 异步 + 分批 |
| 导出文件磁盘满 | 高 | 监控 /backups/exports 目录 + 7d 过期清理 + 告警 |
| 跨用户下载 | 中 | HMAC 签名 URL + userId 校验 |
| 慢查询（聚合多模块） | 中 | 走 ReadPort + 索引 + 必要时缓存 |
| 导出请求堆积 | 中 | @RateLimit + admin 监控 + PENDING 计数告警 |
| 速率限制误伤正常用户 | 低 | 默认 5/min 宽松；可按 user 调优 |
| v1.0 同步导出阻塞 controller | 中 | 默认 inline + row_count 阈值切异步 |

## 9. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（导出文件磁盘挂载 + nginx /api/exports 路由）
  - `plan-data-flyway.md`（V21 export_requests + V22 export_artifacts）
  - `plan-shared-integration.md`（OutboxWorker 投递 export.completed / export.failed）
  - `plan-shared-infra.md`（@RateLimit scope=export + @Auditable）
  - `plan-01-task.md` ~ `plan-06-ai.md`（ReadPort 暴露给 export 模块）
- 下游：
  - `plan-notify.md`（监听 export.completed / export.failed 触发 Web Push）
  - `plan-observability-backup.md`（监控 export_requests 堆积 + 磁盘使用 + 导出失败率）
- 后续：
  - v1.1+ 大文件异步导出 + progress_pct + WebSocket 进度推送
  - v1.1+ 导出模板（按用户偏好自定义字段 / 排序 / 分组）
  - v1.1+ 导出历史与重导出（保留 30d）
