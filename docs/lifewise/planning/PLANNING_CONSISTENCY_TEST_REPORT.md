# Planning Consistency Test Report

> 测试日期：2026-07-31
> 工具：`scripts/test-planning-consistency.ps1`
> 范围：`docs/lifewise/planning/*.md` + `docs/lifewise/architecture/*.md`
> 断言数：56（覆盖 9 轮 BUG 修复：X1-X8 + N1-N22 + B1-B26 共 56 项）

## 1. 测试执行结果

```
=== Lifewise Cross-Document Planning Consistency Tests ===
Root: C:\Users\jxw\Desktop\ai-coding-projects\Lifewise

[PASS] A01  V21/V34 module CHECK has 6 items  (hits=3)
[PASS] A02  V21/V34 format CHECK has json  (hits=2)
[PASS] A03  V33 outbox CHECK covers 4 auth.* events  (hits=11)
[PASS] A04  ai_jobs.status 7 states include DONE_NO_LLM/DONE_PARTIAL  (hits=12)
[PASS] A05  V32 daily_reports has is_draft column  (hits=2)
[PASS] A06  V32 CHECK uses is_draft not status (X1)  (hits=1)
[PASS] A07  mv_meal_nutrition_weekly UNIQUE INDEX declared (X5)  (hits=1)
[PASS] A08  nginx /api/auth/login location present (E1)  (hits=3)
[PASS] A09  nginx login zone rate=1r/m (E1)  (hits=2)
[PASS] A10  nginx /api/ai/chat SSE buffering off in location (X6)  (hits=3)
[PASS] A11  CSP font-src includes Google Fonts (X6)  (hits=1)
[PASS] A12  outbox retry 3 times consistent (F1)  (hits=1)
[PASS] A12b observability retry exhausted threshold 3 (F1)  (hits=1)
[PASS] A13  @RateLimit scope 5 items (G1)  (hits=1)
[PASS] A14  budget.threshold consistent in 4 files (X7+B2)  (hits=13)
[PASS] A15  ai.job.completed 3-state trigger (X3)  (hits=11)
[PASS] A16  ai-data-scopes report_types use _summary suffix (X4)  (hits=7)
[PASS] A17  milestone link duplicate test renamed to reject (D1)  (hits=1)
[PASS] A18  meal.created consumer does not include export mislabel (C1)  (hits=3)
[PASS] A19  plan-01-task has task_should_emit_created_event (A1)  (hits=1)
[PASS] A20  plan-02-daily is_draft comment is X1 (I1)  (hits=1)
[PASS] A21  plan-export BR-31 H1 fix comment present  (hits=1)
[PASS] A22  N1 nginx proxy_pass /api/auth/login has /api prefix  (hits=1)
[PASS] A23  N1 nginx proxy_pass /api/ai/chat has /api prefix  (hits=1)
[PASS] A24  N2 plan-notify §3.1 type 含 budget.threshold.80  (hits=2)
[PASS] A25  N2 plan-notify §3.1 type 含 milestone.due_soon  (hits=3)
[PASS] A26  N3 plan-notify §3.1 type 不含 meal.reminder（已删）  (hits=0)
[PASS] A27  N4 plan-export milestones 字段对齐 ProgressView  (hits=1)
[PASS] A28  N5 plan-export markdown format test  (hits=1)
[PASS] A29  N6 plan-auth ratelimit per IP  (hits=1)
[PASS] A30  N7 observability BudgetEvaluatorJob removed  (hits=0)
[PASS] A31  N8 plan-shared-infra login scope comment updated  (hits=0)
[PASS] A32a N9 data-flyway V28 not chat backfill  (hits=0)
[PASS] A32b N9 auth V28 owns refresh_tokens 3 tables  (hits=2)
[PASS] A33  N10 shared-integration ref must say V33 not V24  (hits=0)
[PASS] A34  N11 data-flyway V range must include V34  (hits=0)
[PASS] A35  N12 V34 COMMENT must say 6 modules not 4  (hits=0)
[PASS] A36  N13 shared-infra V range comment must include V34  (hits=0)
[PASS] A37  N14 BR-34 must reference UNIQUE not artifact_count  (hits=0)
[PASS] A38  N17 V33 SQL note 23 should be 25  (hits=0)
[PASS] A39  N18 sec4 title 19 should be 23  (hits=0)
[PASS] A40  N22 table count 31 should be 38  (hits=0)
[PASS] A41  N15 V35 row position wrong (after V30 not V34)  (hits=0)
[PASS] A42  N16 plan-06-ai ref V28 chat should be V35  (hits=0)
[PASS] A43  N20 observability 8 jobs should be 13  (hits=0)
[PASS] A44  N21 plan-06-ai payload report_id should be job_id  (hits=0)
[PASS] A45  B22a shared-integration MilestoneMissedJob typo  (hits=0)
[PASS] A46  B22b data-flyway MilestoneMissedJob typo  (hits=0)
[PASS] A47  B4 diet MV column week_start should be period_year/week  (hits=0)
[PASS] A48  B5 diet meal.created trigger should be INSERT not INSERT/UPDATE  (hits=0)
[PASS] A49  B11 ai llm_skipped should be DONE_NO_LLM  (hits=0)
[PASS] A50  B14 ai emit_report_generated_event should be job_completed  (hits=0)
[PASS] A51  B13 ai AiReportController should be AiConsentController  (hits=0)
[PASS] A52  B17 auth should have TokenReuseDetectedEvent  (hits=1)
[PASS] A53  B21a auth users should have password_hash column  (hits=1)
[PASS] A54  B21b auth users should have email_verified column  (hits=2)

=== Result Summary ===
PASS: 56
FAIL: 0
```

