# plan-02-daily 实施方案

## 参考资料

- [`docs/lifewise/specs/PRD/02-daily-report.md`](../specs/PRD/02-daily-report.md) — 产品 PRD
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.4 daily_report 模块边界
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V5（daily_reports / highlights / ai_summaries）+ V11（按月分区）
- [`docs/lifewise/designs/02-daily-ui/02-daily-ui-design.md`](../designs/02-daily-ui/02-daily-ui-design.md) — UI 设计契约
- [`docs/lifewise/architecture/versions/data-model-design-v1.1.1.md`](../architecture/versions/data-model-design-v1.1.1.md) §1.1.4 日报模块字段

## 参考目录

- backend：`app/src/main/java/com/lifewise/daily/`
  - `controller/` — DailyReportController / HighlightController / SearchController / SummaryController
  - `service/` — DailyReportService / HighlightService / SearchService / SummaryService / MoodStatsService
  - `domain/` — DailyReport / DailyReportHighlight / AiSummary
  - `repository/` — DailyReportRepository / HighlightRepository / AiSummaryRepository
  - `port/` — DailyReadPort（暴露给其他模块）
  - `event/` — DailyReportCreated / DailyReportUpdated / AiSummaryGenerated
  - `dto/` — DailyReportCreateRequest / DailyReportView / HighlightRequest / SearchQuery / SearchResult
- frontend：`docs/lifewise/designs/02-daily-ui/`
  - `new-02-daily-ui.html` — 主界面原型（时间线 + 编辑器 + 心情选择）

## 1. 模块边界 / 包结构

daily 模块是用户**每日记录**的入口，承接心情 / 内容 / 亮点三类数据，并通过 ai_summaries 集成手动 AI 摘要。

```
daily/
├── controller/
│   ├── DailyReportController.java     /api/daily-reports CRUD（按月分区）
│   ├── HighlightController.java       /api/daily-reports/{id}/highlights
│   ├── SearchController.java          /api/daily-reports/search?q=...&from=...&to=...
│   └── SummaryController.java         /api/daily-reports/{id}/summary（手动触发 AI）
├── service/
│   ├── DailyReportService.java        创建/更新/查询（按 report_date 自然日）
│   ├── HighlightService.java          ≤3 亮点排序（BR-08）
│   ├── SearchService.java             全文检索（tsvector）+ 时间范围
│   ├── SummaryService.java            手动触发 AI 摘要（异步）
│   └── MoodStatsService.java          心情统计（周/月均值）
├── domain/
│   ├── DailyReport.java               daily_reports 表实体（按月分区）
│   ├── DailyReportHighlight.java      daily_report_highlights 实体
│   └── AiSummary.java                 ai_summaries 实体
├── repository/
│   ├── DailyReportRepository.java
│   ├── HighlightRepository.java
│   └── AiSummaryRepository.java
├── port/
│   └── DailyReadPort.java             实现 DailyReadPortAdapter
├── event/
│   ├── DailyReportCreatedEvent.java   payload: {report_id, user_id, report_date, mood}
│   ├── DailyReportUpdatedEvent.java   payload: {report_id, user_id, change_type}
│   └── AiSummaryGeneratedEvent.java   payload: {report_id, user_id, model_version, generated_at}
└── dto/
    ├── DailyReportCreateRequest.java  {report_date, mood, content_md, weather?}
    ├── DailyReportUpdateRequest.java
    ├── DailyReportView.java           完整视图（含 highlights + summary）
    ├── HighlightRequest.java          {tag, position}
    ├── SearchQuery.java               {q, from, to, page, limit}
    └── SearchResult.java              {data: DailyReportView[], meta}
```

## 2. API 契约

### 2.1 DailyReport CRUD（6 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/daily-reports` | query: `?year=&month=&page=&limit=` | `{data: DailyReportListItem[], meta}` | — |
| GET | `/api/daily-reports/{id}` | — | `{data: DailyReportView}` | `DAILY_REPORT_NOT_FOUND` |
| GET | `/api/daily-reports/by-date/{date}` | — | `{data: DailyReportView}` | `DAILY_REPORT_NOT_FOUND` |
| POST | `/api/daily-reports` | `DailyReportCreateRequest` | `{data: DailyReportView}` | `DUPLICATE_DATE`（BR-06）/ `VALIDATION_FAILED` / `CONTENT_TOO_LONG`（BR-25） |
| PUT | `/api/daily-reports/{id}` | `DailyReportUpdateRequest` | `{data: DailyReportView}` | `DAILY_REPORT_NOT_FOUND` |
| DELETE | `/api/daily-reports/{id}` | — | `{message: "ok"}` | `DAILY_REPORT_NOT_FOUND`（软删） |

