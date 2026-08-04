# plan-06-ai vs V8 Schema 差异清单（review 必报）

> **文档状态**：WIP review notes（step-11 skeleton 阶段产物）
> **创建日期**：2026-08-04
> **关联**：plan-06-ai.md §3 数据模型 + V8__create_ai_module.sql + V25/V31 migrations
> **目的**：把 plan 与实际 DDL 之间的 4 处字段缺口落到文档，避免下次会话重复踩坑

---

## 1. 缺口清单（4 处）

### 1.1 `chat_messages.sql_executed` 缺失 [P0-安全面]

| 维度 | 内容 |
|---|---|
| **plan 提的字段** | `sql_executed TEXT NULL` — 审计 LLM 生成的 SQL（business §6.7 流程 7） |
| **V8 实际** | 无此字段；只有 `message_refs JSONB` + `role` |
| **影响** | ScopedDataFetcher 执行 LLM 生成的 SQL 后无法落库审计；BR-19/22 通过 DB GRANT 控制不可变，但前提是 SQL 文本必须落库 |
| **建议** | 加 V42 Flyway：`ALTER TABLE chat_messages ADD COLUMN sql_executed TEXT NULL` |
| **服务层补偿** | 暂用 `message_refs JSONB` 存 `{sql: "..."}`；后续迁回独立列 |
| **阻塞** | 否 — 可继续；建议本 review 批完后立即补 V42 |

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
| **建议** | V45 Flyway 扩展 CHECK：`ALTER TABLE chat_messages DROP CONSTRAINT chat_messages_role_check; ADD CONSTRAINT ... CHECK (role IN ('USER','ASSISTANT','SYSTEM','TOOL'))` |
| **阻塞** | 否 — MVP 无 tool calling |

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

## 3. 落地建议（review 阶段一并决议）

| 优先级 | 动作 | 阻塞范围 |
|---|---|---|
| P0 | V42 加 `chat_messages.sql_executed` | ScopedDataFetcher 审计落库（plan §6.7 流程 7） |
| P0 | V45 扩展 chat_messages.role CHECK 含 TOOL | tool-calling 路径（v1.2+，MVP 不阻塞） |
| P1 | V43 加 `ai_reports.referenced_entity_ids` | 报告引用追溯 |
| P1 | V44 加 `ai_reports.user_edited` | regenerate 流程 |
| P2 | ai-data-scopes.yml 编译期加载 | ScopedDataFetcher 实现前置依赖（下次会话补） |

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

*文档版本：v0.1（step-11 skeleton 阶段）*
*下次更新时机：服务层 GREEN 落地后补 ScopedDataFetcher 的 SQL 白名单落地详情*