退出码：0

## 2. 断言映射表（56 项 → 9 轮 BUG 修复）

### 2.1 第一轮 X 编号（21 项，A01-A21）

| #    | BUG 修复轮   | 断言摘要                                  | 关键文件 / 行号                                            |
| ---- | --------- | ------------------------------------- | ---------------------------------------------------- |
| A01  | X2 决策 B  | V21/V34 `module CHECK` 含 6 项             | `data-model-v1.2-amendment.md` §1.1；`plan-data-flyway.md` V21/V34 |
| A02  | X2 决策 B  | V21/V34 `format CHECK` 含 `json`         | 同上                                                    |
| A03  | X7        | V33 `outbox_events.event_type` CHECK 含 4 条 `auth.*` 事件 | `plan-data-flyway.md` §3.35 V33                       |
| A04  | X8 / V31  | `ai_jobs.status` 7 态含 `DONE_NO_LLM` / `DONE_PARTIAL` | `plan-06-ai.md` §2.4 + §3；`plan-data-flyway.md` V31   |
| A05  | X1 / V32  | `daily_reports.is_draft` 列声明           | `plan-data-flyway.md` V32；`plan-02-daily.md` §3       |
| A06  | X1        | V32 CHECK 引用 `is_draft=TRUE` 不引 `status` | `plan-data-flyway.md` V32 SQL                          |
| A07  | X5        | `mv_meal_nutrition_weekly` `UNIQUE INDEX uq_mv_meal_user_week` 声明 | `plan-data-flyway.md` §3.4                             |
| A08  | E1        | nginx `/api/auth/login` location 存在      | `plan-deploy-nginx.md` §3                              |
| A09  | E1        | nginx `zone=login rate=1r/m`（替代原 `5r/m`）  | `plan-deploy-nginx.md` §3                              |
| A10  | X6        | nginx `/api/ai/chat` location 内 `proxy_buffering off` | `plan-deploy-nginx.md` §3                              |
| A11  | X6        | CSP `font-src 'self' https://fonts.gstatic.com`     | `plan-deploy-nginx.md` §3                              |
| A12  | F1        | `outbox` 重试 3 次与退避 1s/5s/30s             | `plan-shared-integration.md` §5.1                      |
| A12b | F1        | observability retry exhausted 阈值 `> 3`（不再是 5）  | `plan-observability-backup.md` §7.4                    |
| A13  | G1        | `@RateLimit scope` 5 项含 `export` / `webpush`     | `plan-shared-infra.md` §2.2 注释                        |
| A14  | X7 + B1/B2 | `budget.threshold` 在 4 个文件保持一致        | `plan-notify.md` §5；`plan-shared-integration.md` §4；`plan-data-flyway.md` §7；`plan-03-expense.md` §4 |
| A15  | X3 闭环     | `ai.job.completed` 三态（DONE / DONE_NO_LLM / DONE_PARTIAL）触发 | `plan-06-ai.md` §5.1；`plan-shared-integration.md` §4；`plan-data-flyway.md` §7 |
| A16  | X4        | `ai-data-scopes.yml` `report_types` 用 `_summary` 后缀 | `plan-06-ai.md` §4                                     |
| A17  | D1        | `milestone_link_tasks_should_reject_duplicate`    | `plan-05-plan.md` §5/§6                                |
| A18  | C1        | `meal.created` 消费方不含 `export` 误标       | `plan-04-diet.md` §4                                   |
| A19  | A1        | `task_should_emit_created_event` 测试已补      | `plan-01-task.md` §5.4                                 |
| A20  | I1        | `plan-02-daily` is_draft 注释标 X1（非 X3）  | `plan-02-daily.md` §3                                  |
| A21  | H1        | `plan-export` BR-31 已移除跨表 `expires_at`     | `plan-export.md` §3.1                                  |