### 2.2 Highlight（4 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| GET | `/api/daily-reports/{id}/highlights` | — | `{data: HighlightView[]}` | — |
| POST | `/api/daily-reports/{id}/highlights` | `HighlightRequest` | `{data: HighlightView}` | `HIGHLIGHT_LIMIT_EXCEEDED`（BR-08）/ `INVALID_POSITION` |
| PUT | `/api/daily-reports/{id}/highlights/{hid}` | `HighlightRequest` | `{data: HighlightView}` | — |
| DELETE | `/api/daily-reports/{id}/highlights/{hid}` | — | `{message: "ok"}` | — |

### 2.3 Search（1 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/daily-reports/search` | query: `?q=&from=&to=&page=&limit=` | `{data: SearchHit[], meta}` |

返回：`{report_id, report_date, snippet(高亮), match_score}`，全文检索 tsvector + 时间范围 + 用户隔离。

### 2.4 Summary（手动 AI 摘要，2 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/daily-reports/{id}/summary` | — | `{data: {job_id, status: "PENDING"}}` |
| GET | `/api/daily-reports/{id}/summary` | — | `{data: AiSummaryView}` 或 404 |

AI 摘要走 ai_jobs（plan-06-ai），本模块只触发。

### 2.5 DailyReadPort（跨模块只读契约）

```java
public interface DailyReadPort {
    Optional<DailyReportSnapshot> findByDate(Long userId, LocalDate date);
    List<DailyReportSnapshot> findInRange(Long userId, LocalDate from, LocalDate to);
    Double averageMoodInRange(Long userId, LocalDate from, LocalDate to);
    long countReportsInRange(Long userId, LocalDate from, LocalDate to);
}
```

## 3. 数据模型（V5 + V11）

| 表 | 关键字段 | BR |
|---|---|---|
| `daily_reports` | `report_date DATE` / `mood NUMERIC(2,1)` / `content_md TEXT(≤50000)` / `weather TEXT` / `UNIQUE(user_id, report_date)` / `ai_summary TEXT NULL` / `is_draft BOOLEAN NOT NULL DEFAULT TRUE`（V32 新增，I1 修正：原文标 X3 应为 X1，是 X1 修复加 is_draft 列 + CHECK 改写后的字段去重） | BR-06/07/21.b/25 |
| `daily_report_highlights` | `position ∈ 1..3` + UNIQUE | BR-08 |
| `ai_summaries` | `daily_report_id NOT NULL` / `summary_md` / `model_version NOT NULL`（V25 收紧） / `generated_at NOT NULL`（V25 收紧） / `user_edited BOOLEAN` | BR-21.a/b/c |

**分区**：`daily_reports` 按月分区（V11），分区键 `report_date`。

索引：
- `uq_daily_reports_user_date`（BR-06）
- `idx_daily_reports_user_date`（按日期范围查询）
- GIN 索引 `idx_daily_reports_content_tsv` ON `to_tsvector('simple', content_md)`（全文检索）
- `idx_highlights_report` ON `daily_report_highlights(daily_report_id, position)`

## 4. Outbox 事件（3 条）

| event_type | 触发 | payload | 消费方 |
|---|---|---|---|
| `daily_report.created` | daily_reports INSERT | `{report_id, user_id, report_date, mood}` | ai（统计 / 摘要候选） |
| `daily_report.updated` | daily_reports UPDATE | `{report_id, user_id, change_type}` | ai（重新评估） |
| `ai.summary.generated` | ai_summaries INSERT | `{report_id, user_id, model_version, generated_at}` | user（SSE 推送） |

注：消费 `habit.logged`（来自 task 模块）用于日报亮点推荐（不直接修改日报）。

## 5. 关键验收场景（TDD 种子）

### 5.1 DailyReport CRUD

