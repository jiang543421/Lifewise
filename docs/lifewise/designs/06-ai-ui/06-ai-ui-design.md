# AI 分析模块 UI 原型设计

> **模块代号**：`ai`
> **所属产品**：数字生活 Lifewise（项目代号：照片档）
> **文档版本**：v1.0
> **状态**：Design Draft
> **创建日期**：2026-07-28
> **关联 PRD**：`docs/lifewise/specs/PRD/06-ai-analysis.md`
> **关联架构**：`docs/lifewise/architecture/business-architecture.md`、`docs/lifewise/architecture/technical-architecture.md`
> **交付物**：本设计文档 + 后续 `06-ai-ui-v1.html` 单文件原型（writing-plans 阶段产出）
> **对齐模块**：05-plan-ui / 03-expense-ui / 04-diet-ui（沿用其 Token 体系、命名风格、Sheet 基座）

> **决策备注**：本设计从首次澄清问题开始，始终采用「**方案 2.1 优化版**（V1 主页 + V6 抽屉 + V4 二级页）」作为 IA 决策。澄清阶段 UI 工具的「(推荐) 1」标记为系统预设，AI 实际选择为方案 2.1。无后续 IA 迭代。

---

## 0. 设计目标与范围

### 0.1 目标

基于 MVP PRD（单模块报告生成 + 智能问答 + AI 健康度），交付**单文件 HTML UI 原型** + 本设计文档，用于：

- **设计契约**：产品、设计、研发、测试的视觉与交互共识
- **用户路径演示**：让 stakeholder 在浏览器走完 F1-F5 全部核心流程
- **工程参考**：为后续前端实现提供布局、组件、交互的设计基准

### 0.2 交付范围（In Scope）

- **6 个视图**（V1 / V2 / V4 / V5 / V6 + V1 内部 4 状态变体；V2 报告级 6 态状态机）
- **5 个关键交互流**（F1-F5，详见 §6）
- **8 个图表组件**（LineChart / BarChart / DonutChart / TopList / HeatCalendar / StackChart / TagCloud / MarkdownRender）
- **11 个 AI 模块专属组件**（AIRobotHeader / AIHealthBadge / DataSnapshotChip / AIInsightChip / ReportFormInput / ReportCard / MetaBar / ChatDrawer / ChatMessageBubble / HistoryListItem / HealthPopover）
- **5 个状态机**（V2 6 态 + V6 4 态 + V5 3 态 + 错误边界 4 类 + 重试策略）
- 移动优先 + 响应式（mobile / tablet / desktop，详见 §6）
- 中保真 + 伪交互（本地 JS 模拟状态切换）

### 0.3 不在范围（Out of Scope）

- 后端 API（原型使用本地 JS 模拟）
- 真实 Ollama 接入（用 mock prompt 模板）
- 真实图表库（SVG/CSS 手绘 8 个图表，不引入 chart.js / ECharts）
- 跨模块洞察（PRD v1.1+）
- 报告订阅（PRD v1.1+）
- 多轮对话（>10 轮）（PRD v1.2+）
- 趋势预测（PRD v1.2+）
- 移动端原生手势（滑动返回等）

### 0.4 关键决策一览

| # | 决策点 | 决策 |
|---|--------|------|
| 1 | 主导航 Tab 位置 | Tab 6「🤖 AI」（在 Me 之后） |
| 2 | 视觉基调 | 墨绿 #2D5043 主色 + 深夜蓝 #3B5BFE 点亮色（局部点缀） |
| 3 | AI 模块内部 IA | V1 主页 + V6 Lv1.5 抽屉 + V4 Lv2 二级页（**方案 2.1**，无 Sub-Tab） |
| 4 | V1 ↔ V2 切换 | 同页布局替换，query param 表达状态（`?jobId=xxx&source=...`） |
| 5 | V6 抽屉复用 | 承载两种模式：snapshot（数据快照）/ qa（智能问答） |
| 6 | V5 健康度 | Popover 浮层（AI Tab 顶部右上角角标，**非全局**） |
| 7 | 5 模块报告设计深度 | Expense / Task / Daily 详图；Diet / Plan 简化复用 |
| 8 | 8 图表实现 | 全部手写 SVG（与 plan-ui / expense-ui / diet-ui 约定一致） |
| 9 | 5 模块配色 | 完全复用各模块既有 cat-* token（AI 模块不污染分类色） |
| 10 | V2 状态机 | 6 态：idle / queued / json_ready / llm_streaming / complete / failed / partial_failed |
| 11 | JSON → LLM 触发 | 默认开启（自动触发），用户可一键关闭（**PRD AI-007「可选开启」+ US-AI-02 AC-1**） |
| 12 | 流式协议 | SSE（Content-Type: text/event-stream） |
| 13 | 失败率阈值 | 30%（5min 滑动窗口） |
| 14 | 重试次数 / 间隔 | **3 次 / 3s**（PRD §8 风险 3「Job Queue 自动重试 3 次」） |
| 15 | 速率限制 | disabled 30s + 输入框右侧计数器 |

---

## 1. 总体架构 & 路由

### 1.1 视图清单（6 个，含 1 浮层）

| # | 视图 | 路由 | Tab 内位置 | 备注 |
|---|------|------|------------|------|
| V1 | 报告生成 | `/ai` | AI Tab 默认首屏 | 模块选择 + 时间段 + 触发 |
| V2 | 报告详情 | `/ai?jobId={jobId}&source={generated\|history\|push}` | 同页布局替换 | 复用 V1 报告卡片 + MetaBar |
| V6 | 问答/快照抽屉 | 路由无（component 状态） | Lv1.5 浮层（叠加在 V1/V2 上） | 承载 snapshot / qa 两种模式 |
| V4 | 历史全屏页 | `/ai/history` | Lv2 二级页 | 列表 + 筛选 |
| V5 | 健康度详情 | Popover（非路由） | AI Tab 顶部右上角角标 | 趋势 + 探活历史 |

### 1.2 架构总览

```
AI Tab（Tab 6，模块代号 ai）
├─ V1 报告生成           ← Tab 默认首屏（输入框固定底部）
├─ V2 报告详情           ← 与 V1 同页布局替换
├─ V6 问答/快照抽屉       ← Lv1.5 浮层（snapshot / qa 模式）
├─ V4 历史全屏页          ← Lv2 二级页
└─ V5 健康度详情          ← Popover 浮层（顶部右上角角标）

全局：AI Tab 顶部右上角持久健康度角标（绿/黄/红）
```

### 1.3 关键技术决策

- **同页布局替换**：V1 ↔ V2 用 query param（`?jobId`）而非 path param，保持 Tab 内单页体感
- **source 区分**：`source=generated`（V1 刚生成）/ `source=history`（V4 跳转）/ `source=push`（v1.1 deep link），决定 MetaBar 元素
- **V6 抽屉复用**：仅承载 snapshot / qa 两种模式（V4 历史列表项点击直跳 V2，不再走 V6 预览）
- **V5 Popover**：避免独立路由，点击 / 失焦 / 滚动 / ESC 自动关闭
- **浏览器后退**：V2 返回 V1（移除 jobId query）；V4 返回 AI Tab（/ai）；不污染全局 Tab 栈

### 1.4 路由 vs 浮层对照

| 视图 | 形态 | 理由 |
|------|------|------|
| V1 | 路由 `/ai` | 主页，可被 deep link |
| V2 | 路由 `/ai?jobId=xxx` | 同页切换用 query param |
| V4 | 路由 `/ai/history` | Lv2 二级页 |
| V5 | Popover | 健康度是辅助信息，不需被刷新 / 分享 |
| V6 | 组件状态（非路由） | 抽屉是组件层 Modal/Teleport |

---

## 2. 信息架构（IA 树）

### 2.1 Lv0 主导航（6 项）

```
Home / Task / Plan / Daily / Me / AI  ← Tab 6
```

### 2.2 Lv1 AI Tab 内部结构