### 2.2 第二轮 N 编号（22 项，A22-A44）

| #     | BUG | 断言摘要 | 关键文件 / 行号 |
| ----- | --- | -------- | -------------- |
| A22   | N1  | nginx `proxy_pass /api/auth/login` 含 `/api` 前缀 | `plan-deploy-nginx.md` §3 |
| A23   | N1  | nginx `proxy_pass /api/ai/chat` 含 `/api` 前缀     | `plan-deploy-nginx.md` §3 |
| A24   | N2  | `plan-notify` §3.1 type 含 `budget.threshold.80` | `plan-notify.md` §3.1 |
| A25   | N2  | `plan-notify` §3.1 type 含 `milestone.due_soon` | `plan-notify.md` §3.1 |
| A26   | N3  | `plan-notify` §3.1 type 不含已删 `meal.reminder` | `plan-notify.md` §3.1 |
| A27   | N4  | `plan-export` milestones 字段对齐 ProgressView     | `plan-export.md` §3 |
| A28   | N5  | `plan-export` markdown format 测试名存在           | `plan-export.md` §5 |
| A29   | N6  | `plan-auth` ratelimit 维度 per IP（非 per user） | `plan-auth.md` §6 |
| A30   | N7  | `plan-observability-backup` BudgetEvaluatorJob 已删（事件驱动替代） | `plan-observability-backup.md` §4 |
| A31   | N8  | `plan-shared-infra` login scope 注释更新         | `plan-shared-infra.md` §2.2 |
| A32a  | N9  | `plan-data-flyway` V28 不引用 chat_backfill     | `plan-data-flyway.md` §3.28 |
| A32b  | N9  | `plan-data-flyway` V28 auth 持有 refresh_tokens 三表 | `plan-data-flyway.md` §3.28 |
| A33   | N10 | `plan-shared-integration` 引用 V33（而非 V24）  | `plan-shared-integration.md` §4 修订说明 |
| A34   | N11 | `plan-data-flyway` V 范围包含 V34               | `plan-data-flyway.md` 顶部 |
| A35   | N12 | V34 COMMENT 写 "6 modules"（非 4）             | `plan-data-flyway.md` §3.34 |
| A36   | N13 | `plan-shared-infra` V 范围注释含 V34            | `plan-shared-infra.md` 顶部 |
| A37   | N14 | BR-34 引用 UNIQUE（非 artifact_count）         | `data-model-v1.2-amendment.md` §1 |
| A38   | N17 | V33 SQL 注释 "23" 改为 "25"                   | `plan-data-flyway.md` §3.33 |
| A39   | N18 | §4 标题 "19 条" 改为 "23 条"（探索发现：实际应为 25，已二次修订） | `plan-shared-integration.md` §4 + `plan-data-flyway.md` §7 |
| A40   | N22 | 表数 "31 张" 改为 "38 张"                     | `plan-data-flyway.md` 顶部 |
| A41   | N15 | V35 行位置：应在 V30 后（非 V34 后）             | `plan-data-flyway.md` §3 |
| A42   | N16 | `plan-06-ai` 引用 V28 chat 应为 V35         | `plan-06-ai.md` §8 |
| A43   | N20 | observability "8 个 Job" 改为 "13 个"        | `plan-observability-backup.md` §4 |
| A44   | N21 | `plan-06-ai` payload `report_id` 改为 `job_id` | `plan-06-ai.md` §4 |

