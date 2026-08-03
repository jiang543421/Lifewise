# Lifewise 项目研发规范

> 本文档为 Claude Code / Claude Agent 在 Lifewise 项目中工作的核心约束。
> 优先级：根目录 `CLAUDE.md` > `.claude/rules/` > 用户 Prompt 内临时说明。

---

## 1. 项目概述

### 1.1 背景

Lifewise 是一个**个人版生活管理 Web 应用**，覆盖任务、日报、消费、饮食、计划、AI 六大模块。所有数据本地化、AI 推理本地化（Ollama deepseek:8b），强调**隐私优先**与**单机可控**。

### 1.2 目标

- 在 6 个高内聚模块之间，建立低耦合的事件协作链路
- 保证 Ollama、Web Push、统计投影或导出能力故障时，核心记录仍可正常保存
- 守住「不跨模块直接修改数据」「不引入完整数据中枢或微服务」两条 v1.0 边界

### 1.3 范围

- **In scope**：模块化单体后端 + 6 模块 UI 原型 + Docker Compose 单机部署 + 24 张表数据模型
- **Out of scope**：跨模块洞察（v1.1）、多模态（v1.2）、协作（v1.3）、多区域 / 多活

### 1.4 团队

- 唯一项目所有者：江兴旺（jiang543421）
- 协作平台：GitHub (`jiang543421/Lifewise`) + Gitee 镜像
- 提交者标识：保持 `git config user.name` / `user.email` 现状，不要随意切换

---

## 2. 技术栈

### 2.1 客户端

- **形态**：响应式 Web PWA（纯 HTML + CSS + JS，无前端框架）
- **样式**：CSS 变量驱动的设计 token 系统；颜色 token 统一为 `--primary` / `--bg-soft` / `--ink-1..3` 别名
- **字体**：Inter + Noto Sans SC（Google Fonts，预连接）
- **PWA 能力**：Service Worker + Web Push + 离线缓存

### 2.2 后端

| 组件 | 版本 | 职责 |
|------|------|------|
| Spring Boot | 3.3 | 6 业务模块 + 3 共享模块的模块化单体 |
| Java | 21 (eclipse-temurin:21-jre) | 运行时 |
| Tomcat | 200 线程 | HTTP/SSE 入口 |
| 数据库 | PostgreSQL 15-alpine | 24 张表 + 5 分区 + 物化视图 |
| 缓存 | Redis 7-alpine | 限流、幂等键、缓存、Web Push 状态 |
| LLM | Ollama + deepseek:8b | 本地 AI 推理（单用户串行） |
| 反向代理 | nginx 1.27-alpine | TLS 1.2+ · HSTS · CSP · 限流 · 压缩 |
| 监控 | Spring Actuator + Prometheus 端点 | 端口暴露 |
| 备份 | prodrigestivill/postgres-backup-local | 每日 03:00 pg_dump + 7 天滚动 |

### 2.3 部署

- **任务编排**：Docker Compose（单机，无 K8s 需求）
- **灾备**：每日 `pg_dump` + 7 天滚动 + 宿主机副本；RPO 24h / RTO < 30min
- **架构方法**：模块化单体 + Docker Compose 单机部署 + PWA 接入

### 2.4 关键依赖（前端原型）

- 字体：Inter（400/500/600/700）+ Noto Sans SC（400/500/600/700）
- 图标：原生 emoji / 内嵌 SVG（不依赖 Font Awesome）
- 图表：内嵌 SVG（不引入 Chart.js 等）

---

## 3. 目录结构规范

```
.
├── CLAUDE.md                      # 本文件
├── README.md                      # 项目说明
├── docs/
│   └── lifewise/
│       ├── architecture/          # 架构设计文档（业务/技术/数据模型）
│       ├── designs/               # UI 原型（每个模块一个静态 HTML）
│       │   ├── 01-task-ui/
│       │   ├── 02-daily-ui/
│       │   ├── 03-expense-ui/
│       │   ├── 04-diet-ui/
│       │   ├── 05-plan-ui/
│       │   └── 06-ai-ui/
│       └── specs/
│           └── PRD/               # 6 个产品 PRD（01-06）
├── docker-compose.yml             # 容器编排
├── nginx/conf/                    # 反向代理配置
├── app/                           # Spring Boot 源码（业务实现，非本仓库主交付）
│   ├── config/
│   └── logs/
└── deploy/                        # 备份、迁移、运维脚本
```