```
Lv1.5 V6 问答/快照抽屉（浮层，叠加在 V1/V2 上方）
└─ V6-A snapshot 模式（V1 数据快照 chip 点击）
└─ V6-B qa 模式（V1「新对话」/ 快捷提问）

Lv1 V1 报告生成（/ai，默认首屏）
├─ Hero 区（30% 屏高，墨绿底 + 深夜蓝头像）
├─ 数据快照卡（4 chip 横向）
├─ AI 关键洞察 chips（生成后填充）
├─ 上次报告卡片（历史折叠）
├─ 报告生成输入框（固定底部 35%）
└─ AIHealthBadge（右上角，触发 V5 Popover）

Lv1 V2 报告详情（/ai?jobId=xxx，同页布局替换）
├─ Header（模块标题 + 生成时间 + 健康度）
├─ AI 关键洞察 chips（10%）
├─ 4 个指标卡（10%）
├─ 数据图表区（50%）
├─ AI 完整解读（30%，默认展开）
└─ MetaBar（5%，按 source 切换）

Lv2 V4 历史全屏页（/ai/history）
├─ ← 返回 │ 报告历史
├─ 筛选条（搜索 / 模块 / 时间范围）
├─ 报告列表（按时间倒序）
└─ 底部留白给 Tab Bar
```

### 2.3 IA 约束清单

| 约束 | 值 | 来源 |
|------|-----|------|
| 最大深度 | ≤ 2（V1/V2 → 抽屉 → V4） | 决策 §信息架构 + 移动端导航规范 |
| Sub-Tab 数量 | 0 | 决策 §信息架构 方案 2.1 |
| 同页 vs 重定向 | V1 ↔ V2 同页（query param 表达） | 决策 §生成跳转 |
| 抽屉数量 | 1 类（V6 复用 snapshot + qa） | 决策 §报告详情 |
| 历史层级 | Lv2 二级页（不是 Sub-Tab） | 决策 §信息架构 |
| 健康度 | Popover（不是路由） | 决策 §1 修订 冲突 4 |
| 路由总数 | 2 个真实路由 + 1 个 query 表达 | `/ai`, `/ai/history`, `/ai?jobId=xxx` |

### 2.4 验收清单

- [ ] iPhone SE（375pt）下 V1 全部 4 段布局可见（最小字号 12pt）
- [ ] V1 → V2 同页切换 < 100ms（query param 表达，无需重新请求报告数据）
- [ ] V6 抽屉可在 V1、V2 上叠加（z-index 200）
- [ ] V4 二级页不阻挡 V1/V2 路径（从 V4 返回 → V1/V2）
- [ ] 健康度 Popover 在 V1/V2/V4 中都可以触发（持久角标）

---

## 3. 核心视图详细设计

### 3.1 V1 报告生成（AI Tab 默认首屏）

**路由**：`/ai`
**视觉基调**：墨绿 #2D5043 底 + 深夜蓝 #3B5BFE 点缀

**布局（自顶向下）**：

```
┌─────────────────────────────────────────┐
│  Hero 区（30% 屏高）                     │  ← 墨绿底 + 深夜蓝头像
│   🤖 AI 助手 + 当日问候                  │
│   "早上好,于博士"                        │
│   "今天有 3 个任务待办,要不要看..."        │
│   🟢 健康度角标（右上角 → V5 Popover）    │
├─────────────────────────────────────────┤
│  数据快照卡（4 chip，横向滚动）            │  ← 深夜蓝边框
│   [📋 任务 12] [📝 日报 5]               │
│   [💰 支出 ¥2,345] [🍱 饮食 1,850]        │
│   ↓ 点 chip → V6 抽屉（snapshot 模式）   │
├─────────────────────────────────────────┤
│  AI 关键洞察 chips（生成后填充）           │  ← 仅 source=generated 时显示
│   [外卖占比 38%] [周末超标 2.3x]          │
│   [预算余 12%] [+ 2 条]                  │
├─────────────────────────────────────────┤
│  上次报告卡片（历史折叠）                  │
│   "上次报告：6月消费报告"                  │
│   "查看 / 重新生成" → V2 (source=history) │
├─────────────────────────────────────────┤
│  ⚪ 留白区（可滚动）                      │
│  ─────────────────────────────────────  │
│  报告生成输入框（固定底部 35%）            │  ← 白底卡片浮起
│   [📋 模块 ▼] [📅 时间段 ▼]              │
│   ┌─────────────────────────────┐        │
│   │ 多行输入框（可选）           │        │
│   │ 例：对比 3 月和 6 月...      │        │
│   └─────────────────────────────┘        │
│   ┌──────────────────────────────┐       │
│   │ ●● 🤖 AI 解读  [开关 ON]      │       │  ← PRD AI-007「可选开启」
│   └──────────────────────────────┘       │
│            [✨ 生成报告]                  │
└─────────────────────────────────────────┘
```

**状态变体**（V1 页面级 4 态，与 V2 报告级 6 态正交）：

| 状态 | 触发条件 | 关键差异 |
|------|---------|---------|
| V1-A 空态 | 用户首次进入 / 无数据 | 顶部 Hero 显示「AI 助手未启动」；输入框 disabled + tooltip「先到 Task 等模块录入数据」 |
| V1-B 待生成 | 已有数据，无报告 | 顶部数据快照正常；底部输入框 enabled；下方无洞察 chips |
| V1-C 生成中 | 已点击「生成报告」 | 输入框替换为进度条 + 「正在生成...」 + 取消按钮；洞察区变 skeleton |
| V1-D 已生成 | 报告完成（同页转 V2） | V1 整体替换为 V2 视图，点 ← 返回 V1-A/B |

> **维度区分**：V1 4 态是**页面级**（基于用户/数据状态）；V2 6 态是**报告级**（基于 job 状态）。V1-C 内部流转 V2 状态机（queued → json_ready → llm_streaming → complete）。V1-A/B 在 V2 视角是 idle；V1-D 在 V2 视角是 complete。

**关键交互**：
- 输入框高度自适应文本（最多 5 行）
- 模块 / 时间段 chip 横向选择
- 「AI 解读开关」：默认 **ON**，用户可关闭；red 健康度=disabled + tooltip「AI 服务暂不可用」（**PRD US-AI-04 AC-3**）
- 「生成报告」按钮：空闲=墨绿底白字；生成中=disabled 灰底；red 健康度=**仍可用**（仅生成 JSON 视图，LLM 解读自动降级）

**时间段选项**（**PRD §6 In Scope**）：
- **本周**：周一至今日
- **本月**：本月 1 号至今日
- **上月**：上月 1 号至上月最后一天
- **最近 30 天**：今日往前 30 天滚动窗口
- **自定义**：用户选择起止日期（最长 1 年）

**模块选项**（**PRD §3 AI-001**）：
- 📋 任务（Task）
- 📝 日报（Daily）
- 💰 消费（Expense）
- 🍱 饮食（Diet）
- 📅 计划（Plan）

### 3.2 V2 报告详情（同页布局替换）

**路由**：`/ai?jobId={jobId}&source={generated|history|push}`
**复用**：与 V1 同页，query param 切换状态

**布局（自顶向下）**：