### 2.3 第三轮 B 编号 P0（10 项，A45-A54）

| #    | BUG | 断言摘要 | 关键文件 / 行号 |
| ---- | --- | -------- | -------------- |
| A45  | B22a | `plan-shared-integration` `MilestoneMissedJob` typo → `MissedMilestoneJob` | `plan-shared-integration.md` §4 |
| A46  | B22b | `plan-data-flyway` 同 typo 修复              | `plan-data-flyway.md` §7 |
| A47  | B4  | `plan-04-diet` MV 列名 `week_start` → `period_year, period_week` | `plan-04-diet.md` §3 |
| A48  | B5  | `plan-04-diet` `meal.created` 触发源仅 INSERT（非 INSERT/UPDATE） | `plan-04-diet.md` §4 |
| A49  | B11 | `plan-06-ai` `llm_skipped` → `DONE_NO_LLM`   | `plan-06-ai.md` §4 |
| A50  | B14 | `plan-06-ai` `ai_should_emit_report_generated_event` → `job_completed` | `plan-06-ai.md` §5 |
| A51  | B13 | `plan-06-ai` `AiReportController` → `AiConsentController` | `plan-06-ai.md` §1 |
| A52  | B17 | `plan-auth` 列出 `TokenReuseDetectedEvent`     | `plan-auth.md` §1 + §4 |
| A53  | B21a | `plan-auth` `users` 含 `password_hash` 列   | `plan-auth.md` §3.1 |
| A54  | B21b | `plan-auth` `users` 含 `email_verified` 列  | `plan-auth.md` §3.1 |

### 2.4 P1 修复（18 项 BUG，无新增断言，靠每文件自检 + Explore agent 复查）

| 文件 | BUG | 修复要点 |
| ---- | --- | -------- |
| plan-shared-integration.md | B1 + ID-03/04/07/08 | 19→25 数字统一 + §4 拆 2 合并行为 4 行 + V33 描述 + 模块列表 6→7 |
| plan-data-flyway.md | B2 | §7 19→25 + 拆合并行 |
| references/shared-strings.md | B23/B26 | expense/budget 加 X7 注释 + §5 cron H2/M5 trail |
| plan-04-diet.md | B3/B6 | 02:30 统一 3 处 + ProfileView 字段 |
| plan-05-plan.md | B8/B9 | status_open→active + PlanStaleNotifyJob 节 |
| plan-06-ai.md | B10/B12/B15/B16 | 5→6 端点 + final_status 三态 + rate limit triple + conversations 表 |
| plan-auth.md | B18/B20/B34 | LoginAttemptService 测试 6 条 + reset-password TOKEN_EXPIRED + JWT 90天→Refresh 30天 |
| plan-observability-backup.md | B24 | Ollama 三态说明 |
| plan-notify.md | B25 | 6→7 类 + milestone.due_soon 状态注释 |

### 2.5 P2 修复（4 项 BUG，靠 grep 自检）

| 文件 | BUG | 修复要点 |
| ---- | --- | -------- |
| plan-data-flyway.md | P2-03/04 | V33 注释 "7 条新事件" → 4 条 |
| plan-04-diet.md | P2-06 | 参考资料 V11 加 "meals" 表名 |
| plan-observability-backup.md | P2-01 | OllamaHealthIndicator 补 ```java 开块 |
| plan-shared-infra.md | P2-05 | JwtRefreshTokenService `//` 行尾注释改为接口契约列表 |

## 3. 测试覆盖维度

### 3.1 数据库迁移完整性（10 项）

