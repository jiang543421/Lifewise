# plan-06-ai 实施方案

## 参考资料

- [`docs/lifewise/specs/PRD/06-ai-insights.md`](../specs/PRD/06-ai-insights.md) — 产品 PRD
- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §3.4 AI 推理（Ollama deepseek:8b）+ §4 隐私约束
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.7 ai 模块边界
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) V25（ai_summaries 加固）+ V8（ai_jobs / ai_reports / chat_messages / chat_feedbacks）
- `CLAUDE.md` §7.6 隐私（AI 约束）

## 参考目录

- backend：`app/src/main/java/com/lifewise/ai/`
  - `controller/` — AiController / AiJobController / AiConsentController
  - `service/` — AiJobService / AiReportService / PromptBuilder / OllamaClient / ScopedDataFetcher / ConsentVerifier / AiRateLimiter / AiAuditLogger
  - `domain/` — AiJob / AiReport / ChatMessage（含 role=SYSTEM 审计消息）
  - `repository/` — AiJobRepository / AiReportRepository / ChatMessageRepository
  - `event/` — AiReportGenerated
  - `dto/` — AiJobRequest / AiJobView / AiReportView / ConsentRequest
  - `config/` — OllamaProperties / ai-data-scopes.yml
- frontend：`docs/lifewise/designs/06-ai-ui/`
  - `new-06-ai-ui.html` — AI 报告列表 + 单份报告详情 + 触发报告按钮

## 1. 模块边界 / 包结构

ai 模块是**最后做**的模块，负责聚合 5 个业务模块 + daily 的数据生成 AI 报告。所有 6 个业务模块 + auth 的事件最终都在这里被消费。

**AI 异步 Executor（H4 强制约束）**：

- `AiJobService.processAsync(jobId)` 必须用 `@Async("aiJobExecutor")`，**禁止**使用 plan-shared-infra 的通用 ThreadPoolTaskExecutor（core=8/max=16 会导致 deepseek:8b OOM）
- `aiJobExecutor` 规格以 [`technical-architecture.md` §3.4](../architecture/technical-architecture.md) 为权威：`core=2 / max=4 / queue=50 / rejection=CallerRuns`（CLAUDE.md §2.2 单用户串行约束）
- `AiJobService` 注入由 `plan-06-ai.config.AiAsyncConfig` 注册的 `aiJobExecutor` Bean，与 shared-infra 通用池完全隔离

```
ai/
├── controller/
│   ├── AiController.java              POST /api/ai/reports/generate + GET /api/ai/reports
│   ├── AiJobController.java           GET /api/ai/jobs/{id}（查询异步任务状态）
│   └── AiConsentController.java       POST /api/ai/consent（用户显式同意）
├── service/
│   ├── AiJobService.java              创建/查询任务（PENDING/RUNNING/DONE/FAILED/CANCELLED）
│   ├── AiReportService.java           报告 CRUD + 受限数据 → Ollama 推理
│   ├── PromptBuilder.java             根据 report_type 拼装 prompt（含数据上下文）
│   ├── OllamaClient.java              调用 Ollama /api/generate（流式 + 非流式）
│   ├── ScopedDataFetcher.java         根据 ai-data-scopes.yml 白名单安全获取数据（防越权）
│   ├── ConsentVerifier.java           校验 user_profiles.ai_consent = true
│   ├── AiRateLimiter.java             10 次/分钟（Redis 令牌桶）
│   └── AiAuditLogger.java             写 chat_messages（role=SYSTEM，不可变 append-only）
├── config/                            ← M3 补全
│   └── AiAsyncConfig.java             aiJobExecutor Bean 注册（core=2/max=4/queue=50/rejection=CallerRuns，technical-arch §3.4 权威；与 shared-infra 通用池完全隔离）
├── domain/
│   ├── AiJob.java                     ai_jobs 表实体（V8）
│   ├── AiReport.java                  ai_reports 表实体（V8）
│   └── ChatMessage.java               chat_messages 表实体（V8，含 role=SYSTEM 审计消息）
├── repository/
│   ├── AiJobRepository.java
│   ├── AiReportRepository.java
│   └── ChatMessageRepository.java     用 role=SYSTEM + message_metadata 存审计
├── event/
│   └── AiJobCompletedEvent.java       payload: {job_id, user_id, report_type, model_version, latency_ms}（与 event_type `ai.job.completed` 对齐）
└── dto/
    ├── AiJobRequest.java              {report_type, period_from, period_to, params?}
    ├── AiJobView.java
    ├── AiReportView.java              {job_id, report_type, content_md, referenced_entity_ids[], model_version}
    └── ConsentRequest.java            {ai_consent: boolean}
```