```
┌─────────────────────────────────────────┐
│  Header（5% 屏高）                       │
│   ← 返回 │ 💰 6月消费报告 │ 2026/07/01   │
│   02:30 │ 🟢 健康度（右上角）              │
├─────────────────────────────────────────┤
│  AI 关键洞察 chips（顶部 10%）            │  ← 深夜蓝底，3-5 条
│   [外卖占比 38%] [周末超标 2.3x]          │
│   [预算余 12%] [+ 2 条]                 │
├─────────────────────────────────────────┤
│  4 个指标卡（10%）                        │  ← 横排，大数据
│   ¥2,345 │ 日均 ¥78 │ 8 笔 │ ¥293/笔   │
├─────────────────────────────────────────┤
│  数据图表区（50%）                        │  ← 模块相关
│   📈 趋势图（LineChart）                  │
│   📊 月度对比（BarChart）                  │
│   🥧 类别占比（DonutChart）                │
│   🏆 Top5（TopList）                      │
├─────────────────────────────────────────┤
│  AI 完整解读（30%，默认展开）              │  ← 极简卡片
│   🤖 "你 6 月支出 ¥2,345,低于预算 12%..." │
│   📌 异常点（最多 3 条）                  │
│   · 周末消费是工作日 2.3 倍                │
│   · 外卖集中于周二、周四                   │
│   💡 建议（最多 2 条）                    │
│   · 试着把外卖控制在每周 2 次             │
│   · 周末预算可考虑改为 ¥200               │
│   👍 12 👎 1 🔄 重新生成                  │
├─────────────────────────────────────────┤
│  MetaBar（5%，按 source 切换）           │
│   source=generated：📥 下载 ⭐ 收藏 🔄 重新生成 │
│   source=history：← 返回 📥 下载 ⭐ 收藏     │
│   source=push：← 返回 📥 下载 ⭐ 收藏       │
└─────────────────────────────────────────┘
```

**6 状态（覆盖 PRD §3 5 态 + partial_failed）**：

| 状态 | 数据 | UI 表现 |
|------|------|---------|
| `idle` | 未触发 | 输入框显示 |
| `queued` | 触发后,JSON 未到位 | 进度条 + 「正在生成 JSON 视图...」 |
| `json_ready` | JSON 拿到,LLM 未启动 | 指标卡 + 图表全显；AI 解读区显示「AI 解读生成中...」（自动触发） |
| `llm_streaming` | JSON + LLM 进行中 | AI 解读区 ⚙️ 流式打字机（typing cursor 闪烁）；首 token < 3s |
| `complete` | 全部完成 | 完整 V2；解读末尾 👍👎🔄 可用 |
| `failed` | 整体失败（API/网络/JSON） | 顶部红色 banner + 报告区空状态 + 重试按钮 |
| `partial_failed` | JSON 完成 + LLM 失败 | 报告区完整 + 解读卡片「💡 AI 解读暂不可用」 + 重试按钮 |

**MetaBar 元素开关矩阵**：

| source | 返回 | 下载 | 收藏 | 重新生成 |
|--------|------|------|------|---------|
| `generated` | — | ✅ | ✅ | ✅ |
| `history` | ✅ | ✅ | ✅ | ❌ |
| `push` | ✅ | ✅ | ✅ | ❌ |

### 3.3 V6 问答/快照抽屉（Lv1.5 浮层）

**触发**：
- V1 数据快照 chip → 抽屉展示 `snapshot` 模式
- V1 顶部「新对话」按钮 / 快捷提问 chip → 抽屉进入 `qa` 模式

**布局（底部 Sheet 80% 屏）**：

```
┌─────────────────────────────────────────┐
│  × │  对话标题 / 数据快照  │ ⓘ  │ ⊕    │  ← 顶部 60dp
├─────────────────────────────────────────┤
│  消息列表 / 快照内容（中部，可滚动）        │
│                                          │
│   ┌───────────────────────────────┐      │
│   │ 用户：昨天花了多少？            │      │  ← 用户气泡（墨绿）
│   └───────────────────────────────┘      │
│   ┌───────────────────────────────┐      │
│   │ AI：昨天支出 ¥234...           │      │  ← AI 气泡（深夜蓝软底）
│   │                            👍👎📊│      │
│   │ [📊 数据来源] ← 点击展开 SQL    │      │
│   └───────────────────────────────┘      │
│                                          │
├─────────────────────────────────────────┤
│  🟢 健康 │ [输入框...] │ 发送            │  ← 底部 80dp
└─────────────────────────────────────────┘
```

**软键盘自适应**：
- 抽屉高度：未输入 60%，输入时 90%（让出 input 空间）
- 监听 `window.visualViewport` 调整高度
- 下拉手势关闭（drag handle 在顶部）

**子状态**：
- **空闲态**：输入框 placeholder「想问点什么？例如：3 月支出多少」
- **规则路径**（< 500ms）：输入后 200ms 内显示「快速匹配中...」 → AI 气泡直接出现
- **LLM 路径**：输入后显示「AI 思考中...」 → 流式打字机 → 完成后显示 👍👎
- **数据来源展开**：点击 📊 → 抽屉内 inline 展示 SQL + 原始数据表（折叠态）

### 3.4 V4 历史全屏页（Lv2 二级页）

> **PRD 解读说明**（US-AI-05 AC-1）：PRD 中描述的"报告历史 **Tab**"在 IA 决策中被解读为 **Lv2 二级页**（路由 `/ai/history`），而非 AI Tab 内部的 Sub-Tab。原因：MVP 阶段 AI 模块 IA 严格遵循 **0 Sub-Tab** 决策（§1.3），避免多层级导航复杂度。

**路由**：`/ai/history`

**布局**：

```
┌─────────────────────────────────────────┐
│  ← 返回 │ 报告历史                      │  ← 顶部 60dp
├─────────────────────────────────────────┤
│  [🔍 搜索] [📋 模块 ▼] [📅 时间范围 ▼]   │  ← 筛选条 60dp
├─────────────────────────────────────────┤
│  报告列表（按时间倒序）                   │
│  ┌─────────────────────────────────┐    │
│  │ 💰 消费报告                      │    │  ← 卡片
│  │ 2026 年 6 月                     │    │
│  │ 生成于 2026/07/01 02:30         │    │
│  │ ⭐ 已收藏                        │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │ 📋 任务报告                      │    │
│  │ 2026 年 5 月                     │    │
│  │ 生成于 2026/06/01 04:15         │    │
│  └─────────────────────────────────┘    │
│  ...                                   │
├─────────────────────────────────────────┤
│  底部留白给 Tab Bar                     │
└─────────────────────────────────────────┘
```

**关键交互**：
- 点击卡片 → 直接跳 V2（同页布局替换为 `source=history`）
- 筛选条件：模块（多选）/ 时间范围（近 7/30/90 天 / 自定义）/ 收藏过滤
- 搜索：按报告标题 / 时间段模糊匹配

### 3.5 V5 健康度详情 Popover

**触发**：AI Tab 内任意位置右上角 🟢 角标

**布局**（角标下方 Popover，320×auto）：

```
┌─────────────────────────────────┐
│  AI 服务健康度                   │
├─────────────────────────────────┤
│  🟢 状态：正常                   │
│  队列堆积：0                     │
│  P95 延迟：2.3s                 │
│  失败率：0.5%                   │
│  5min 趋势：[📈 折线]            │
│  Ollama 进程：运行中              │
│  最近 10 次任务：10 ✅ 0 ❌       │
├─────────────────────────────────┤
│  [查看详细监控] (开发占位)        │
└─────────────────────────────────┘
```

**角标自身状态**（持久可见，AI Tab 内）：

| 状态 | 颜色 | 触发 |
|------|------|------|
| 🟢 健康 | `--success` #10B981 | Ollama 200 + 5min P95 < 5s + 失败率 < 30% |
| 🟡 慢 | `--warning` #F59E0B | 5min P95 ≥ 5s 且 < 30s |
| 🔴 不可用 | `--danger` #EF4444 | Ollama 不可达 / 失败率 > 30% |

**关闭行为**：
- 点击角标再次 → toggle
- 点击 Popover 外区域 → 关闭
- V1/V2 切换时 → 自动关闭
- ESC（桌面端） → 关闭

---

## 4. 组件 & Token 体系

### 4.1 复用现有全局 Token

| 类别 | Token | 复用来源 |
|------|-------|---------|
| 颜色 | `--bg` / `--surface` / `--border` | 01-task-ui |
| 文字 | `--text-1` / `--text-2` / `--text-3` | 01-task-ui |
| 状态 | `--success` / `--warning` / `--danger` | 01-task-ui / 03-expense-ui |
| 字号 | `fs-display` / `fs-h2` / `fs-body` / `fs-caption` | 01-task-ui |
| 间距 | `sp-1` ~ `sp-6` | 01-task-ui |
| 圆角 | `r-sm` / `r-md` / `r-lg` | 01-task-ui |

