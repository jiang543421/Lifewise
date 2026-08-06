# AI 模块 rebase 冲突分析：skeleton → main

> 编写日期：2026-08-05
> 编写会话：handoff 会话（非 rebase 执行会话）
> 目的：在新会话的 rebase worktree 里执行 `git rebase main` 之前，
> 给执行者一份导航：哪些文件会冲突、按什么规则 take 哪一侧、
> cherry-pick 哪些 flyway commit、验证 gate 是什么。

## 0. 关键事实

| 维度 | 值 |
|---|---|
| 工作目录 | `C:\Users\jxw\Desktop\ai-coding-projects\lifewise-ai-rebase-wt` |
| 当前分支 | `feature/ai-step11-skeleton-wip-rebase`（HEAD = `4be74e1`）|
| rebase 目标 | `main`（HEAD = `23c022d`，已含 5 个业务模块 + 31 张 schema）|
| skeleton 起点 | `feature/ai-step11-skeleton-wip` (`4be74e1`) |
| flyway 起点 | `feature/ai-v42-v46-flyway` (`f397401`) |
| skeleton vs flyway | skeleton ⊃ flyway（skeleton 多了 Ollama / ScopedData / Audit / RateLimiter / 3 Controllers / 全套测试）|
| rebase 工时预估 | 2-6 小时，~25 java 冲突 + 1 markdown 冲突 |
| 已 drop | `stash@{0}` = `0ebe72fff2a580ffcdc8c20b0f6d56e6923e7d33`（4 个 msg-*.txt 已救到 `$env:TEMP\lifewise-stash-2026-08-05-*`）|

## 1. 冲突面

> 表内 "来源 commit" 列出冲突的两侧各自 commit SHA，按 §2 决策规则处理。

| 路径 family | skeleton 来源 | flyway 来源 | 风险 |
|---|---|---|---|
| `ai/config/AiAsyncConfig.java` | `b46dee5` | `3ffc92f` | 高 |
| `ai/domain/{AiJob,AiReport,ChatMessage}.java` | `ad9e5e9` | `3a2eedf` | 中-高 |
| `ai/domain/enums/*` (5 files) | `ad9e5e9` | `3a2eedf` | 中 |
| `ai/dto/*` (8 files) | `b46dee5` | `3ffc92f` | 中 |
| `ai/repository/*` (3 files) | `b46dee5` | `3ffc92f` | 中 |
| `ai/service/exception/*` (5 重叠 + 4 个 skeleton 独有) | `b46dee5` | `3ffc92f` | 中 |
| `ai/web/{CurrentUser,CurrentUserArgumentResolver,MissingCurrentUserException}.java` | `b46dee5` | `3ffc92f` | 中-高 |
| `docs/lifewise/ai/review-notes-v8-schema-gap.md` | `d81252f` | `6c7e157` | 高（同名 commit 不同内容）|
| `task/web/CurrentUserArgumentResolver.java` | `d63f507` | — | 高（main 73 行 vs skeleton 早期状态）|

## 2. 解决规则（per family）

### 2.1 主旋律
- **TAKE skeleton** —— skeleton 是 flyway 的超集，多出的功能不能丢
- **flyway 唯二价值**：V42 (message_metadata) + V46 (job_type CHECK) + V47 (referenced_entity_ids JSONB) 三条 Flyway SQL
- **task/web/CurrentUserArgumentResolver.java 单独**：take main 的 `d63f507`（73 行单 impl，符合 §7.3.1）

### 2.2 per-family

#### 2.2.1 `ai/config/AiAsyncConfig.java`
**TAKE skeleton**（adapter 模式更完整）。如 flyway 有独有 profile 控制，逐行 cherry-pick。

#### 2.2.2 `ai/domain/{AiJob,AiReport,ChatMessage}.java`
**TAKE skeleton**。逐文件核对 flyway `3a2eedf` 差异，仅在 skeleton 缺字段时补。