## 2. API 契约

### 2.1 AI 报告生成（2 个端点）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| POST | `/api/ai/reports/generate` | `AiJobRequest` | `{data: AiJobView}`（PENDING） | `CONSENT_REQUIRED`（BR-26）/ `RATE_LIMITED`（三重：`10 req/min/user + 60 req/h/user + 100 req/min/global`；shared-strings §7 'ai' scope；nginx 10r/m burst=2）/ `REPORT_TYPE_INVALID` / `INVALID_PERIOD` |
| GET | `/api/ai/reports` | query: `?report_type=&page=&limit=` | `{data: AiReportView[], meta}` | — |

支持的 `report_type`：
- `daily_summary` — 单日摘要（daily + task + habit）
- `weekly_summary` — 周报（5 模块聚合）
- `monthly_summary` — 月报（消费/饮食/计划完成率）
- `plan_review` — 计划合理性评估（plan + milestone 进度）
- `task_advice` — 任务建议（基于完成率 + 心情）

### 2.2 AI Job（2 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/ai/jobs/{id}` | — | `{data: AiJobView}` |
| GET | `/api/ai/jobs` | query: `?status=&page=` | `{data: AiJobView[], meta}` |

### 2.3 AI Chat（双路径问答，business §6.7 流程 7）

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| POST | `/api/ai/chat` | `{question: string, scope?: string}` | `{data: {answer_md, source: "RULE"\|"LLM", evidenceRefs[], sql?: string}}` | `RATE_LIMITED` / `CONSENT_REQUIRED` / `UNSAFE_SQL` |

**双路径实现**：
1. **规则优先**（`com.lifewise.ai.rule.RuleEngine.matches(question)`，本模块 §1.2 定义）→ 命中走预定义参数化查询，返回 `{answer_md, source: "RULE", evidenceRefs}`，P95 < 500ms
2. **LLM 后备**（Ollama 健康时）→ LLM 生成 SQL → AST + 白名单 + SELECT-only + LIMIT 校验 → 服务端注入 `userId` + `maxRows` → 执行只读查询 → 流式返回
3. **Ollama 不可用** → 仅规则路径可用，LLM 路径返回 `AI_UNAVAILABLE`

### 2.4 结构化报告 + PARTIAL 降级（business §6.6 流程 6）

`/api/ai/reports/generate` 流程：
```
1. 先调用 AnalysisDataProvider 拉取所有源域结构化数据
2. 任一源失败 → 生成 PARTIAL 报告（标注缺失模块）
3. 全部成功 → 生成完整结构化 JSON 报告
4. LLM 健康 + 用户同意 → 叠加 LLM 解读
5. LLM 失败/超时 → 以纯结构化数据完成（status=DONE_NO_LLM）
6. 结构化报告必填 evidenceRefs（指向源 aggregateId + 字段路径）
```

`ai_jobs.status` 扩展：
- `PENDING` / `RUNNING` / `DONE` / `FAILED` / `CANCELLED`（原有）
- `DONE_PARTIAL`：源数据缺失但生成部分报告
- `DONE_NO_LLM`：结构化完成但 LLM 跳过（健康/超时/不同意）