**5 模块分类色保留不变**（**D 路径**）：AI 模块不污染既有 cat-*。

### 4.2 AI 模块新增 Token（`--ai-*` 前缀，6 个）

```css
/* AI 主色：深夜蓝（AI 点亮色，元素级点缀） */
--ai-accent:        #3B5BFE;   /* AI 头像 / AI 解读标题 / Chip 标签 */
--ai-accent-soft:   #E8EEFF;   /* AI bubble 软底 / Chip 背景 */
--ai-accent-text:   #FFFFFF;   /* AI 色块上的文字 */
--ai-glow:          rgba(59, 91, 254, 0.16);   /* AI 光晕（解读区流式光） */
--ai-divider:       #E8EEFF;   /* AI 区域分割线 */

/* 动效 */
--animation-pulse-ai: ai-pulse 2s ease-in-out infinite;

@keyframes ai-pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
```

**禁止事项**（写进 spec 防止滥用）：
- ❌ `--ai-accent` 用作区块级背景（仅元素级点缀）
- ❌ 引入 `--ai-gradient-*` 渐变 token（违反「墨绿基调」决策）
- ❌ AI 色出现在非 AI Tab 区域（如 Task / Plan 的按钮）
- ❌ AI 色用作大色块、卡片背景

### 4.3 8 个图表组件

> **实现约束**：全部用 SVG/CSS 手绘，不引入 chart.js / ECharts（与 03-expense-ui / 05-plan-ui 约定一致）。

#### 4.3.1 LineChart（趋势图）

**用途**：Expense 趋势 / Diet 营养趋势 / Daily 心情曲线
**数据模式**：连续数值 + 时间序列

```typescript
interface LineChartData {
  series: { name: string; values: number[]; color?: string; smooth?: boolean }[];
  xAxis: string[];
  yAxis?: { min?: number; max?: number; unit?: string; name?: string };
  showArea?: boolean;
}

interface LineChartProps {
  data: LineChartData;
  height?: number;        // 默认 240
  showLegend?: boolean;   // 默认 true
  showAxis?: boolean;     // 默认 true
}
```

**视觉规范**：
- 主线：墨绿 #2D5043，宽 2px
- 辅线：深夜蓝 #3B5BFE（对比系列）
- 区域填充：rgba(45, 80, 67, 0.08)
- 数据点：hover 显示 tooltip
- 移动端：隐藏 Y 轴文字，仅保留刻度线

#### 4.3.2 BarChart（区间对比）

**用途**：Task 优先级分布 / Expense 月度对比 / Plan 时间占用
**数据模式**：类别 + 数值（可堆叠）

```typescript
interface BarChartData {
  items: { label: string; value: number; color?: string }[];
  comparison?: { label: string; value: number; color?: string }[];
  valueUnit?: string;
}

interface BarChartProps {
  data: BarChartData;
  orientation?: 'horizontal' | 'vertical';
  stacked?: boolean;
  highlightIndex?: number;
}
```

**视觉规范**：
- 主柱：墨绿 #2D5043
- 对比柱：中性灰 #94A3B8
- 高亮：深夜蓝 #3B5BFE
- 数字标签：柱顶上方，墨绿字

#### 4.3.3 DonutChart（占比）

**用途**：Task 状态占比 / Diet 营养素占比 / 类别支出占比
**数据模式**：类别百分比

```typescript
interface DonutChartData {
  items: { label: string; value: number; color?: string }[];
  total?: number;
  centerText?: { value: string; label?: string };
}

interface DonutChartProps {
  data: DonutChartData;
  showLegend?: boolean;       // 默认 true
  showPercentage?: boolean;   // 默认 true
}
```

**视觉规范**：
- 扇区按 cat-* token 自动分配色
- 圆心：墨绿大字 + 中性灰说明
- hover：扇区放大 + 显示数值
- 移动端：图例放底部

#### 4.3.4 TopList（榜单）

**用途**：Expense Top5 / Diet Top5 / Task 高频任务
**数据模式**：排序数据

```typescript
interface TopListData {
  items: {
    rank: number;
    label: string;
    sublabel?: string;
    value: number;
    valueUnit?: string;
    icon?: string;
    color?: string;
  }[];
  valueUnit?: string;
}

interface TopListProps {
  data: TopListData;
  limit?: number;        // 默认 5
  showBar?: boolean;     // 默认 true
}
```

**视觉规范**：
- 排名 1-3：圆角徽章（1=金 #F59E0B，2=银 #94A3B8，3=铜 #CD7F32）
- 4-5：纯文字排名
- 数字条：长度映射数值，墨绿色
- 行高：48dp（紧凑型）

#### 4.3.5 HeatCalendar（日历热力图）

**用途**：Daily 写作频次 / Daily 心情 / Plan 日程密度
**数据模式**：二维时间数据（日 × 强度）

```typescript
interface HeatCalendarData {
  cells: { date: ISODate; intensity: number; meta?: object }[];
  dateRange: { start: ISODate; end: ISODate };
  intensityRange?: { min: number; max: number };
}

interface HeatCalendarProps {
  data: HeatCalendarData;
  cellSize?: number;          // 默认 12px
  showMonthHeader?: boolean;  // 默认 true
}
```

**视觉规范**：
- 5 级强度：墨绿 #2D5043 从 20% 到 100% 透明度
- 空数据：#E8EEFF
- 周末：浅边框区分
- hover：tooltip 显示日期 + 强度值 + meta

#### 4.3.6 StackChart（堆叠构成）

**用途**：Diet 早午晚构成 / Task 标签构成 / Plan 时段构成
**数据模式**：多类别构成

```typescript
interface StackChartData {
  xAxis: string[];
  series: { name: string; values: number[]; color: string }[];
  normalized?: boolean;
}

interface StackChartProps {
  data: StackChartData;
  orientation?: 'horizontal' | 'vertical';
  showLegend?: boolean;
}
```

#### 4.3.7 TagCloud（关键词云）

**用途**：Daily 亮点 tag 词云
**数据模式**：词频展示

```typescript
interface TagCloudData {
  tags: {
    text: string;
    weight: number;      // 1-5
    category?: string;
  }[];
}

interface TagCloudProps {
  data: TagCloudData;
  maxTags?: number;     // 默认 30
}
```

**视觉规范**：
- 字号：weight × 4 + 12px = 16-32px
- 颜色：按 category 取 cat-* token
- hover：放大 1.2x + tooltip
- 移动端：字号下限 14px

#### 4.3.8 MarkdownRender（AI 文字渲染）

**用途**：Daily AI 复盘 / V2 AI 完整解读 / 系统通知富文本
**数据模式**：流式文本

```typescript
interface MarkdownRenderProps {
  content: string;
  streaming?: boolean;
  streamingCursor?: boolean;
  collapsible?: boolean;
}
```

**视觉规范**：
- 字体：与 V2 AI 解读区一致（中性灰 #475569）
- H1-H3：墨绿
- 列表：圆点 bullet，墨绿色
- 代码块：浅灰背景
- 加粗：墨绿 #2D5043
- **安全：DOMPurify 强制过滤，禁止内联脚本**
- 流式：typing cursor 在最右侧（1px 宽蓝白交替，闪烁 1Hz）
- 响应式：移动端单列 + 内部滚动（max-height: 70vh + overflow-y: auto），不截断

### 4.4 5 模块报告组件复用矩阵

```
组件           Expense  Task  Plan   Diet   Daily
─────────────────────────────────────────────────
LineChart        ●       -     -     ●      ●
BarChart         ●       ●     ●     -      -
DonutChart       -       ●     -     ●      -
TopList          ●       -     -     ●      -
HeatCalendar     -       -     ●     -      ●
StackChart       -       ●     -     ●      -
TagCloud         -       -     -     -      ●
MarkdownRender   -       -     -     -      ●
```