#### 2.2.3 `ai/domain/enums/*`（5 files）
**TAKE skeleton**。逐 enum 核 `values()` 顺序与文档说明。

#### 2.2.4 `ai/dto/*`（8 files）
**TAKE skeleton**。特别地 `AiReportView.referencedEntityIdsJson` 字段必须保留 skeleton 的 `List<Long>` 版本（与 V47 schema 对齐，`f2336b3 fix(ai)` 已修过）。

#### 2.2.5 `ai/repository/*`（3 files）
**TAKE skeleton**（JPA Adapter 风格统一以 skeleton 为准）。

#### 2.2.6 `ai/service/exception/*`
5 个 flyway 与 skeleton 重叠 → **TAKE skeleton**；4 个 skeleton 独有的 → 直接保留无冲突。

#### 2.2.7 `ai/web/{CurrentUser,CurrentUserArgumentResolver,MissingCurrentUserException}.java`
- `CurrentUser.java` + `MissingCurrentUserException.java`：**TAKE skeleton**（注解模板与 task/daily 一致）
- `CurrentUserArgumentResolver.java`：参考 §2.2.9，take task 模块统一模板

#### 2.2.8 `docs/lifewise/ai/review-notes-v8-schema-gap.md`
**TAKE skeleton 的 `d81252f`**（含 v0.3 schema 注解更完整）。flyway 的 `6c7e157` 主题重复，舍弃。

#### 2.2.9 `task/web/CurrentUserArgumentResolver.java`
main 的 `d63f507` 是权威 73 行版本。**TAKE main 的内容**，丢弃 skeleton 这边的早期状态。

## 3. Cherry-pick 序列（rebase 完成后）

```bash
cd $WT_PATH   # = lifewise-ai-rebase-wt

# 仅 cherry-pick flyway 的 SQL commit
git cherry-pick f397401   # V47: ai_reports.referenced_entity_ids JSONB
git cherry-pick 4aae764   # V42 message_metadata + V46 job_type CHECK extension

# 以下 flyway commit 跳过（不 cherry-pick）：
#   6c7e157 docs(ai): capture plan-06 vs V8 schema gap → skeleton 已有 d81252f
#   3ffc92f feat(ai): add DTO + exception + web resolver + async config → 与 b46dee5 冲突，已 take skeleton
#   3a2eedf feat(ai): scaffold domain layer → 与 ad9e5e9 冲突，已 take skeleton
```

## 4. 验证 gate

```bash
# 4.1 编译全模块
mvn -pl app clean compile
# 预期 BUILD SUCCESS；如有 CurrentUserArgumentResolver 报错，回 §2.2.9

# 4.2 跑 ai 模块单测
mvn -pl app test -Dtest='com.lifewise.ai.**'
# 重点测试：
#   - AiReportViewTest（List<Long> referencedEntityIds）
#   - OllamaClientTest（retry / latency / token-truncate）
#   - ScopedDataFetcherTest（whitelist + user_id injection）
#   - PromptBuilderTest
#   - ConsentVerifierTest
#   - AiRateLimiterTest（10/m user + 60/h user + 100/m global 三重 quota）
#   - 3 个 controller WebMvcTest：AiReport / AiJob / AiConsent

# 4.3 跑 6 模块集成测试
mvn -pl app test
# 全模块 GREEN

# 4.4 编译产物 sanity
ls app/target/classes/com/lifewise/ai/ | head -20
```

## 5. 推送 + 合并 + 清理

