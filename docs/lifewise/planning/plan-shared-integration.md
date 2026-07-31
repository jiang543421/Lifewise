# plan-shared-integration 实施方案

## 参考资料

- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §2 架构原则（事务性 Outbox / 依赖单向化 / 契约优先）
- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §3 事件投递与异步处理
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) §0 outbox_events 主键 + V33 事件枚举扩展（4 条 auth.*，配合 V23 已有的 2 条 export.* + 1 条 notification.requested，CHECK 共收 25 项事件，见 shared-strings §1）
- `CLAUDE.md` §4.5 HTTP API 响应格式（统一信封）

## 参考目录

- backend：`app/src/main/java/com/lifewise/shared/integration/`
  - `outbox/` — OutboxWriter + OutboxWorker + 重试 + 死信
  - `event/` — 事件枚举 + payload schema + version 管理
  - `port/` — 跨模块只读接口（Port interface）
  - `dto/` — 统一响应信封 + 分页 meta + 错误码
- frontend：—（前端不参与本模块）

## 1. 模块边界 / 包结构

shared-integration 是**6 业务模块间唯一允许的协作通道**。四块组成：

> **架构裁决待定（事件粒度）**：business-architecture §5.4 锁定 MVP 仅 5 个粗粒度事件（`TaskCompleted.v1` / `NotificationRequested.v1` / `AnalysisCompleted.v1` / `ExportCompleted.v1` / `SourceDataChanged.v1`）；本文件 §4 沿用 25 条细粒度事件（task.* / daily_report.* / habit.logged / meal.* / expense.* / budget.threshold / plan.* / milestone.* / ai.* / auth.* / export.* / notification.*）。**两种粒度各有取舍**：粗粒度减复杂度但增加消费方耦合，细粒度解耦但需更多 schema 版本管理。架构组裁决前，本文件 §4 暂保留 25 条细粒度；后续如切到粗粒度，§4 事件表需重写。

```
shared/integration/
├── outbox/
│   ├── OutboxWriter.java              @Transactional(MANDATORY) + ObjectMapper 序列化 payload
│   ├── OutboxWorker.java              @Scheduled 轮询 pending 事件（默认 lag ≤ 1s，可由 outbox.poll.ms 覆盖）
│   ├── OutboxDispatcher.java          按 event_type 路由到订阅者
│   ├── OutboxStatus.java              PENDING / DISPATCHED / DISCARDED 内存枚举（不持久化）
│   ├── OutboxConfig.java              @Configuration：把 WorkerConfig 暴露为 Bean
│   ├── OutboxEventRepository.java     4 方法接口（save / findById / findPendingBatch / markDispatched）
│   └── persistence/
│       └── JpaOutboxEventRepository.java  NamedParameterJdbcTemplate + JSONB CAST + GeneratedKeyHolder
├── event/
│   ├── EventType.java                 事件名枚举（25 条；命名粒度待架构裁决，见 §1 注）
│   ├── EventVersion.java              事件 schema 版本管理
│   ├── EventEnvelope.java             {eventId, eventType, eventVersion, occurredAt, userId, aggregateId, correlationId, causationId, payload, traceId}（与 business-architecture §5.3 对齐）
│   └── payload/
│       ├── TaskCompletedPayload.java
│       ├── ExpenseCreatedPayload.java
│       └── ...
├── port/
│   ├── TaskReadPort.java              task 模块只读视图给其他模块用
│   ├── DailyReadPort.java
│   ├── ExpenseReadPort.java
│   ├── MealReadPort.java
│   ├── PlanReadPort.java
│   └── AiReadPort.java
└── dto/
    ├── ApiResponse.java               {success, data, error, meta}
    ├── PageMeta.java                  {total, page, limit, has_next}
    ├── ErrorEnvelope.java             {code, message, trace_id, details}
    └── ErrorCode.java                 错误码常量
```

## 2. API 契约

### 2.1 暴露给业务模块的注解 / 接口

| 名称 | 类型 | 行为 |
|---|---|---|
| `@Outbox(eventType = "task.completed")` | 方法注解 | 方法返回后，同事务写 outbox_events |
| `EventType` | 枚举 | 25 条事件名常量 |
| `OutboxWorker.dispatch()` | 内部 | 轮询 + 派发（业务模块不直接调用） |
| `XxxReadPort` | interface | 跨模块只读（无写方法） |
| `ApiResponse.ok(data)` | 静态工厂 | Controller 统一返回 |