**模块 layout 复用**：
- **Expense / Task / Plan**：图表主导 layout（指标卡 + 图表 + AI 解读）
- **Diet**：复用 Expense layout
- **Daily**：文字主导 layout（心情曲线 + Markdown 摘要 + 标签云 + 日历热力图）

### 4.5 11 个 AI 模块专属组件

| # | 组件 | 用途 | 引用视图 |
|---|------|------|---------|
| 1 | **AIRobotHeader** | 顶部 Hero（墨绿底+深夜蓝头像） | V1 |
| 2 | **AIHealthBadge** | 右上角健康度角标（绿/黄/红） | V1/V2/V4 |
| 3 | **DataSnapshotChip** | 数据快照横滑卡 | V1 |
| 4 | **AIInsightChip** | AI 关键洞察 chip | V1/V2 |
| 5 | **ReportFormInput** | 底部固定输入框（模块+时间段+文本+AI 解读开关） | V1 |
| 6 | **ReportCard** | 报告详情视图（V2 复用） | V2 |
| 7 | **MetaBar** | 底部 MetaBar（按 source 切换元素） | V2 |
| 8 | **ChatDrawer** | QA/快照抽屉（V6 复用） | V6 |
| 9 | **ChatMessageBubble** | 用户/AI 气泡 | V6 |
| 10 | **HistoryListItem** | 历史列表项 | V4 |
| 11 | **HealthPopover** | 健康度详情 Popover | V5 |

### 4.6 关键组件细节

#### 4.6.1 AIHealthBadge

```typescript
interface HealthBadgeProps {
  status: 'healthy' | 'slow' | 'unavailable';
  latency?: number;       // ms，显示在 Popover 内
  failureRate?: number;   // %，显示在 Popover 内
  onClick?: () => void;
}
```

视觉：圆形 28×28dp，颜色对应 `--success` / `--warning` / `--danger`，边框 2px 白色描边。

#### 4.6.2 AIInsightChip（左侧边条方案）

```typescript
interface InsightChipData {
  text: string;
  category?: 'summary' | 'anomaly' | 'tip';
  highlighted?: boolean;
}

interface InsightChipProps {
  data: InsightChipData;
  onClick?: () => void;
}
```

视觉：
- 圆角胶囊 16dp 圆角
- 内边距：8dp × 12dp
- 背景：纯白（边条方案）
- **左侧 4dp 边条**：
  - `summary`：`--text-3` #9CA3AF
  - `anomaly`：`--warning` #F59E0B
  - `tip`：`--ai-accent` #3B5BFE
- 数字部分：墨绿 #2D5043 加粗
- 标签部分：灰色 #475569

#### 4.6.3 MetaBar

```typescript
interface MetaBarProps {
  source: 'generated' | 'history' | 'push';
  jobId: string;
  isFavorited?: boolean;
  onDownload?: () => void;
  onFavoriteToggle?: () => void;
  onRegenerate?: () => void;
  onBack?: () => void;
}
```

**元素矩阵**：

| source | 返回 | 下载 | 收藏 | 重新生成 |
|--------|------|------|------|---------|
| generated | — | ✅ | ✅ | ✅ |
| history | ✅ | ✅ | ✅ | — |
| push | ✅ | ✅ | ✅ | — |

#### 4.6.4 ChatDrawer（V6 容器）

```typescript
interface DrawerProps {
  open: boolean;
  onClose: () => void;
  variant: 'qa' | 'snapshot';
  height?: '60%' | '80%' | '90%';   // 默认 80%
  title?: string;
  showBackdrop?: boolean;
}
```

视觉：顶部圆角 16dp，背景白色，backdrop rgba(0,0,0,0.4)，slide-up 300ms ease-out。

#### 4.6.5 HealthPopover（V5 容器）

```typescript
interface PopoverProps {
  open: boolean;
  anchor: HTMLElement | null;
  onClose: () => void;
  width?: number;        // 默认 320px
}
```

视觉：定位右对齐 anchor 下方 8dp，圆角 12dp，阴影 0 8px 24px rgba(0,0,0,0.12)。

#### 4.6.6 AIInterpretationToggle（AI 解读开关）

> **PRD 引用**：AI-007「报告 LLM 解读（可选开启）」 + US-AI-02 AC-1「开启「AI 解读」开关后，下方出现「🤖 解读」区域」

```typescript
interface AIInterpretationToggleProps {
  enabled: boolean;            // 默认 true（自动开启）
  available: boolean;          // 健康度计算（red 时 = false）
  onChange: (enabled: boolean) => void;
  reason?: string;             // disabled 时的 tooltip 文案
}
```

视觉：
- 圆角胶囊 24dp 圆角
- 左侧：🤖 AI 解读文字
- 右侧：toggle 开关（墨绿 #2D5043 = ON / 中性灰 = OFF）
- 默认状态：**ON**（自动开启）
- disabled 状态：灰色 + tooltip「AI 服务暂不可用」（PRD US-AI-04 AC-3）

行为：
- **ON**（默认）：json_ready → llm_streaming → complete（流式解读）
- **OFF**：json_ready → complete（仅 JSON 视图，PRD AI-007）

---

### 4.7 统一数据契约

```typescript
// AI 报告统一数据契约
interface AIReport {
  jobId: string;
  module: 'task' | 'daily' | 'expense' | 'diet' | 'plan';
  timeRange: { start: ISODate; end: ISODate };
  generatedAt: ISODate;
  status: 'idle' | 'queued' | 'json_ready' | 'llm_streaming' | 'complete' | 'failed' | 'partial_failed';
  insights: InsightChipData[];
  cards: MetricCard[];
  charts: ChartConfig[];
  llmInterpretation?: string;
  cached: boolean;
}

interface ChartConfig {
  type: 'line' | 'bar' | 'donut' | 'toplist' | 'heatmap' | 'stack' | 'tagcloud' | 'markdown';
  title: string;
  data: any;
  height?: number;
  config?: object;
}

// 路由级 discriminated union
function RenderChart({ config }: { config: ChartConfig }) {
  switch (config.type) {
    case 'line':     return <LineChart data={config.data} height={config.height} />;
    case 'bar':      return <BarChart data={config.data} />;
    case 'donut':    return <DonutChart data={config.data} />;
    case 'toplist':  return <TopList data={config.data} />;
    case 'heatmap':  return <HeatCalendar data={config.data} />;
    case 'stack':    return <StackChart data={config.data} />;
    case 'tagcloud': return <TagCloud data={config.data} />;
    case 'markdown': return <MarkdownRender content={config.data} />;
  }
}
```

### 4.8 LLM 客户端抽象（**PRD §3 AI-041**）

> **PRD 引用**：AI-041「LLM 客户端抽象（LlmClient 接口，可替换）」

```typescript
// LlmClient 抽象接口（实现可替换：Ollama / vLLM / 云端 LLM 等）
interface LlmClient {
  // 单次推理（用于 V6 Q&A 规则未命中时）
  complete(prompt: string, opts?: {
    temperature?: number;   // 默认 0.7
    maxTokens?: number;    // 默认 2048
    stopSequences?: string[];
  }): Promise<LlmResponse>;

  // 流式推理（用于 V2 AI 解读 + V6 Q&A LLM 路径）
  stream(prompt: string, opts?: {
    temperature?: number;
    maxTokens?: number;
  }): AsyncIterable<LlmChunk>;

  // 健康检查（Ollama /api/tags）
  healthCheck(): Promise<{
    ok: boolean;
    latencyMs?: number;
    models?: string[];
  }>;
}

interface LlmResponse {
  text: string;
  tokensUsed?: number;
  latencyMs: number;
}

interface LlmChunk {
  text: string;        // 本次增量
  done: boolean;       // 是否流结束
  tokensUsed?: number;
}
```