```bash
# 5.1 push
git push -u origin feature/ai-step11-skeleton-wip-rebase

# 5.2 PR
gh pr create --base main --head feature/ai-step11-skeleton-wip-rebase \
   --title "feat(ai): step 11-13 skeleton + Ollama + 3 controllers (closes AI module)" \
   --body "详见 plan-06-ai.md 与本文件 docs/lifewise/ai/conflict-analysis-skeleton-rebase.md。

本 PR 落地 6/6 模块的最后一个：AI 模块骨架 + Ollama 集成 + 3 个 controller。
schema 增量：V42 (message_metadata) + V46 (job_type CHECK) + V47 (referenced_entity_ids)。"

# 5.3 squash merge（保持 main 单 commit 历史）
gh pr merge --squash

# 5.4 清理 worktree + 原 WIP 分支
git worktree remove C:/Users/jxw/Desktop/ai-coding-projects/lifewise-ai-rebase-wt
git branch -D feature/ai-step11-skeleton-wip-rebase feature/ai-step11-skeleton-wip feature/ai-v42-v46-flyway
git push origin --delete feature/ai-step11-skeleton-wip feature/ai-step11-skeleton-wip-rebase feature/ai-v42-v46-flyway

# 5.5 7 天后清理 $env:TEMP rescue manifest（保留也行）
Remove-Item "$env:TEMP\lifewise-stash-2026-08-05-RESCUE-MANIFEST.txt"
Remove-Item "$env:TEMP\lifewise-stash-2026-08-05-untracked" -Recurse -Force
Remove-Item "$env:TEMP\lifewise-stash-2026-08-05-full.patch"
Remove-Item "$env:TEMP\lifewise-stash-2026-08-05-index.patch"
```

## 6. 风险与兜底

| 风险 | 触发条件 | 兜底 |
|---|---|---|
| rebase autosquash 失败 | 冲突标记未正确解析 | `git rebase --abort` 后从 §1 重新来过 |
| mvn verify RED | 任一 take-决策漏字段 | 定位到失败测试，反查对应 conflict resolution |
| 4 msg-*.txt 草稿误删 | 引用 obsolete commit message | $env:TEMP 7 天内可调阅 |
| flyway 分支被误用 | 跳过 cherry-pick 步骤 | §3 跳过说明 + §2.1 主旋律 |
| 6 模块集成测试失败 | task / daily 等共用 resolver 路径破坏 | §2.2.9 take main |
| Ollama 容器未启导致 IT 失败 | 集成测试阶段连真实 ollama | 当前 ai 是单元测试为主，IT 跳过即可 |

## 7. 引用

- `plan-06-ai.md`（AI 模块 PRD，6 模块最后一篇）
- `docs/lifewise/ai/review-notes-v8-schema-gap.md`（V8 schema 缺口分析，本次 rebase 一并入主仓）
- `docs/lifewise/architecture/known-limitations-v1.md`（Phase B-1 / B-2 / H3 / M* 状态）
- `~/.claude/projects/.../memory/ai-step11-wip-commits.md`
- `~/.claude/projects/.../memory/ai-step12-pathb-flyway-branches.md`
- `~/.claude/projects/.../memory/ai-step13-skeleton-rebase-handoff.md`（本文件 handoff record）
- `~/.claude/projects/.../memory/feedback-powershell-encoding-trap.md`
- `~/.claude/projects/.../memory/feedback-wip-skeleton-session-pattern.md`
- `~/.claude/rules/powershell/bom-trap.md`（PS 5.1 BOM / EncodedCommand 坑）
- `~/.claude/rules/ecc/java/coding-style.md`（immutability / record / sealed type）
- `~/.claude/rules/ecc/java/testing.md`（JUnit 5 / AssertJ / 80% 覆盖率）

---

> **下次会话第一动作**：
> ```bash
> cd C:/Users/jxw/Desktop/ai-coding-projects/lifewise-ai-rebase-wt
> git status
> # 应见到 1 个 untracked: docs/lifewise/ai/conflict-analysis-skeleton-rebase.md
> git add docs/lifewise/ai/conflict-analysis-skeleton-rebase.md
> git commit -m "docs(ai): add rebase conflict analysis (handoff from 2026-08-05)"
> # 然后按 §1-§6 顺序执行
> ```