### 2.2 跨模块只读契约（Port）

```java
// task 模块提供，其他模块消费（契约以 plan-01-task.md §2.4 为权威）
// 范围：仅暴露跨模块通用读操作；任务内部统计 / 高级查询走 plan-01-task 自身 service（避免共享层膨胀）
public interface TaskReadPort {
    Optional<TaskSnapshot> findById(Long userId, Long taskId);     // userId 校验
    List<TaskSnapshot> findByIds(Long userId, List<Long> taskIds); // 批量查询
    List<TaskSnapshot> findByPlanId(Long userId, Long planId);     // 多对一
    long countCompletedSince(Long userId, Instant since);          // 统计（plan 进度）
}
```

规则：
- 入参必须带 `userId`，内部校验所有权
- 只暴露 snapshot（不可变 record），不暴露 JPA entity
- 抛出 `ResourceNotFoundException` 而非返回 null（业务层显式处理）

### 2.3 统一响应信封

```json
// 成功
{
  "success": true,
  "data": { ... },
  "error": null,
  "meta": { "total": 100, "page": 1, "limit": 20, "has_next": true }
}

// 失败
{
  "success": false,
  "data": null,
  "error": { "code": "TASK_NOT_FOUND", "message": "...", "trace_id": "..." },
  "meta": null
}
```

## 3. 数据模型

### 3.1 outbox_events（V2 + V30 + V33 实际列；path B 修订）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | IDENTITY（V30 分区表主键之一） |
| `occurred_at` | TIMESTAMPTZ PK | V2 分区键（按月 RANGE 分区） |
| `user_id` | BIGINT NOT NULL | 所有权 + Worker 分片键（BR-22） |
| `aggregate_type` | TEXT | Task / Expense / Meal 等 |
| `aggregate_id` | BIGINT | 业务实体 ID |
| `event_type` | TEXT | 事件名（25 条白名单，V33） |
| `event_version` | INTEGER DEFAULT 1 | schema 版本（V30 新增） |
| `correlation_id` | TEXT NULL | 链路追踪：跨服务调用同一根 ID（V30 新增；envelope UUID 入库前 toString） |
| `trace_id` | TEXT NULL | 调用链 trace ID（V30 新增） |
| `payload` | JSONB | EventEnvelope 内容（INSERT 用 `CAST(:p AS jsonb)`） |
| `published_at` | TIMESTAMPTZ NULL | **path B 用此列判 PENDING/DISPATCHED**（`NULL = PENDING`，`NOT NULL = DISPATCHED`） |

**path B 不持久化字段**（不在表内，仅在内存 / Java record 流转）：
- `causationId`（envelope 上是 UUID，DB 列是 BIGINT；语义错位，v1.1 评估）
- `attemptCount`（Worker 内存 `Map<Long,Integer>`，进程重启归零）

索引（沿用 plan-data-flyway 已落地）：
- `idx_outbox_user_pending WHERE published_at IS NULL`（部分索引，BR-22）
- `UNIQUE (aggregate_type, aggregate_id, event_type)`（BR-16 去重；注：v1.0 不强制启用，等跨模块实际重复事件后再添加）

### 3.2 ~~outbox_dead_letter~~（path B 不引入；推迟至 v1.1-amendment）

v1.0 不创建 `outbox_dead_letter` 表。失败 3 次后仅记 ERROR 日志 + 跳过；行保持 `published_at IS NULL` 由 admin 通过 SQL 手动 `UPDATE ... SET published_at = now()` 介入。

v1.1-amendment 待评估：
- `outbox_dead_letter` 表 + `moveToDeadLetter()` 自动化
- `outbox_events.retry_count` / `next_attempt_at` 列 + backoff 调度
- Worker 分布式锁（`pg_try_advisory_lock`）避免多副本重复派发

理由（CLAUDE.md §4.1 YAGNI）：单机单用户触发死信 = 设计失败；v1.0 范围内失败兜底 = admin 介入 + 日志。