**实现策略**：
- MVP 默认实现：`OllamaClient`（deepseek:8b 模型，PRD §9 基础设施）
- 抽象层位置：`apps/api/src/ai/llm/LlmClient.ts`
- 替换方法：实现 LlmClient 接口的新类（如 `VllmClient`、`CloudLlmClient`），在 DI 容器中注入即可
- 不在 MVP 范围：云端 LLM 接入（**PRD §6 Out of Scope「仅本地 Ollama」**）

**Prompt 模板路径**（**PRD §3 AI-044**）：
- 所有 prompt 存放在 `resources/prompts/*.md`
- MVP 模板清单（**PRD §10**）：
  - `MONTHLY_EXPENSE.md`
  - `WEEKLY_TASK.md`
  - `DIET_NUTRITION.md`
  - `QA_SQL_GEN.md`
  - `QA_INTERPRET.md`
  - `DAILY_SUMMARY.md`
- 模板加载：`PromptLoader.load('monthly_expense', { module, timeRange })`

---

## 5. 状态机 & 错误处理

### 5.0 基础设施约束（PRD §3 AI-040 / AI-041 / AI-042）

> **Job Queue 异步化（PRD §3 AI-042）**：报告生成任务**不入 HTTP 请求线程**，通过 Job Queue（Redis / BullMQ）异步执行。HTTP 立即返回 jobId（< 500ms），客户端通过 SSE 订阅进度。

- **任务入队**：`POST /api/ai/reports` → 写 ai_report_job 表 + 入队 → 返回 jobId
- **任务消费**：Worker 进程从队列取出 → 执行 SQL 聚合 → 写入 ai_report JSON → 触发 LLM 流式
- **状态推送**：Worker 通过 SSE channel（`/api/ai/reports/{jobId}/stream`）推送状态变更
- **失败重试**：Job Queue 自动重试 **3 次**（PRD §8 风险 3，间隔 3s）
- **健康检查**：独立 cron（每 30s，**PRD §3 AI-040**）调 `LlmClient.healthCheck()` 写入 ai_health_log

### 5.1 V2 报告 6 态状态机

```
idle ──► queued ──► json_ready ──► llm_streaming ──► complete
            │           │              │              │
            │           │              └── error ─────┼──► partial_failed
            │           │                             │
            ├── error ──┼── error ──────────────────┼──► failed
            │           │                             │
            └───────────┴───────── error ─────────────┘
```

**状态转换矩阵**：

| 当前态 | 触发 | 下一态 | UI 表现 |
|--------|------|--------|---------|
| idle | 点击「生成报告」 | queued | 输入框 disabled + 进度条 |
| queued | JSON 完成 | json_ready | 指标卡 + 图表渲染；AI 解读区「AI 解读生成中...」（自动触发） |
| json_ready | AI 解读开关=**开**（默认） | llm_streaming | 解读区加载，typing cursor 闪烁 |
| json_ready | AI 解读开关=**关** | complete | **跳过 LLM，仅 JSON 视图**（PRD AI-007「可选关闭」） |
| llm_streaming | LLM 完成 | complete | 解读区完整；👍👎🔄 可用 |
| 任意态 | API / 网络 / JSON 错误 | failed | 顶部红色 banner + 报告区空状态 + 重试按钮 |
| llm_streaming / json_ready | LLM 错误 | partial_failed | 报告区完整 + 解读卡片「💡 AI 解读暂不可用」 + 重试按钮 |

**用户感知分级**：
- **用户可见**：idle / queued / json_ready / llm_streaming / failed / partial_failed / complete
- **流式中断**：保留已显示 token + 顶部「⚠️ AI 解读中断，[继续生成]」按钮

### 5.2 V6 Q&A 状态机

```
idle ──► typing ──► rule_match (< 500ms) ──► complete
            │                  │
            │                  └─► llm_streaming ──► complete
            └──► llm_streaming (规则未匹配) ──► complete
```

**3 个分支**：
- **规则路径**（< 500ms）：200ms 内 "快速匹配中" → 1 个气泡回答
- **LLM 路径**：输入 → "AI 思考中" → 流式 → 完成
- **混合路径**：规则 fallback 到 LLM（罕见）

### 5.3 V5 健康度 3 态（5min 滑动窗口）

```
healthy ──► slow ──► unavailable
   ▲          │           │
   └─── 5min ─┘           │
        滑动窗口全部       │
        指标恢复          │
                          └── Ollama 30s 单次探活失败
```

**判定规则**：

| 指标 | 窗口 | 阈值 |
|------|------|------|
| P95 响应时间 | 滑动 5min | ≥ 5s 且 < 30s → slow |
| 失败率 | 滑动 5min | > 30% → unavailable |
| Ollama 探活 | 30s 单次 | 失败 1 次即标记 unhealthy |
| 健康度恢复 | 滑动 5min | 全部指标回到 healthy → 恢复 |

### 5.4 错误边界（4 类）

| 错误类型 | 触发 | 用户感知 | 降级策略 |
|---------|------|---------|---------|
| **API 错误** | 4xx / 5xx 非 200 | 顶部 toast "报告生成失败" + 重试按钮 | 仍可浏览历史报告 |
| **网络错误** | 离线 / 超时 | 顶部红色 banner "网络异常" + 离线状态 | 仅拦截：生成新报告 / Q&A / 健康度刷新；已缓存的历史报告可继续浏览 |
| **LLM 错误** | Ollama 拒绝 / 超时 | 报告区完整 + 解读卡片「💡 AI 解读暂不可用」 | 解读降级为「数据已生成」 |
| **LLM 内容异常** | 输出包含正则白名单外的字符 | 解析失败 → 解读降级为「数据已生成」 | 元数据记录供运维排查 |

**网络错误时操作矩阵**：

| 操作 | 网络错误时 |
|------|----------|
| 浏览历史报告 | ✅ 可用（localStorage） |
| 切换历史报告 Tab | ✅ 可用 |
| 生成新报告 | ❌ disabled + 提示「网络异常，请稍后重试」 |
| Q&A 提问 | ❌ disabled |
| 健康度刷新 | ❌ 探活失败，角标保持上一态 |
| 历史报告的「重新生成」按钮 | ❌ disabled |

### 5.5 失败降级路径（PRD §8 风险应对）

| 风险 | 降级路径 |
|------|---------|
| Ollama 不可用 | JSON 视图正常 + 解读区显示「数据已生成，AI 解读暂不可用」+ 健康度角标红 + **Job Queue 自动重试 3 次**（PRD §8 风险 3） |
| LLM 推理 > 30s | 自动 cancel + 降级为「纯 JSON 视图」+ 健康度角标黄 |
| LLM 生成 SQL 注入（后端） | AST 解析拒绝 + 友好提示「问题未能理解，请换种问法」 |
| LLM 输出 XSS（前端） | DOMPurify 强制清洗（MarkdownRender 组件内置） |
| LLM 输出有毒内容 | 输出正则校验 + 解读卡片显示「⚠️ AI 解读仅供参考」水印 |
| 用户隐私顾虑 | 仅本地 Ollama + 产品页明确「数据不出本机」+ 用户可一键关闭 |
| 速率限制 | 每用户 10 req/min + 60 req/h + 全局 100 req/min |

### 5.6 白名单正则（allowed-chars.ts）

```typescript
// 涵盖：CJK + 平假/片假名 + 拉丁字母 + 数字 + 空白 + 标点 + 符号 + 换行
// 不允许：HTML 标签、控制字符、私用 Unicode 区、Emoji 异常字形
// 不匹配 → 丢弃该段 + 标记「⚠️ AI 解读片段异常，已隐藏」
const ALLOWED_PATTERN =
  /^[一-龥぀-ゟ゠-ヿa-zA-Z0-9\s\p{P}\p{S}\n\r]+$/u;
```

**Unicode 范围明示**（便于国际化扩展）：