### 3.1 命名规则

| 对象 | 规则 | 示例 |
|------|------|------|
| 仓库根目录 | 小写、连字符 | `Lifewise/` |
| 子模块目录 | `{序号}-{名}-ui/` | `01-task-ui/` |
| HTML 原型文件 | `new-{序号}-{模块名}-ui.html` | `new-02-daily-ui.html` |
| 架构文档 | `{类型}-architecture.md` | `business-architecture.md` |
| 版本快照 | `{类型}-v{版本}.md` | `data-model-v1.2-amendment.md` |
| PRD | `{序号}-{模块名}.md` | `01-task-management.md` |
| 容器目录 | 小写英文 | `nginx/`, `app/`, `db/` |

### 3.2 不允许的目录

- `node_modules/`、`target/`、`dist/`、`build/`（构建产物）
- `.idea/`、`*.iml`（IDE 配置）
- `docs-task*.png`、`docs-task-tmp/`（临时截图与草稿）

---

## 4. 代码风格规范

### 4.1 通用原则

- **KISS**：能直接 JS 解决就不引入框架
- **DRY**：6 模块共享的设计 token 必须在所有 UI 原型中保持别名一致
- **YAGNI**：不为 v1.1+ 演进（跨模块洞察、多模态）预留接口
- **不可变**：Java/Python 优先不可变对象；JS 不直接 mutate DOM 状态

### 4.2 命名规则

| 对象 | 命名 | 示例 |
|------|------|------|
| 变量 / 函数 | `camelCase`（英文） | `selectedPlanId`, `renderAside()` |
| 布尔值 | `is` / `has` / `should` / `can` 前缀 | `isLoading`, `hasPermission` |
| 接口 / 类型 / 组件 | `PascalCase` | `PlanCard`, `UserState` |
| 常量 | `UPPER_SNAKE_CASE` | `MAX_RETRY_COUNT`, `MODULE_PATHS` |
| CSS 类 | `kebab-case` | `top-nav`, `side-link`, `module.active` |
| CSS 变量 | `--kebab-case` | `--primary`, `--bg-soft`, `--ink-1` |
| Java 包 | `com.lifewise.{module}` | `com.lifewise.task` |
| Java 类 | `PascalCase` | `TaskService`, `PlanController` |
| 数据库表 | `snake_case`，按模块聚合 | `task`, `plan_milestone`, `outbox_event` |

### 4.3 格式化规则

- **HTML 缩进**：2 空格
- **CSS 缩进**：2 空格
- **JS 缩进**：2 空格
- **行宽**：单行不超过 120 字符
- **引号**：HTML 属性 `双引号`；JS 字符串优先 `单引号`，模板字符串用反引号
- **分号**：JS / Java 必须有；HTML 属性后不写
- **尾逗号**：JS / Java 多行结构保留 trailing comma

### 4.4 注释规则

- **何时写注释**：
  - 解释「为什么」而非「是什么」
  - 标注模块边界、依赖方向、事件契约
  - 标注 OLLAMA / 异步 / 不可变等易踩坑行为
- **语言**：中文描述 + 英文术语
  - ✅ `// 模块边界：禁止跨模块直接修改数据（见 business-architecture §4）`
  - ❌ `// 设置主色变量`
- **TODO 格式**：`TODO(责任人): 描述`，例：`TODO(jiang): 等 v1.1 接入 Outbox`
- **FIXME 格式**：`FIXME(责任人): 描述`，需关联 issue

### 4.5 HTTP API 响应格式