### 2.5 Consent（1 个端点）

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/ai/consent` | `ConsentRequest` | `{data: {ai_consent, consented_at}}`（写入 user_profiles） |

## 3. 数据模型（V8 + V25 加固）

| 表 | 关键字段 | BR |
|---|---|---|
| `ai_jobs` | `status ∈ {PENDING,RUNNING,DONE,FAILED,CANCELLED,DONE_NO_LLM,DONE_PARTIAL}`（X8：V31 7 态 CHECK；DONE_NO_LLM=Ollama 红色态/超时/不同意；DONE_PARTIAL=源数据缺失） / `prompt_hash` / `model_version` / `report_type` / `user_id` | BR-16/17 |
| `ai_reports` | `job_id` / `report_type` / `content_md` / `referenced_entity_ids JSONB` / `user_edited` | BR-18 |
| `chat_messages` | `role ∈ {USER,ASSISTANT,SYSTEM,TOOL}` / `conversation_id`（V25 加）/ `message_metadata JSONB` / `sql_executed`（审计专用字段） | BR-26 / V25 字段加固 |
| `chat_feedbacks` | `chat_message_id` / `rating ∈ {UP,DOWN}` / `comment` | — |
| `conversations` | AI 会话聚合根（V25，承载 `conversation_id`，chat_messages 按此聚合）/ `user_id` / `title` / `created_at` / `last_message_at` | BR-26 / V25 |

**`user_profiles.ai_consent`**（V2 已有字段）— 必须 true 才允许生成报告（BR-26）。

**AI 审计实现**：用 `chat_messages.role = 'SYSTEM'` + `message_metadata` JSONB（包含 `trace_id / decision_type / latency_ms / tokens_used`），由数据库角色限制 `UPDATE/DELETE` 权限实现不可变（BR-19/22 通过 DB GRANT 控制，不是表结构）。

**Ollama 健康探测**（business §6.6 + technical §3.8）：`OllamaHealthIndicator` 每 30s 主动探测一次（HTTP GET `/api/tags`），连续 2 次失败置**红色态**；恢复后立即置绿。**红色态行为**：`POST /api/ai/reports/generate` 仍受理（创建 PENDING 作业），但**不创建 LLM 作业**，仅完成结构化数据报告（status=`DONE_NO_LLM`）；`POST /api/ai/chat` 规则路径可用，LLM 路径返回 `AI_UNAVAILABLE`。

索引：
- `idx_ai_jobs_user_status_created` ON `ai_jobs(user_id, status, created_at DESC)`
- `idx_ai_reports_user_type_created` ON `ai_reports(user_id, report_type, created_at DESC)`
- `idx_chat_messages_user_role_created` ON `chat_messages(user_id, role, created_at DESC)`（含 role=SYSTEM 审计消息）
- `idx_chat_messages_conversation_created` ON `chat_messages(conversation_id, created_at DESC)`（V25 加）

## 4. 受限 SQL 白名单（ai-data-scopes.yml 配置 + 代码常量）

> **设计决策**：v1.2 修订范围内不引入独立 `ai_data_scopes` 表，白名单配置通过 `resources/ai-data-scopes.yml` + Java 常量类 `ScopedDataDefinitions` 维护；变更走 code review + 配置文件版本化，避免运行时配置漂移。**v1.3+ 评估迁回数据库表**（如果出现动态 scope 需求）。

```yaml
# resources/ai-data-scopes.yml
scopes:
  - table_name: tasks
    allowed_columns: [id, title, priority, status, completed_at]
    where_template: "user_id = :user_id AND deleted_at IS NULL"
    report_types: [daily_summary, weekly_summary, monthly_summary]   # X4：与 §2.1 report_type 枚举对齐（原 [daily, weekly, monthly] 与 API 不一致）
  - table_name: daily_reports
    allowed_columns: [id, report_date, mood, content_md]
    where_template: "user_id = :user_id AND report_date BETWEEN :from AND :to"
    report_types: [daily_summary, weekly_summary, monthly_summary]   # X4
  - table_name: expenses
    allowed_columns: [id, amount_cents, category_id, occurred_at]
    where_template: "user_id = :user_id AND occurred_at BETWEEN :from AND :to"
    report_types: [monthly_summary]                                  # X4
  - table_name: meals
    allowed_columns: [id, type, occurred_at, total_kcal_cents]
    where_template: "user_id = :user_id AND occurred_at BETWEEN :from AND :to"
    report_types: [weekly_summary]                                   # X4
  - table_name: milestones
    allowed_columns: [id, title, due_at, status, completed_at]
    where_template: "plan_id IN (SELECT id FROM plans WHERE user_id = :user_id)"
    report_types: [plan_review]
  - table_name: plans
    allowed_columns: [id, title, status, last_activity_at]
    where_template: "user_id = :user_id AND status = 'ACTIVE'"
    report_types: [plan_review]