- V21/V34 模块 CHECK 6 项（A01）
- V21/V34 格式 CHECK 5 项含 json（A02）
- V33 outbox CHECK 25 条事件（A03 + A38/A39）
- V31 ai_jobs.status 7 态（A04）
- V32 daily_reports.is_draft + CHECK（A05/A06）
- 表数 38 张（A40）
- V35 行位置 + V28 边界（A32a/A32b/A41）
- BR-34 UNIQUE 引用（A37）
- V34 COMMENT 6 modules（A35）
- V 版本号范围 V33/V34（A33/A34/A36）

### 3.2 物化视图（1 项）

- `mv_meal_nutrition_weekly` CONCURRENTLY 前置 UNIQUE INDEX（A07）
- MV 列名 `period_year/period_week`（A47）

### 3.3 nginx 部署（4 项）

- 登录限流语义 15min/5 次（A08/A09）
- AI Chat SSE keepalive 在 location 内（A10）
- CSP font-src Google Fonts（A11）
- proxy_pass `/api` 前缀（A22/A23）

### 3.4 横切契约（4 项）

- outbox 重试次数一致（A12/A12b）
- @RateLimit scope 5 项（A13）
- 事件消费者对齐 notify（A14）
- ai.job.completed 三态触发（A15 + A49/A50）

### 3.5 配置 / 语义一致性（3 项）

- ai-data-scopes report_types 枚举（A16）
- milestone link 重复拒绝（A17）
- meal.created 消费方（A18 + A48）

### 3.6 测试用例完整性（2 项）

- task.created 事件测试（A19）
- 文档注释自洽（A20/A21）

### 3.7 notify 类型枚举（3 项）

- budget.threshold.80 / milestone.due_soon 存在（A24/A25）
- meal.reminder 已删（A26）

### 3.8 export 模块（2 项）

- milestones 字段对齐 ProgressView（A27）
- markdown format 测试名（A28）

### 3.9 auth / observability 边界（4 项）

- ratelimit per IP（A29）
- BudgetEvaluatorJob 已删（A30）
- login scope 注释（A31）
- V28 auth 边界（A32b）

### 3.10 数字 / 命名系统（10 项）

- V33 注释 25 条（A38）
- §4 标题 25 条（A39）
- 表数 38 张（A40）
- V35 行位置（A41）
- plan-06-ai V35 chat（A42）
- observability 13 jobs（A43）
- payload job_id（A44）
- `MissedMilestoneJob` typo（A45/A46）
- AiConsentController（A51）
- TokenReuseDetectedEvent（A52）
- users 列 password_hash/email_verified（A53/A54）

## 4. 复测命令

```powershell
# 在仓库根目录
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-planning-consistency.ps1
```

退出码 `0` = 全部通过；非零 = 失败条数。

## 5. 已知局限

1. **静态 grep 而非语义校验**：本脚本基于字符串匹配，不验证 CHECK 约束的语义正确性（例如 V21 module CHECK 中是否真的存在 6 个合法值）。语义验证需 PostgreSQL Testcontainers（不在 v1.0 范围）。
2. **不验证字段类型**：`is_draft BOOLEAN NOT NULL DEFAULT TRUE` 中 `BOOLEAN` 关键字拼写错误不会被检测（仅校验列存在）。
3. **不验证事件 payload schema**：仅校验 `event_type` 名存在，不验证 payload JSON Schema 合规性。
4. **不验证 Flyway SQL 语法**：本脚本只读规划文档 SQL 段，不连库执行。
5. **P1/P2 修复未新增断言**：P1（18 BUG）/ P2（4 BUG）的修复靠每文件自检 + Explore agent 复查，未在断言脚本中固化（cost 高，收益边际递减）。

## 6. 后续建议

- CI 集成：在 GitHub Actions 中调用 `pwsh scripts/test-planning-consistency.ps1` 作为 PR check
- 扩大断言：固化 P1 修复为断言（status_open/active、02:30 时点、final_status、JWT 15min、Ollama YELLOW、6→7 类等）
- 与 `business-architecture.md` / `data-model-v1.2-amendment.md` 双向校验（当前单向：仅校验 plan-*.md 引用架构文档）

---

> 本报告与 `scripts/test-planning-consistency.ps1` 同步维护。修改规划文档后必须重跑测试，确保 56 项全绿。