## 4. Outbox 事件清单（25 条 = 13 业务 + 3 daily_report 补 + 2 task/milestone CU 拆 + 1 plan + 2 ai 补 + 2 export + 1 notification + 4 auth.* = 25 条）

> 修订说明：v1.0 草案列出 16 条事件；plan-02-daily.md §4 实际定义 3 条 daily_report 事件（daily_report.created / updated + ai.summary.generated），本节同步补齐 + V23 引入 2 条 export.* + 1 条 notification.requested + V33 引入 4 条 auth.*，事件清单更新为 **25 条**（与 references/shared-strings.md §1 对齐）。

| event_type | 触发源 | 消费方 | version |
|---|---|---|---|
| `task.completed` | tasks.status → DONE | plan | 1 |
| `task.reopened` | tasks.status → OPEN | plan | 1 |
| `task.created` | tasks INSERT（CU，**无 task.deleted**） | plan（BR-30 刷新 last_activity_at） | 1 |
| `task.updated` | tasks UPDATE | plan | 1 |
| `milestone.created` | milestones INSERT（CU，**无 milestone.deleted**） | plan（BR-30） | 1 |
| `milestone.updated` | milestones UPDATE | plan | 1 |
| `milestone.completed` | milestones.status → DONE | ai, daily_report | 1 |
| `milestone.missed` | MissedMilestoneJob | ai | 1 |
| `habit.logged` | habit_logs INSERT | daily_report, ai | 1 |
| `daily_report.created` | daily_reports INSERT | ai | 1 |
| `daily_report.updated` | daily_reports UPDATE | ai | 1 |
| `ai.summary.generated` | ai_summaries INSERT | user（SSE） | 1 |
| `meal.created` | meals INSERT | ai | 1 |
| `expense.created` | expenses INSERT | ai, **notify**（X7：原 push_subscriptions 直接消费已废弃，统一走 notify 模块） | 1 |
| `budget.threshold` | BudgetEvaluatorJob（INSERT notification_requests）→ emit outbox event | **notify**（消费事件触发 Web Push；X7：原 push_subscriptions 直接消费已废弃，统一走 notify 模块） | 1 |
| `plan.created` | plans INSERT | ai | 1 |
| `ai.job.completed` | ai_jobs.status → **DONE / DONE_NO_LLM / DONE_PARTIAL**（X3：三态均触发，避免 Ollama 红色态或源数据缺失时通知丢失） | user（SSE） + notify | 1 |
| `ai.report.feedback` | chat_feedbacks INSERT | — | 1 |
| `export.completed` | export_artifacts INSERT | user | 1 |
| `export.failed` | export_requests.status → FAILED | user | 1 |
| `notification.requested` | notification_requests INSERT | notification_deliveries | 1 |
| **`auth.user.registered`** | users INSERT（auth 模块） | notify（欢迎通知）+ ai（用户冷启动数据收集） | 1 |
| **`auth.user.logged_in`** | refresh_tokens 写入（auth 模块） | notify（异地登录告警） | 1 |
| **`auth.user.password_reset_requested`** | password_resets INSERT（auth 模块） | notify（邮件 + Web Push 双通道） | 1 |
| **`auth.token.reuse_detected`** | JwtRefreshService 检测到 reuse（auth 模块） | **强制下线该 family + Web Push 安全告警**（plan-shared-infra §1 JwtRefreshTokenService.revokeFamily 触发；CVE 关键安全事件） | 1 |

## 5. 关键验收场景（TDD 种子）

### 5.1 outbox（path B 修订）

> 命名空间同步：`processed_at` → `published_at`；`outbox_dead_letter` 推迟至 v1.1-amendment。