```

**关键约束**：PromptBuilder **只能**通过 `ScopedDataFetcher` 按白名单读取数据，**严禁**拼接 SQL 字符串或直连 Repository。`ScopedDataDefinitions` 类在编译期加载 yml，运行时只读。

## 5. Outbox 事件（1 条发布 + 17 条订阅）

### 5.1 ai 模块发布（1 条）

| event_type | 触发 | payload | 消费方 |
|---|---|---|---|
| `ai.job.completed` | ai_jobs.status → **DONE / DONE_NO_LLM / DONE_PARTIAL**（X3 闭环：§2.4 与 §4 触发条件对齐；AiJobService.processAsync 在三态全部调 OutboxWriter.write(ai.job.completed)，payload 用 final_status 字段标识 DONE/DONE_NO_LLM/DONE_PARTIAL，避免 Ollama 红色态或源数据缺失时通知丢失） | `{job_id, user_id, report_type, final_status, model_version, latency_ms}` | user（SSE）+ notify + observability 监控 |

### 5.2 ai 模块订阅（17 条跨模块事件，全部走 Port 读）

| 来源 | 用途 |
|---|---|
| `task.completed / task.reopened / task.created / task.updated / habit.logged` | TaskReadPort 读 → 报告数据源 |
| `daily_report.created / updated / ai.summary.generated` | DailyReadPort 读 → 报告数据源 |
| `expense.created / budget.threshold` | ExpenseReadPort 读 → 报告数据源 |
| `meal.created` | DietReadPort 读 → 报告数据源 |
| `plan.created / milestone.created/updated/completed/missed` | PlanReadPort 读 + findStale → 报告触发源 + 14 天提醒 |
| `auth.user.registered` | 用户首次注册 → 引导开启 AI consent（不直接生成报告） |

注：ai 不直接消费事件触发报告生成；用户**显式调用 `/api/ai/reports/generate`** 才生成（BR-26 隐私保护）。

## 6. AI 推理流程（关键路径）

```
1. POST /api/ai/reports/generate {report_type, period_from, period_to}
   ↓
2. ConsentVerifier.check(userId) → user_profiles.ai_consent = true
   ↓
2.5. audit(CONSENT_CHECK, decision=APPROVED|DENIED) → chat_messages role=SYSTEM（4 类决策必留痕）
   ↓
3. AiRateLimiter.tryAcquire(userId) → 10/min Redis 令牌桶
   ↓
4. AiJobService.create() → INSERT ai_jobs (PENDING) + 返回 jobId
   ↓
5. 异步 @Async 任务：
   a. AiJobService.updateStatus(RUNNING) + audit(DATA_FETCH)
   b. ScopedDataFetcher.fetch(userId, reportType, period) → 按 ai-data-scopes.yml 白名单取数
   c. PromptBuilder.build(reportType, data, params) → 拼 prompt + 系统提示（含隐私约束）
   d. AiAuditLogger.log(MODEL_CALL, prompt_hash, model_version)
   e. OllamaClient.generate(prompt) → 流式获取 content_md
   f. AiReportService.save(jobId, contentMd, referencedEntityIds)
   g. AiJobService.updateStatus(DONE) + audit(GENERATE, latency_ms)
   h. OutboxWriter.write(ai.job.completed)
   ↓
