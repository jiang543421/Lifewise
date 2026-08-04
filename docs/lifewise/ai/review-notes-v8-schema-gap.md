# plan-06-ai vs V8 Schema 差异清单（review 必报）

> **文档状态**：WIP review notes（step-11 skeleton + step-12 路径 B Flyway 阶段）
> **创建日期**：2026-08-04
> **更新日期**：2026-08-04（code-review 后扩到 5 条 + 修正 V31 误判）
> **关联**：plan-06-ai.md §3 + V8__create_ai_module.sql + V31__extend_ai_jobs_status.sql + V42/V46 Flyway
> **目的**：把 plan、code 与实际 DDL 之间的字段缺口落到文档，避免下次会话重复踩坑

---

## 0. 状态机修正（code-review Finding #1 反驳）

reviewer 误判：Java enum 含 `PENDING_PARTIAL` / `RUNNING_DEGRADED`，但 V31 migration
已经把 `ai_jobs.status` CHECK 扩展为 9 值（含这两个 ghost state），与 enum 完全对齐。
V31 line 14-20 已显式声明：

```sql
CHECK (status IN (
    'PENDING','PENDING_PARTIAL',
    'RUNNING','RUNNING_DEGRADED',
    'DONE','DONE_PARTIAL','DONE_NO_LLM',
    'FAILED','CANCELLED'
))
```

因此 **无需 V45 迁移**，原 review-notes §1.1 的"V45 扩 status CHECK"误判作废。

---

## 1. 缺口清单（5 处）

### 1.1 `chat_messages.sql_executed` 缺失 [P0-安全面]

| 维度 | 内容 |
|---|---|
| **plan 提的字段** | `sql_executed TEXT NULL` — 审计 LLM 生成的 SQL（business §6.7 流程 7） |
| **V8 实际** | 无此字段；只有 `message_refs JSONB` + `role` |
| **影响** | ScopedDataFetcher 执行 LLM 生成的 SQL 后无法落库审计；BR-19/22 通过 DB GRANT 控制不可变，但前提是 SQL 文本必须落库 |
| **建议** | 加 Flyway：`ALTER TABLE chat_messages ADD COLUMN sql_executed TEXT NULL` |
| **服务层补偿** | 暂用 `message_refs JSONB` 存 `{sql: "..."}`；后续迁回独立列 |
| **状态** | 仍待落地（未在本批 Flyway 中；优先级低于 message_metadata 因为 SQL 审计可走 message_metadata 子结构） |

### 1.2 `ai_reports.referenced_entity_ids` 缺失 [P1-可观测性]

| 维度 | 内容 |
|---|---|
| **plan 提的字段** | `referenced_entity_ids JSONB` — 报告引用的源 aggregateId 列表 |
| **V8 实际** | 无此字段；只有 `feedback_count` / `helpful_count` / `period_start/end` |
| **影响** | 跨报告引用追溯失败；plan §2.1 GET /api/ai/reports 契约要求该字段 |
| **当前妥协** | AiReportView.referencedEntityIdsJson 返回 `"[]"` |
| **建议** | 加 V43 Flyway：`ALTER TABLE ai_reports ADD COLUMN referenced_entity_ids JSONB NOT NULL DEFAULT '[]'::jsonb` |
| **阻塞** | 否 — 返回空数组兜底；V43 落地后回填 AiReportService |

### 1.3 `ai_reports.user_edited` 缺失 [P1-编辑回路]

| 维度 | 内容 |
|---|---|
| **plan 提的字段** | `user_edited BOOLEAN` — 用户手动编辑后标记，AI 不覆盖 |
| **V8 实际** | 无此字段；V8 仅暴露 `feedback_count` / `helpful_count` |
| **影响** | 用户编辑报告后 AI 无法感知，可能下次 regenerate 时静默覆盖 |
| **建议** | 加 V44 Flyway：`ALTER TABLE ai_reports ADD COLUMN user_edited BOOLEAN NOT NULL DEFAULT FALSE` |
| **阻塞** | 否 — 当前 regenerate 流程尚未实现 |

### 1.4 `chat_messages.role` 枚举差异 [P0-数据校验]

| 维度 | 内容 |
|---|---|
| **plan 提的枚举** | `role ∈ {USER, ASSISTANT, SYSTEM, TOOL}` — 含 TOOL 角色 |
| **V8 实际 CHECK** | `role ∈ {USER, ASSISTANT, SYSTEM}` — 不含 TOOL |
| **影响** | 若 LLM 调用工具调用框架（v1.2+ 评估），TOOL 角色无法入库 |
| **当前妥协** | ChatRole 枚举按 V8 实际只有 3 个值；tool message 暂用 SYSTEM + content 前缀 `[TOOL]` 区分 |
| **建议** | Flyway 扩展 CHECK：`ALTER TABLE chat_messages DROP CONSTRAINT chat_messages_role_check; ADD CONSTRAINT ... CHECK (role IN ('USER','ASSISTANT','SYSTEM','TOOL'))` |
| **阻塞** | 否 — MVP 无 tool calling |