- `daily_create_should_set_default_mood_null`：未指定 mood → NULL
- `daily_create_should_reject_mood_invalid`：非 {1, 1.5, 2, ..., 5} → 400（BR-07）
- `daily_create_should_reject_content_too_long`：> 50000 字符 → `CONTENT_TOO_LONG`（BR-25）
- `daily_create_should_reject_duplicate_date`：同 user 同 date 已存在 → `DUPLICATE_DATE`（BR-06）
- `daily_update_should_preserve_report_date`：update 不允许改 report_date
- `daily_query_should_filter_by_year_month`：按年/月切片正确
- `daily_query_should_partition_prune`：查询 7 月只命中 `daily_reports_2026_07` 分区
- `daily_by_date_should_return_existing`：按日期查找唯一日报
- `daily_delete_should_soft_delete`：deleted_at 写入 + summary 级联软删

### 5.2 Highlight

- `highlight_create_should_reject_position_invalid`：非 1..3 → 400（BR-08）
- `highlight_create_should_reject_when_3_exists`：已有 3 条 → `HIGHLIGHT_LIMIT_EXCEEDED`（BR-08）
- `highlight_should_be_unique_per_position`：同 position 重复 → 400
- `highlight_delete_should_clear_position`：删除后该位置可被复用

### 5.3 Search

- `search_should_match_content_fulltext`：q=关键词 → 返回 tsvector 匹配
- `search_should_highlight_snippet`：返回 snippet 含 `<em>` 标签
- `search_should_filter_by_date_range`：from/to 范围生效
- `search_should_paginate`：分页正确
- `search_should_isolate_user`：用户隔离（userId 不匹配 → 不返回）

### 5.4 Summary

- `summary_should_create_pending_job`：POST → ai_jobs PENDING 状态
- `summary_should_return_existing`：GET 第二次返回已生成
- `summary_should_mark_user_edited`：用户在 UI 修改 → user_edited=true
- `summary_should_publish_event`：生成 → outbox 写 ai.summary.generated

### 5.5 Outbox

- `daily_should_emit_created_event`：创建 → daily_report.created
- `daily_should_emit_updated_event`：更新 → daily_report.updated
- `outbox_should_rollback_on_business_failure`：service 异常 → outbox 不写入

### 5.6 Port（其他模块集成）

- `port_should_find_by_date`：ai 模块调 `findByDate` → 返回 snapshot
- `port_should_average_mood_in_range`：心情周均值
- `port_should_count_reports_in_range`：日活跃统计

### 5.7 UI（浏览器手动验证）

- `ui_daily_editor_should_show_mood_selector`：心情 1-5 半星选择
- `ui_daily_should_render_timeline`：左侧时间线正确显示
- `ui_highlight_should_drag_to_reorder`：拖拽改变 position
- `ui_search_should_show_snippet`：搜索结果高亮
- `ui_responsive_mobile`：移动端编辑器自适应

## 6. 验收标准

- [ ] 13 个 API 端点全部实现 + Swagger 文档
- [ ] 3 张表（含 1 个分区表）Repository 单测覆盖率 ≥ 85%
- [ ] 全文检索 GIN 索引就位
- [ ] 3 条 Outbox 事件注册到 EventType 枚举
- [ ] DailyReadPort 暴露给其他模块
- [ ] tsvector 触发器在 INSERT/UPDATE 时自动更新
- [ ] 关键路径 100% 覆盖（创建 / 摘要触发 / 搜索）
- [ ] UI 主界面浏览器手动验证
- [ ] PRD 02 §BR 全部覆盖（BR-06/07/08/21/25）

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| tsvector 索引膨胀 | 中 | `simple` 配置（不分词）+ 长度限制（BR-25） |
| 按月分区写入失败阻塞 | 高 | 事务包裹 + 自动预建 3 月分区 |
| 全文检索性能 | 中 | GIN 索引 + LIMIT 限制 |
| 心境周统计跨时区 | 中 | 强制 `user.timezone` |
| summary 重复触发 | 低 | ai_jobs 幂等键（report_id + 当日） |
| 软删后 summary 残留 | 中 | 级联软删（BR-21 应用层） |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`
  - `plan-data-flyway.md`（V5 + V11 分区）
  - `plan-shared-infra.md`
  - `plan-shared-integration.md`
  - `plan-auth.md`
  - `plan-01-task.md`（消费 habit.logged → 亮点推荐）
  - `plan-observability-backup.md`（daily_reports.is_draft 归档 + ai.summary.generated 事件流 + chat_messages role=SYSTEM 审计）
- 下游：
  - `plan-06-ai.md`（消费 daily_report.* + DailyReadPort + ai_jobs 触发源）