| 类别 | 范围 | 含义 |
|------|------|------|
| `一-龥` | U+4E00 - U+9FA5 | CJK Unified Ideographs |
| `぀-ゟ` | U+3040 - U+309F | Hiragana |
| `゠-ヿ` | U+30A0 - U+30FF | Katakana |
| `a-zA-Z0-9` | ASCII | Latin / digits |
| `\s` | ASCII | whitespace |
| `\p{P}` | Unicode | Punctuation |
| `\p{S}` | Unicode | Symbols |
| `\n\r` | ASCII | newlines |

### 5.7 重试策略

| 场景 | 重试策略 | UI 反馈 |
|------|---------|---------|
| 单报告生成失败 | **自动重试 3 次**（间隔 3s，**PRD §8 风险 3**） | 顶部 toast "重试中... 1/3" → "2/3" → "3/3" |
| 单报告第 4 次失败（重试 3 次仍失败） | 停止重试 + 红色 banner | 「[重试] 按钮」手动触发 |
| LLM 流式中断 | 保留已显示内容 + 「[继续生成]」按钮 | 用户手动 |
| Q&A 失败 | 不自动重试 | 气泡显示「回答失败」+ 重试按钮 |
| 健康度持续 red | 30s 探活 + 指数退避 | 角标持续 red + Popover 详情 |

### 5.8 速率限制（PRD §3 AI-043 + §8 风险 7）

**三层限流**：
- 每用户 10 req/min + 60 req/h
- 全局 100 req/min

超过时：
- 输入框右侧小字「已用 10/10 次/分钟」（实时计数器）
- 输入框 disabled 30s
- 健康度角标变黄（提示用户）
- 顶部 toast「请求过于频繁，请稍后再试」

**异常用户自动熔断**（**PRD §8 风险 7 应对**）：
- **触发**：单用户 **5 分钟内触发熔断阈值 ≥ 3 次**（即反复触发限流）
- **行为**：自动熔断 **15 分钟**，期间所有 AI 操作直接返回 429
- **熔断期间 UI**：V1 输入框持续 disabled + 顶部红色 banner「账号已被临时限制，请 15 分钟后再试」
- **降级**：熔断期内 health badge 保持黄（不触发 red），提示"功能受限中"
- **解除**：15 分钟后自动恢复；服务端记录熔断日志供运营回溯

---

## 6. 响应式 & 交互流

### 6.1 断点定义

```
mobile:   < 768px
tablet:   768 ~ 1024px
desktop:  ≥ 1024px
```

AI Tab 内的布局切换：

| 视图 | mobile | tablet | desktop |
|------|--------|--------|---------|
| V1 报告生成 | 单列，底部输入框固定（V1-C 例外） | 单列，截图区可双列 | 4 chip 双列 + 输入框右下 |
| V2 报告详情 | 单列，图表堆叠 | 指标卡双列 + 图表单列 | 指标卡 4 列 + 图表 2x2 |
| V6 抽屉 | 60% 屏 | 70% 屏 | 60% 屏（居中） |
| V4 历史 | 单列 | 单列 | 列表双列 |

### 6.2 8 个图表响应式

| 组件 | mobile | desktop |
|------|--------|---------|
| LineChart | 高度 200px, 隐藏 Y 轴文字 | 高度 280px, 显示完整 Y 轴 |
| BarChart | 横向排列，柱子高度 180px | 柱状 280px，数字可旋转 |
| DonutChart | 外径 160px（图例底部） | 外径 200px（图例右侧） |
| TopList | 5 行，行高 48dp | 5 行 + 横向条形图 |
| HeatCalendar | **3 月/屏 + 横向滑动**，单元格 10px | 12 月/屏，单元格 12px |
| StackChart | 高度 200px，图例底部 | 高度 280px，图例右侧 |
| TagCloud | 字号下限 14px，2 列 | 字号 16-32px，3 列 |
| MarkdownRender | 单列 + 内部滚动（max-height: 70vh + overflow-y: auto，不截断） | 双栏 + 目录导航 |

### 6.3 5 个关键交互流

#### F1 首次进入 AI Tab

```
1. 用户点击 Tab 6「🤖 AI」
2. 进入 /ai，触发 V1 加载
3. 检查 Ollama 健康度（30s 探活 + 5min 滑动窗口）
4. 显示 V1 状态：
   - 健康：V1-B（待生成）
   - 慢：V1-B 顶部加黄色提示「AI 解读可能延迟」
   - 不可用：V1-A（空态）+ 输入框 disabled + tooltip「AI 服务暂不可用」
5. 加载数据快照（4 chip 横向）
6. 加载历史折叠区（最近 3 条 + 查看全部）
7. 用户可点击「生成报告」开始 F2
```

#### F2 生成报告全流程（SSE 推送）

```
1. V1-B 用户点击「生成报告」
   → POST /api/ai/reports → 立即返回 jobId
   → 路由：/ai?jobId=xxx&source=generated
   → V1 切换为 V2 视图（同页布局替换）
2. V2 显示 queued 进度条
3. 后端通过 SSE 推送 JSON 进度（Content-Type: text/event-stream）
   → V2 切换到 json_ready
   - 指标卡渲染
   - 图表渲染（按模块选 4-5 个）
   - **检查「AI 解读开关」（PRD AI-007）**：
     - **开（默认）**：AI 解读区显示「AI 解读生成中...」自动触发
     - **关**：跳过 LLM，直接 complete（仅 JSON 视图）
4. LLM 开始流式 → V2 切换到 llm_streaming
   - typing cursor 闪烁
   - 解读文本逐字呈现（首 token < 3s）
5. LLM 完成 → V2 切换到 complete
   - 解读末尾 👍👎🔄 可用
6. 失败处理：
   - 解读失败 → partial_failed（解读卡片「💡 AI 解读暂不可用」）
   - 整体失败 → failed（顶部红色 banner + 重试，**Job Queue 自动重试 3 次**——PRD §8 风险 3）
   - 流式中断 → 保留已显示 + 「[继续生成]」按钮
7. 用户可操作 MetaBar：
   - 生成态：下载 / 收藏 / 重新生成
   - 历史态：返回 / 下载 / 收藏
```

**SSE 协议选型**：
- ✅ 单向推送（服务端 → 客户端），AI 报告状态就是单向
- ✅ 基于 HTTP，无需额外端口（WebSocket 需独立 ws 端口）
- ✅ Ollama 自身用 HTTP + SSE 风格 streaming，AI 模块顺势对齐
- ✅ 自动重连，断网恢复友好
- ❌ 不用 WebSocket（双向需求，AI 报告不需要）
- ❌ 不用长轮询（延迟高、连接管理复杂）

#### F3 历史报告查看与切换

```
1. V1-B 用户点击「查看全部」→ /ai/history
2. V4 加载历史列表（按时间倒序）
3. 用户可筛选：模块 / 时间范围 / 收藏
4. 列表项点击 → 直接跳 V2（同页布局替换为 source=history）
   - V2 顶部 MetaBar 显示：返回 / 下载 / 收藏
5. V2 历史态显示 MetaBar：返回 / 下载 / 收藏
6. 用户点击「返回」→ URL 移除 jobId，回到 V1-B
```

#### F4 Q&A 对话流程

```
0. V6 qa 模式打开 → 加载**对话历史**（**PRD AI-025「保留 30 天」**，单次会话 ≤ 10 轮）
1. V1 数据快照 chip 点击 → V6 抽屉打开（snapshot 模式）
2. V1 顶部「新对话」按钮 或 快捷提问 chip → V6 抽屉打开（qa 模式）
3. 用户输入问题 → 触发：
   - 规则引擎（200ms 内尝试匹配）
   - 匹配：直接单气泡回答（< 500ms）
   - 不匹配：进入 LLM 慢路径
4. LLM 慢路径：
   - "AI 思考中..." 气泡
   - 后端生成 SQL → 校验 → 执行 → 结果回灌 LLM → 输出 Markdown
   - 流式打字机呈现
5. 用户可操作：
   - 👍 / 👎 反馈
   - 📊 数据来源 → 内联展开 SQL + 原始数据
   - 下拉关闭抽屉
6. 失败处理：< 500ms 重试按钮 / 30s 后再问
```