### 1.5 `chat_messages.message_metadata` 缺失 [P0-审计载体 — review 新发现]

| 维度 | 内容 |
|---|---|
| **来源** | code-review Finding #4（reviewer 比本 review-notes 多发现一条，原 v0.1 漏记） |
| **plan 提的字段** | `message_metadata JSONB` — audit 行结构化载荷（plan §3 数据模型 + §7.7 断言集） |
| **V8 实际** | 无此字段；只有 `message_refs JSONB` |
| **影响** | `audit_should_include_trace_id` / `audit_should_include_tokens_used`（plan §7.7）无落地载体；AiAuditLogger 只能写 `content` 纯文本，trace_id 与决策路径不可回溯 |
| **与 message_refs 区分** | `message_refs` = 引用片段（命中的源 aggregateId；UI 卡片可点）；`message_metadata` = 系统审计上下文（trace_id / decision_type / latency_ms / tokens_used） |
| **落地状态** | ✅ **已落 V42**（commit `4d1c04a` in `feature/ai-v42-v46-flyway`）：`ALTER TABLE chat_messages ADD COLUMN message_metadata JSONB NULL` |
| **服务层接线** | AiAuditLogger 写入 role=SYSTEM 行时必填 `message_metadata`；写失败 → 整审计行 ROLLBACK（BR-19/22） |

---

## 2. plan §2.1 端点字段映射现状

| plan API 字段 | V8 实际字段 | 映射方式 |
|---|---|---|
| `POST /api/ai/reports/generate` body.report_type | ai_jobs.job_type | AiJobType.fromWire() 映射（snake_case → UPPER_SNAKE_CASE） |
| `AiJobView.report_type` | ai_jobs.job_type | 反向映射 |
| `AiReportView.referenced_entity_ids` | 暂缺 | 兜底 `[]` |
| `ChatResponse.source` | 内存枚举 | RULE / LLM 字符串常量 |
| `ChatResponse.conversation_id` | chat_messages.conversation_id（V25 nullable） | 直接透传 |

---

## 3. 落地建议（路径 B 已部分执行）

| 优先级 | 动作 | 状态 | 阻塞范围 |
|---|---|---|---|
| P0 | V42 加 `chat_messages.message_metadata` | ✅ **已落** commit `4d1c04a` | BR-19/22 结构化审计 |
| P0 | V46 扩 `ai_jobs.job_type` CHECK 含 MONTHLY_SUMMARY/TASK_ADVICE | ✅ **已落** commit `4d1c04a` | UI 月报/任务建议 |
| P0 | `chat_messages.sql_executed` 缺失 | ⏳ **未落** | ScopedDataFetcher SQL 审计（可暂走 message_metadata 子结构） |
| P0 | `chat_messages.role` CHECK 含 TOOL | ⏳ **未落**（v1.2+ 评估） | tool-calling 路径，MVP 不阻塞 |
| P1 | `ai_reports.referenced_entity_ids` | ⏳ **未落**（V47 待你决策） | 报告引用追溯 |
| P1 | `ai_reports.user_edited` | ⏳ **未落** | regenerate 流程 |
| P2 | ai-data-scopes.yml 编译期加载 | ⏳ **未落** | ScopedDataFetcher 实现前置依赖（下次会话补） |

**代码侧 commit-D 待落**：AiReportView.referencedEntityIds 改 `List<Long>`；AiAsyncConfig
加 `@EnableAsync`；AiJob.markRunning 加防御断言。详见路径 B Step 5。

---

## 4. 服务层依赖图（下次会话起点）

```
AiController ─┬─► AiJobService ─┬─► ScopedDataFetcher ──► NamedParameterJdbcTemplate (5 表)
              │                 ├─► OllamaClient ──► deepseek:8b
              │                 ├─► PromptBuilder
              │                 ├─► AiAuditLogger ──► chat_messages (role=SYSTEM)
              │                 └─► AiRateLimiter ──► Redis Token Bucket
              │
              ├─► AiReportService ──► AiReportRepository
              │
              ├─► AiChatService ─┬─► RuleEngine (P95<500ms 路径)
              │                  └─► OllamaClient + ScopedDataFetcher (LLM 慢路径)
              │
              └─► ConsentVerifier ──► user_profiles.ai_consent
```

---

*文档版本：v0.2（step-11 skeleton + 路径 B Flyway 落地后）*
*下次更新时机：服务层 GREEN 落地后补 ScopedDataFetcher 的 SQL 白名单落地详情；commit-D 修代码后再升 v0.3*