所有 REST 接口统一信封：

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "meta": { "total": 100, "page": 1, "limit": 20 }
}
```

错误时 `success=false`、`data=null`、`error={code, message}`，`meta` 可省。

---

## 5. Git 工作流规范

### 5.1 分支命名

| 类型 | 格式 | 示例 |
|------|------|------|
| 主分支 | `main` | `main` |
| 长期功能 | `feature/{name}` | `feature/auth-design` |
| 短期修复 | `fix/{issue-id}-{slug}` | `fix/42-ai-timeout` |
| 文档 | `docs/{slug}` | `docs/data-model-v1.2` |
| 实验 | `实验/{slug}` | `实验/ai-stream-sse` |

- 分支必须从最新的 `main` 拉取
- 合并到 `main` 前必须通过 `git pull --rebase` 与远端同步
- 严禁 force push 已推送到 `main` 的 commit

### 5.2 提交消息格式

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <description>

<optional body>
```

**type**：`feat` / `fix` / `refactor` / `docs` / `test` / `chore` / `perf` / `ci`

**scope**：`task` / `daily` / `expense` / `diet` / `plan` / `ai` / `arch` / `ui` / `infra`

**示例**：

- `feat(ai): add streaming SSE endpoint for insight generation`
- `fix(expense): correct weekend multiplier in report aggregation`
- `refactor(ui): align top-nav with 02-daily paradigm`
- `docs(arch): update data-model v1.2 amendment`

**body**（可选）：
- 段落说明「为什么」而非「做了什么」
- 列出涉及的关键文件 / 决策点
- 引用相关 PRD / 架构文档

### 5.3 PR 合并流程

1. **预检查**（CI / 本地）：
   - `git diff main...HEAD` 检查无混入敏感信息
   - 所有改动文件经过 code review
   - 测试覆盖率 ≥ 80%（若涉及 Java / Python 业务代码）
2. **PR 模板**：
   - 标题：`<type>(<scope>): <summary>`
   - 描述：背景 / 改动清单 / 测试计划 / 关联 PRD 或 issue
3. **合并策略**：squash merge 到 `main`，保留单一 commit 历史
4. **合并后**：删除本地与远程 feature 分支

### 5.4 严禁操作

- ❌ 直接 push 到 `main` 未经 PR
- ❌ 未经明确许可执行 `git push --force` / `git reset --hard`
- ❌ 提交 `.env` / 密钥 / 证书 / 数据库快照
- ❌ 提交 `node_modules/` / `target/` / `dist/` / `.idea/` / 临时截图

---

## 6. 测试规范

### 6.1 覆盖率要求

- **业务代码（Java）**：≥ 80% 行覆盖
- **关键路径**：100% 覆盖（认证、AI 报告生成、金额计算、Outbox 投递）
- **UI 原型**：不强求单测，但每次 view 改动必须在浏览器中手动验证

### 6.2 测试类型

| 类型 | 工具 | 范围 |
|------|------|------|
| 单元测试 | JUnit 5 + Mockito | Service 层、Util、Domain |
| 集成测试 | Spring Boot Test + Testcontainers | Controller、Repository、Outbox |
| 端到端 | Playwright（若引入） | 6 模块各 1 条主流程 |
| 契约测试 | OpenAPI Generator | API 字段兼容 |

### 6.3 测试命名

- **方法命名**：`{模块}_{场景}_{期望}`，例：`plan_should_fail_when_end_date_before_start_date`
- **测试目录**：`src/test/java/com/lifewise/{module}/`
- **测试文件**：`{被测类名}Test.java`

### 6.4 TDD 流程

1. **红**：先写失败测试
2. **绿**：实现最小通过代码
3. **重构**：消除重复、优化设计
4. **验证**：覆盖率 ≥ 80%

---

## 7. 安全规范

### 7.1 密钥与凭证

- ❌ **严禁**硬编码 API Key / 密码 / Token / 证书
- ✅ 使用 `.env` + Docker Compose `env_file` 注入
- ✅ `.env` 必须加入 `.gitignore`
- ✅ Secret Rotation 周期：JWT 密钥 90 天，DB 密码 180 天

### 7.2 输入验证

- 所有 Controller 入口必须校验 `@Valid` + Bean Validation
- 数据库操作**必须**使用参数化查询（禁止字符串拼接）
- 用户上传的文件：白名单 MIME + 文件大小上限 + 病毒扫描（可选 ClamAV）
- HTML 输出：对用户输入做 escape，禁用 `v-html` / `dangerouslySetInnerHTML`