**对话历史策略**（PRD AI-025 + Out of Scope）：
- **时间维度**：保留 **30 天**（超出自动清理）
- **轮次维度**：单次会话 ≤ **10 轮**（>10 轮不计入 v1.0）
- **持久化**：服务端 ai_chat_history 表；前端 V6 抽屉打开时拉取最近 1 条会话
- **入口**：抽屉顶部「历史」按钮 → 弹出 30 天会话列表（按时间倒序）

#### F5 健康度降级恢复

```
1. 探活周期 30s + 5min 滑动窗口
2. 健康 → 降级：
   - P95 ≥ 5s → 角标变黄 + Popover 详情
   - 失败率 > 30% / Ollama 不可达 → 角标变红
   - 解读区自动降级为「💡 AI 解读暂不可用」
3. 降级 → 恢复：
   - 5min 窗口内 P95 < 5s + 失败率 < 30% → 角标恢复绿
   - 解读区恢复自动 LLM
4. 用户主动：
   - 点角标 → V5 Popover：当前态 + 5min 趋势 + 探活历史
   - 点 Popover 外区域 / ESC → 关闭
```

---

## 7. 验收清单

### 7.1 组件验收

- [ ] 8 个 chart component 都有 TypeScript 接口定义
- [ ] 11 个 AI 模块专属组件 TypeScript 完整
- [ ] 复用 token 数量 ≥ 60%（避免引入重复）
- [ ] AI 模块新增 token ≤ 6 个
- [ ] 图表全手写 SVG（ECharts / chart.js 0 引入）
- [ ] 统一数据契约 AIReport 定义完整
- [ ] AI 色用量 ≤ 5%（可图像采样校验）
- [ ] DOMPurify 在 MarkdownRender 中强制过滤

### 7.2 视图验收

- [ ] V1 4 状态全展示（V1-A 空态 / V1-B 待生成 / V1-C 生成中 / V1-D 已生成）
- [ ] V2 6 状态全展示（idle / queued / json_ready / llm_streaming / complete / failed / partial_failed）
- [ ] V6 4 子状态（idle / 规则匹配 / LLM 路径 / 数据来源展开）
- [ ] V5 3 态全展示（healthy / slow / unavailable）
- [ ] V4 列表 + 筛选 + 搜索可用

### 7.3 交互流验收

- [ ] F1：首次进入 AI Tab 4 态健康度分支全可见
- [ ] F2：生成报告全流程 SSE 推送，首 token < 3s
- [ ] F3：历史报告 V4 → V2 直跳（无 V6 预览）
- [ ] F4：Q&A 规则路径 < 500ms / LLM 路径流式
- [ ] F5：健康度降级恢复可观察

### 7.4 响应式验收

- [ ] iPhone SE（375pt）下 V1 全部 4 段布局可见
- [ ] V1 → V2 同页切换 < 100ms（query param 表达）
- [ ] V6 抽屉可在 V1、V2 上叠加（z-index 200）
- [ ] V4 二级页不阻挡 V1/V2 路径
- [ ] 健康度 Popover 在 V1/V2/V4 中都可触发

### 7.5 安全验收

- [ ] DOMPurify 在 MarkdownRender 强制过滤
- [ ] 白名单 regex 拒绝非法字符
- [ ] 速率限制每用户 10 req/min + 60 req/h
- [ ] SQL 注入防御在后端 LLM→SQL 生成路径（PRD AI-023）

---

## 8. 关键决策日志（决策追踪）

| # | 决策点 | 决策 | 来源 |
|---|--------|------|------|
| 1 | 主导航 Tab 位置 | Tab 6「🤖 AI」 | 澄清 1 |
| 2 | 视觉基调 | 墨绿 + 深夜蓝 | 澄清 2 |
| 3 | AI 模块内部 IA | V1 主页 + V6 Lv1.5 抽屉 + V4 Lv2 二级页 | 澄清 3（方案 2.1） |
| 4 | V1 ↔ V2 切换 | 同页布局替换，query param | 澄清 4 |
| 5 | V6 抽屉复用 | snapshot / qa 两种模式 | §1 修订 |
| 6 | V5 健康度 | Popover（非路由） | §1 修订 冲突 4 |
| 7 | 5 模块设计深度 | Expense / Task / Daily 详图；Diet / Plan 简化复用 | 澄清 5 |
| 8 | 8 图表实现 | 全部手写 SVG | 澄清 §4 |
| 9 | 5 模块配色 | 复用既有 cat-* token，AI 不污染（D 路径） | 澄清 §4 核验 1 |
| 10 | V2 状态机 | 6 态（failed + partial_failed） | §5 M2 |
| 11 | JSON → LLM 触发 | 默认开启（自动触发），用户可一键关闭（**PRD AI-007「可选开启」+ US-AI-02 AC-1**） | §5 M1 |
| 12 | 流式协议 | SSE | §6 M3 |
| 13 | 失败率阈值 | 30%（5min 滑动窗口） | §5 S3 |
| 14 | 重试次数 / 间隔 | **3 次 / 3s**（PRD §8 风险 3「Job Queue 自动重试 3 次」） | §5 S4 |
| 15 | 速率限制 | disabled 30s + 输入框右侧计数器 | §5 S5 + O3 |
| 16 | InsightChip 视觉 | 左侧边条方案 | §4 核验 |
| 17 | V3 状态机合并 | queued 合并到 idle 进度条 | §5 核验（保持独立） |
| 18 | 网络错误不禁用历史 | 局部禁用矩阵 | §5 M3 |
| 19 | LLM 错误降级 | JSON 视图完整 + 解读区降级 | §5 核验 |
| 20 | MarkdownRender 移动端 | 内部滚动（max-height 70vh，不截断） | §6 M2 |
| 21 | HeatCalendar mobile | 3 月/屏 + 横向滑动 | §6 M1 |
| 22 | V4 → V2 路径 | 直跳 V2（删 V6 预览） | §6 K1 |
| 23 | AI 解读开关组件 | 默认 ON，可关闭（PRD AI-007 + US-AI-02 AC-1） | PRD 对齐 P0-1 |
| 24 | Job Queue 重试次数 | **3 次**（PRD §8 风险 3） | PRD 对齐 P0-2 |
| 25 | red 健康度降级 | 「生成报告」可用（JSON），「AI 解读开关」disabled（PRD US-AI-04 AC-3） | PRD 对齐 P0-3 |
| 26 | 30 天对话历史 | V6 抽屉打开时拉取最近 30 天会话（PRD AI-025） | PRD 对齐 P1-4 |
| 27 | 异常用户自动熔断 | 5 分钟内触发 3 次熔断 → 自动熔断 15 分钟（PRD §8 风险 7） | PRD 对齐 P1-5 |
| 28 | 时间段选项 | 本周/本月/上月/最近 30 天/自定义 5 项（PRD §6 In Scope） | PRD 对齐 P1-6 |
| 29 | LlmClient 抽象 | `LlmClient` 接口 + OllamaClient 默认实现（PRD AI-041） | PRD 对齐 P2-7 |
| 30 | Prompt 模板路径 | `resources/prompts/*.md`（PRD AI-044 + §10） | PRD 对齐 P2-8 |
| 31 | Job Queue 异步化 | 报告任务不入 HTTP 线程，Redis/BullMQ 异步执行（PRD AI-042） | PRD 对齐 P2-9 |
| 32 | 历史 Tab 解读 | PRD「Tab」→ Lv2 二级页 `/ai/history`（0 Sub-Tab 决策） | PRD 对齐 P2-10 |

---

## 9. 后续步骤

- **writing-plans 阶段**：基于本 spec 创建实施计划
- **HTML 原型**：writing-plans 阶段产出 `06-ai-ui-v1.html` 单文件原型
- **测试范围**：F1-F5 5 个交互流 + 6 个状态机 + 5 个模块 report 视图

---

*文档版本：v1.0-draft*
*下一步：spec self-review → user review → writing-plans*