6. 客户端通过 SSE 订阅 ai.job.completed 推送（可选）
```

## 7. 关键验收场景（TDD 种子）

### 7.1 Consent

- `ai_should_reject_when_consent_false`：ai_consent=false → `CONSENT_REQUIRED`（BR-26）
- `ai_should_set_consent_true`：POST /consent → user_profiles.ai_consent=true
- `ai_should_log_consent_decision`：audit 写 CONSENT_CHECK + decision=APPROVED/DENIED

### 7.2 RateLimit（三重防护，与 shared-strings §7 'ai' scope 对齐）

- `ai_should_allow_10_per_minute`：连发 10 次/分钟/user → 全部通过
- `ai_should_reject_11th_in_minute`：第 11 次/分钟/user → 429 `RATE_LIMITED`
- `ai_should_allow_60_per_hour_user`：连发 60 次/小时/user → 全部通过（与 10/min 双维度）
- `ai_should_reject_61st_in_hour_user`：第 61 次/小时/user → 429
- `ai_should_allow_100_per_minute_global`：全局配额（防 OOM 跨用户叠加）
- `ai_should_reject_101st_in_minute_global`：第 101 次/分钟全局 → 429
- `ai_should_reset_after_window`：Redis TTL 过期后 → 计数清零

### 7.3 ScopedDataFetcher（核心安全）

- `scoped_should_only_allow_listed_columns`：尝试读 `password_hash` → 抛 `COLUMN_NOT_ALLOWED`
- `scoped_should_inject_user_filter`：where_template 强制 `user_id = :user_id`
- `scoped_should_block_cross_user`：userId 替换为他人 → 0 行返回
- `scoped_should_use_period_range`：daily/weekly/monthly 按 report_type 取不同列
- `scoped_should_reject_unknown_table`：table_name 不在白名单 → `SCOPE_NOT_DEFINED`

### 7.4 PromptBuilder

- `prompt_should_include_privacy_notice`：系统提示含「不返回敏感字段」
- `prompt_should_include_data_context`：data 注入 prompt 上下文
- `prompt_should_truncate_to_token_limit`：超 4096 tokens → 截断
- `prompt_should_record_prompt_hash`：audit 写 prompt_hash（不存原文，BR-22）

### 7.5 OllamaClient

- `ollama_should_call_local_endpoint`：http://ai:11434/api/generate
- `ollama_should_handle_timeout`：timeout 30s → job FAILED
- `ollama_should_handle_model_unavailable`：deepseek:8b 未就绪 → 重试 3 次 + 失败
- `ollama_should_capture_latency`：audit latency_ms 准确

### 7.6 AiJob

- `job_should_set_pending_on_create`：创建 → PENDING
- `job_should_transition_to_running`：异步开始 → RUNNING
- `job_should_complete_on_success`：Ollama 返回 → DONE + report INSERT
- `job_should_fail_on_ollama_error`：Ollama 失败 → FAILED + error_message
- `job_should_be_idempotent`：同 (user_id, report_type, period) 当日不重复生成

### 7.6.1 final_status 三态触发（X3 闭环：§4 + §2.4 + §6 一致）

- `job_should_emit_done_when_ollama_ok`：LLM 健康 + 结构化数据齐 + 用户同意 → status=DONE + outbox 写 `ai.job.completed`，payload.final_status=DONE
- `job_should_emit_done_no_llm_when_ollama_red`：Ollama 红色态/超时/不同意 → status=DONE_NO_LLM + outbox 写 `ai.job.completed`，payload.final_status=DONE_NO_LLM
- `job_should_emit_done_partial_when_source_missing`：任一源失败（X3 PARTIAL 降级） → status=DONE_PARTIAL + outbox 写 `ai.job.completed`，payload.final_status=DONE_PARTIAL

### 7.7 Audit

- `audit_should_be_immutable`：无 UPDATE/DELETE 权限（DB 角色）
- `audit_should_record_all_decisions`：4 类决策全留痕
- `audit_should_include_trace_id`：trace_id 串联 4 步
- `audit_should_include_tokens_used`：model 调用 token 计数

### 7.8 Outbox

- `ai_should_emit_job_completed_event`：成功 → outbox 写 ai.job.completed（与架构 v1.1.1 §3.7.1 一致；payload 含 `job_id` 引用）
- `outbox_should_rollback_on_business_failure`：Ollama 失败 → ai_jobs FAILED + ai_reports 不写 + outbox 不写

### 7.9 UI（浏览器手动验证）

- `ui_ai_consent_banner_should_show`：未同意 → 顶部 banner + 引导
- `ui_ai_report_list_should_render`：报告列表 + 状态 badge
- `ui_ai_report_detail_should_show_content_md`：Markdown 渲染
- `ui_ai_generate_button_should_show_progress`：PENDING/RUNNING 状态进度
- `ui_ai_rate_limit_should_show_toast`：触发限流 → toast 提示

## 8. 验收标准

- [ ] 6 个 API 端点全部实现 + Swagger 文档（§2.1: 2 reports；§2.2: 2 jobs；§2.3: 1 chat；§2.5: 1 consent）
- [ ] 5 张表（ai_jobs / ai_reports / chat_messages / chat_feedbacks / conversations）Repository 单测覆盖率 ≥ 85%
- [ ] ai-data-scopes.yml 配置 6 个白名单条目落库（编译期加载）
- [ ] Ollama deepseek:8b 集成跑通（含 timeout / 重试）
- [ ] 10 次/分钟速率限制生效
- [ ] 关键路径 100% 覆盖（consent / scoped fetch / ollama call / audit）
- [ ] chat_messages role=SYSTEM 不可变（DB 角色 GRANT UPDATE/DELETE 拒绝验证）
- [ ] 1 条 Outbox 事件发布 + 17 条事件订阅全注册
- [ ] UI 主界面浏览器手动验证
- [ ] PRD 06 §BR 全部覆盖（BR-16/17/18/19/22/26/28）

## 9. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| Ollama 服务宕机导致报告失败 | 高 | timeout 30s + 重试 3 次 + job FAILED + 用户提示 |
| LLM 输出敏感数据 | 高 | 受限 SQL 白名单 + prompt 系统约束 + 输出过滤 |
| 用户数据泄露到云端 | 极高 | 强制本地 Ollama + outbound 网络隔离（CLAUDE.md §7.6） |
| AI 报告生成耗时长阻塞 | 中 | @Async 异步 + job 状态机 + SSE 推送 |
| Audit log 被篡改 | 高 | DB 角色只 INSERT + 归档定期导出 |
| 速率限制被绕过 | 中 | Redis 令牌桶 + IP+userId 双维度 |
| 跨用户数据越权（最严重） | 极高 | ScopedDataFetcher 强制 user_id 注入 + 集成测试 |
| 模型版本升级导致输出漂移 | 中 | prompt_hash + report_type 版本管理 + user_edited 标记 |

## 10. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（nginx 不代理 ai 容器，Ollama 仅内网）
  - `plan-data-flyway.md`（V8 四张 AI 表 + V25 ai_summaries 加固 + V35 chat_messages 回填）
  - `plan-shared-infra.md`（@RequireAuth + @RateLimit 注解复用）
  - `plan-shared-integration.md`（OutboxWriter + ApiResponse 信封）
  - `plan-auth.md`（userId 来源 + ai_consent 字段）
  - `plan-01-task.md`（**强依赖**：TaskReadPort）
  - `plan-02-daily.md`（**强依赖**：DailyReadPort + ai.summary.generated 触达 ai_jobs）
  - `plan-03-expense.md`（**强依赖**：ExpenseReadPort + budget.threshold）
  - `plan-04-diet.md`（**强依赖**：DietReadPort）
  - `plan-05-plan.md`（**强依赖**：PlanReadPort + findStale 提醒）
- 下游：
  - `plan-observability-backup.md`（ai_jobs 状态监控 + chat_messages role=SYSTEM 审计归档 + Ollama 健康检查）