### 7.3 认证 / 授权

- 邮箱密码 + JWT + Refresh Token
- Refresh Token 必须 rotation + reuse detection
- Spring Security 启用 CSRF（Web 端）+ CORS 白名单
- 密码强度：≥ 12 字符 + 大小写 + 数字 + 符号

### 7.3.1 v1.0 个人版白名单鉴权（临时方案）

v1.0 个人版永远只有一个 user（userId=1）。鉴权由三层防御保证：

1. **nginx 层**（`nginx/conf/conf.d/default.conf`）：在 `/api/ai/chat`、`/api/ai/`、
   `/api/` 三个 location 强制覆盖 `X-User-Id=1`，客户端传任何值都会被丢弃
2. **应用层**（`CurrentUserArgumentResolver`）：白名单校验，非 userId=1
   一律抛 `MissingCurrentUserException` → 401
3. **fail-safe 降级**：missing header 时降级到 userId=1（nginx 故障时的兜底）

**演进路径**：v1.1+ 切换多用户时，删除本节；启用 §7.3 的 JWT + Refresh Token
链路，从 `SecurityContext` 取 principal。

注意：本节覆盖的 nginx + resolver 改动按 `daily` 主 commit 提交，task 模块
作为配套同步（同漏洞修复）。

### 7.4 速率限制

- 接口：每用户 60 req/min（Redis 令牌桶）
- 登录：5 次失败锁定 15 分钟
- AI 报告生成：每用户 10 次/分钟（防 OOM）

### 7.5 错误信息

- 用户面：友好提示（"操作失败，请稍后重试"）
- 服务端：结构化日志（含 trace ID、user ID、错误码）
- ❌ 严禁把堆栈 / SQL / 内部路径暴露给前端

### 7.6 隐私（AI 约束）

- 用户数据**仅在本地**发送给 Ollama deepseek:8b
- ❌ 严禁上传用户数据到云端 LLM
- 所有 AI 报告生成记录写入 `outbox_event` 留痕

---

## 8. 上下文管理

### 8.1 项目级记忆规则

- 全局规则：`~/.claude/CLAUDE.md`（个人全局）
- 项目规则：本文件（仓库根）
- 模块规则：`docs/lifewise/{module}/AGENTS.md`（模块独有约束，按需建立）

### 8.2 文档读取优先级

1. `docs/lifewise/architecture/` 下的架构契约（最高优先级）
2. `docs/lifewise/specs/PRD/` 下的产品定义
3. `docs/lifewise/designs/` 下的 UI 原型（参考实现）
4. Git log 中的历史决策（重要 commit message）

### 8.3 输出语言

- 默认中文回复
- 代码、命令、变量名、文件路径保持英文
- 提交消息保持英文（Conventional Commits）
- 文档文件名保持英文

---

## 9. 评审与变更

- **架构变更**：必须先更新 `docs/lifewise/architecture/`，再动代码
- **UI 变更**：必须先更新 `docs/lifewise/designs/`，再动 app 源代码
- **数据模型变更**：必须先更新 `docs/lifewise/architecture/data-model-v{}.md`，再写 Flyway 迁移
- **跨模块影响**：在 PR description 中必须列出所有受影响的 6 模块

---

## 10. 红线操作（必须先确认）

以下操作即使在 auto-accept 模式下也必须先与用户确认：

- 删除文件 / 目录 / git 历史
- 修改 `.env`、密钥、Token、证书、CI/CD 配置
- `git push` / `git rebase` / `git reset --hard` / 强制推送
- 公开发布（`npm publish`、生产部署等）
- 修改 6 个模块的接口契约（影响跨模块协作）
- 修改数据库表结构（必须先 Flyway 迁移 + 评审）
- 切换 Spring Boot / Java / PostgreSQL 主版本

---

> **维护说明**：本文件随项目主分支演进。修改前请先在 PR 中讨论；修改后请同步更新顶部「版本 / 日期」与相关引用。