- `outbox_should_write_in_same_transaction`：业务 INSERT + outbox INSERT 同事务（`Propagation.MANDATORY`）；业务回滚 → outbox 也回滚
- `outbox_should_poll_pending_events`：Worker 默认每 1s 拉取 `published_at IS NULL` 事件（`outbox.poll.ms` 可覆盖）
- `outbox_should_mark_dispatched_atomic`：派发成功后 UPDATE `published_at = now()` 用 `WHERE id = ?` 防并发
- `outbox_should_dispatch_to_subscriber`：按 event_type 路由到对应订阅者（fan-out）
- `outbox_should_retry_in_memory_on_failure`：订阅者失败 → 内存 `Map<Long,Integer> attempts`++；不搬死信
- `outbox_should_discard_after_max_attempts`：attempts ≥ maxRetries 时记 ERROR 日志 + 跳过；行仍 PENDING
- `outbox_should_skip_already_processed`：并发 Worker 通过行锁（`SELECT ... FOR UPDATE SKIP LOCKED`）避免重复派发（v1.0 暂由单进程假设保证；v1.1 加分布式锁）
- `outbox_should_dedupe_by_aggregate`：BR-16 唯一约束防重复（v1.0 约束在 schema 层预留，运行时去重由 v1.1 评估）
- `outbox_should_serialize_payload_via_object_mapper`：H2 不变式 — payload 经 Jackson `writeValueAsString` 写入 JSONB，绝不 `Map.toString()`
- `outbox_should_deserialize_payload_from_jsonb`：H3 不变式 — 派发时反序列化为 `Map<String,Object>`，不是 `Map.of("_raw", rawString)`
- `shared_integration_context_should_load`：M2 — `@SpringBootTest` 验证 outbox 子包全部 Bean 接线（`OutboxWriter` / `OutboxDispatcher` / `OutboxWorker` / `OutboxEventRepository`）

### 5.2 port

- `port_should_expose_readonly_view`：编译期只读（接口无写方法）
- `port_should_reject_cross_user_access`：userId 不匹配抛 `ResourceNotFoundException`
- `port_should_return_snapshot_not_entity`：返回 `record` 而非 JPA entity
- `port_should_handle_empty_optional`：业务显式处理 `Optional.empty()`

### 5.3 dto

- `dto_should_wrap_success_response`：成功响应格式正确
- `dto_should_wrap_error_response`：失败响应 `data=null` / `error.code/message/trace_id`
- `dto_should_paginate_with_meta`：分页响应含 `total / page / limit / has_next`
- `dto_should_omit_meta_when_not_paginated`：非分页接口 `meta=null`

### 5.4 event

- `event_should_validate_payload_schema`：JSON Schema 校验 payload 必填字段
- `event_should_increment_version_on_breaking_change`：破坏性 schema 变更 version+1
- `event_should_serialize_to_jsonb`：payload 正确序列化为 JSONB

## 6. 验收标准（path B 修订）

- [ ] Outbox 写入与业务数据同一事务（ACID，`Propagation.MANDATORY`）
- [ ] Worker 轮询 lag ≤ 1s（P99，默认 `outbox.poll.ms=1000`）
- [x] ~~失败重试 3 次后进入死信~~ → 改为「失败重试 3 次后日志 ERROR + 跳过；行保持 PENDING 由 admin 介入」
- [ ] 跨模块只读接口禁止写操作（编译期 + 运行期双校验）
- [ ] 统一响应信封 100% 覆盖（code review 检查）
- [ ] 25 条事件全部注册到 EventType 枚举
- [ ] Outbox 模块单测覆盖率 ≥ 80%（关键路径：dispatch / retry / discard）
- [ ] Spring Context 装配验证（M2：`SharedIntegrationContextTest` 通过）
- [ ] ~~死信表监控告警~~ → 推迟至 v1.1-amendment

## 7. 风险登记（path B 修订）

| 风险 | 等级 | 缓解 |
|---|---|---|
| Outbox 事件堆积 | 高 | 监控 `outbox_pending_count` Prometheus gauge；阈值告警 |
| 消费失败导致 ~~死信暴涨~~ → 行 PENDING 堆积 | 高 | ~~死信表每日 review~~ → admin 通过 SQL `UPDATE outbox_events SET published_at = now() WHERE id IN (...)` 手动介入；监控 outbox_pending_count 自动告警 |
| 事件 schema 演进破坏消费方 | 中 | version 字段；v+1 时旧消费者保留兼容期 |
| Port 误暴露写方法 | 中 | 接口命名规范（`*ReadPort`）；code review 检查 |
| Worker 单点 | 中 | ~~任务调度器分布式锁（PG `pg_try_advisory_lock`）~~ → v1.0 单进程假设；v1.1-amendment 加 advisory lock |
| JSONB 索引性能 | 低 | GIN 索引仅在高频查询字段 |
| attemptCount 进程重启归零 | 低 | v1.0 接受（admin 介入兜底）；v1.1 引入 DB 列 |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（Redis 容器）
  - `plan-data-flyway.md`（**仅 outbox_events 表**；path B 不引入 `outbox_dead_letter`）
  - `plan-shared-infra.md`（@RequireAuth 在 Port 调用前生效）
- 下游：
  - `plan-auth.md`（登录事件发布到 outbox）
  - `plan-01-task.md` ~ `plan-06-ai.md`（含 `plan-auth.md`，共 7 个模块，全部通过 Outbox + Port 协作）
  - `plan-observability-backup.md`（监控 outbox_pending_count）

## 9. v1.0 path B 修订摘要（commit-pending）

**触发**：code review 发现 `OutboxEventRecord` 与实际 V2/V30/V33 schema 不匹配（14 字段 UUID PK + status + retry_count vs 11 字段 BIGINT PK + published_at）。两条修复路径可选：

- **Path A**：补 V36/V37 Flyway 迁移（增加 `status` / `retry_count` / `next_attempt_at` 列 + `outbox_dead_letter` 表）
- **Path B**（已选）：收缩 Java 代码到 DB 实际能支持的最小 outbox

**决策依据**（CLAUDE.md §4.1 YAGNI + §10 红线）：
1. YAGNI：单机单用户触发死信 = 设计失败；死信表 + retry 列是「为运维擦屁股」设计，v1.0 范围内失败兜底 = admin 介入 + 日志。
2. 红线（CLAUDE.md §10）：加 V36/V37 =「修改数据库表结构」，必须先评审 + 迁移；当前 Java 代码假设未经评审的列是**实现错位**不是 schema 缺漏。
3. 现状红利：V30 + V33 已铺够列（`id` BIGINT、`occurred_at`、`payload` JSONB、`published_at`、`correlation_id` TEXT、`causation_id` BIGINT、`aggregate_type`、`aggregate_id`）。

**path B 范围（已落地）**：
- `OutboxEventRecord`：12 字段（id Long + 10 业务字段 + publishedAt + attemptCount 内存态）；drop causationId / status / retryCount / nextAttemptAt 持久化
- `OutboxStatus`：内存枚举 `{PENDING, DISPATCHED, DISCARDED}`，由 `publishedAt` 推断
- `OutboxEventRepository`：4 方法（save / findById / findPendingBatch / markDispatched）；drop `moveToDeadLetter` / `markFailed`
- `OutboxWorker`：内存 `Map<Long,Integer> attempts`；attempts ≥ maxRetries 时只记 ERROR + 跳过；`@Scheduled` 调度（`outbox.poll.ms` 默认 1000）；`@ConditionalOnProperty` 控制开关
- `JpaOutboxEventRepository`：NamedParameterJdbcTemplate + JSONB `CAST(:p AS jsonb)` + GeneratedKeyHolder 回填 id
- `DeadLetterService.java` / `DeadLetterServiceTest.java`：**删除**

**v1.1-amendment backlog**（不在 v1.0 范围）：
1. `outbox_events` 增加 `retry_count INT DEFAULT 0` + `next_attempt_at TIMESTAMPTZ` 列
2. 新建 `outbox_dead_letter` 表 + `moveToDeadLetter()` 自动迁移 + 监控告警
3. Worker 分布式锁（PG `pg_try_advisory_lock`）支持多副本部署
4. envelope `causationId` UUID → DB BIGINT 的语义映射（保持 UUID 链路追溯）
5. `JpaOutboxEventRepositoryIT`（embedded-postgres）验证真实 SQL 行为（H2 与 PG JSONB 差异）

**当前验证**：
- `mvn test`: 65/65 GREEN（11 个测试类，包含 `SharedIntegrationContextTest` M2）
- Path B 范围内的核心契约已闭环（事务边界 / JSON 序列化 / 路由 / 重试 / 调度）

**对下游 plan 的影响**：
- `plan-01-task.md` ~ `plan-06-ai.md`：发布事件时不要假设存在 DLQ；失败处理走日志 + admin
- `plan-auth.md`：login/register 事件发布同事务语义不变
- `plan-observability-backup.md`：监控 `outbox_pending_count`（`WHERE published_at IS NULL`），阈值告警