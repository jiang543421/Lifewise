# 日报模块 UI 原型设计

> **模块代号**:`daily_report`
> **设计日期**:2026-07-26
> **文档版本**:v1.1(2026-07-26 PRD 验证修订)
> **状态**:Approved Design
> **修订记录**:
> - v1.1 修 4 项 P0 偏差:① 默认心情 3 星(对齐 US-DR-01 AC-2)② AI 摘要卡增加「删除」操作(对齐 US-DR-03 AC-2)③ 搜索 `q` 字段范围 = title + contentMd + highlights.name(对齐 US-DR-04 AC-1)④ 新增 Job Queue / 分区表 / 采纳率 等后端职责声明
> **关联 PRD**:`docs/lifewise/specs/PRD/02-daily-report.md`
> **关联架构**:`docs/lifewise/architecture/business-architecture.md`、`docs/lifewise/architecture/technical-architecture.md`
> **交付物**:本设计文档 + `02-daily-ui-v1.html` 单文件原型
> **对齐模块**:`01-task-ui` 设计范式(命名风格 / Token 体系 / Sheet&Dialog 基座复用)

---

## 0. 设计目标与范围

### 0.1 目标

基于 MVP PRD(日报 CRUD + 心情 + 亮点 + Markdown + 月度时间线 + AI 摘要可降级 + 搜索筛选 + 导出),交付**单文件 HTML UI 原型**,用于:

- 设计契约:产品、设计、研发、测试的视觉与交互共识
- 用户路径演示:让 stakeholder 在浏览器走完所有核心流程
- 工程参考:为后续前端实现提供布局、组件、交互的设计基准

### 0.2 交付范围(In Scope)

- **5 个日报核心视图**(见 §2)
- **8 个核心交互流**(见 §6)
- 移动优先 + 响应式(平板 / 桌面,详见 §9)
- 11 个日报专属组件 + 5 个全局复用 + 1 个全局触发器 + **15 个设计令牌(含 1 个新增 `--ai-timeout-ms`)**(详见 §3、§4)
- **AI 摘要严格 5 状态**(未生成 / 生成中 / 生成成功 / 生成失败 / 不可用),详见 §7.5
- **自动保存严格 5 状态**(编辑中 / 正在保存 / 草稿已保存 / 保存失败 / 冲突),详见 §7.1
- **业务状态严格 4 状态**(草稿 / 已完成 / 保存失败 / 冲突,正交),详见 §7.5.2
- 中保真 + 伪交互(本地 JS 模拟状态切换)
- **不实现关键词云 DR-013**(V2 顶部预留 56px 折叠区)

### 0.3 不在范围(Out of Scope)

- 后端 API(原型使用本地 JS)
- 真实 Ollama 调用(原型用 1.5s setTimeout + 随机失败模拟)
- **真实 AI 摘要需后端 Job Queue 异步化**(PRD §9 基础设施;原型层同步模拟,仅前端 B/D 状态切换,无服务端任务队列)
- v1.1 自动复盘推送(22:00) / 周报 / 月报 / 心情异常预警
- v1.2 图片 / 语音 / OCR
- v1.3 协作分享 / 团队日报墙 / 日报模板
- 关键词云(DR-013,待确认;V2 顶部预留折叠区)
- 写未写的历史日期 / 未来日期编辑
- **按月分区表 / 超 1 年归档**(PRD §8 风险 #3;后端职责)
- **prompt 校验**(PRD §8 风险 #1 应对;后端职责,前端仅展示「建议」标签)
- **AI 摘要采纳率埋点**(PRD §1 KR1.2;行为日志,不在原型内实现)

---

## 1. 信息架构(IA)

### 1.1 顶层结构

```
App 入口
 └─ 今日 Dashboard(默认首屏)         ← 含「📝 今日日报」快入口卡
      ├─ 「任务」入口 → 任务模块(沿用 01-task-ui)
      ├─ 「习惯」入口 → 习惯模块(沿用 01-task-ui)
      └─ 「📝 日报」入口 → 日报模块(V1-V5)
           ├─ V2 日报时间线(月)(默认;含 Overlay 搜索筛选)
           └─ V3 写日报页(默认今日)
 └─ 全局回收站(G1,任务/习惯/日报共享)
 └─ 全局导出中心(G2,日报模块通过 DailyExportTrigger 触发)
 └─ 全局通知中心(G3,降级)
```

### 1.2 5 项 Tab Bar

```
┌──────────────────────────────┐
│ 🏠   │ 📋  │ 🎯  │ 📝 • │ 👤 │
│ 今日 │ 任务 │ 习惯 │ 日报 │ 我的│
└──────────────────────────────┘
                                ↑
                         徽章规则(§1.3)
```

- 「📝 •」徽章 = 当日未写日报
- 顺序理由:任务/习惯是行为驱动(白天),日记是反思(晚上),📝 排在 👤 前

### 1.3 徽章消失规则

> **进入写日报页 ≠ 徽章消失**。
>
> 徽章消失**唯一**条件:今日日报 Markdown **非空** **且** 自动保存**成功**。
>
> 空内容草稿 / 正在保存 / 保存失败 / 冲突 → 红点保留。

### 1.4 核心视图(5 个)+ 全局共享(3 个)

**日报模块核心视图(5 个)**:

| # | 视图 | 路由 | 关联 PRD |
|---|---|---|---|
| **V1** | 今日 Dashboard | `/` | US-DR-01 AC-1,日报快捷入口 |
| **V2** | 日报时间线(月) | `/daily/timeline` | DR-010,DR-011,DR-012 |
| **V3** | 写日报页 | `/daily/edit?date=YYYY-MM-DD` | DR-001,DR-002,DR-005,DR-006,DR-007,DR-008 |
| **V4** | 日报详情页 | `/daily/detail/:date` | DR-003,DR-005,DR-006,DR-007,DR-008,DR-011 |
| **V5** | **搜索筛选 Overlay**(V2 顶部下推,**非独立页面**) | URL:`/daily/timeline?search=...&moodMin=4...&dateFrom=...&dateTo=...&highlights=...` | DR-020,DR-021,DR-022,DR-023 |

**全局共享能力(不计入日报视图数)**:

| # | 视图 | 路由 | 备注 |
|---|---|---|---|
| **G1** | 全局回收站 | `/recycle-bin` | 任务 / 习惯 / 日报共享;日报软删除 30 天可恢复 |
| **G2** | 全局导出中心 | `/export` | 单条 .md / 月度 .zip;日报模块通过 `DailyExportTrigger` 触发 |
| **G3** | 全局通知中心 | `/notifications` | AI 摘要生成完成通知降级,复用之 |

### 1.5 各核心视图的状态矩阵(通用 4 状态)

| 视图 | 空 | 加载 | 失败 | 成功 | 备注 |
|---|---|---|---|---|---|
| **V1 Dashboard** | ✅ 今日未写 | ✅ 骨架 100ms | ✅ 加载日报失败 toast+重试 | ✅ 含📝快捷卡 | — |
| **V2 时间线(月)** | ✅ 当月暂无 / 该月暂无 | ✅ 骨架 100ms | ✅ 月份加载失败 toast+重试 | ✅ 双区(图+列表) | 切月份独立加载 |
| **V3 写日报** | ✅ 空白 + **默认心情 3 星**(US-DR-01 AC-2)| ✅ 进入页骨架 | ✅ 5 种业务状态(§7.1)| ✅ 自动保存成功 | — |
| **V4 详情页** | ✅ 当日未写 →"暂无日报";**历史未写不提供补写 CTA** | ✅ 骨架 100ms | ✅ Markdown 渲染降级纯文本 | ✅ 内容完整 | AI 5 状态(§7.5)|
| **V5 Overlay** | ✅ 初始"试试关键词";无结果"换个关键词" | ✅ 输入防抖 500ms | ✅ 网络失败 toast | ✅ 高亮命中 | URL 序列化 |

---

## 2. 页面清单(5 视图 + 全局 3 视图)

| # | 视图 | 触发 | 备注 |
|---|---|---|---|
| 1 | V1 今日 Dashboard | App 启动 | 含📝快入口卡 |
| 2 | V2 日报时间线(月) | Tab 📝 → 默认 | 双区:心情图 + 日列表;月份切换 |
| 3 | V2 顶部 V5 搜索 Overlay | V2 顶部🔍 | 非独立页面;URL 与 V2 同源 |
| 4 | V3 写日报页 | V1 卡 / V4 编辑 / Dashboard | 静默自动保存;**markdown 非空 + 保存成功 → 红点消失** |
| 5 | V4 日报详情页 | V2 列表点击(已写日) | Markdown + AI 摘要 + 操作区 |
| 6 | V5(月度心情图全屏 Drawer)| V2 mini 心情图长按 | sm/md 底部 Sheet;lg 居中 modal |
| 7 | DailyExportTrigger(Sheet)| V2 / V4 顶部📤 | 唤起 G2 GlobalExportSheet:`{ type:'daily', scope:'single'\|'month' }` |
| 8 | G1 全局回收站 | 👤 我的 → 回收站 | 任务/习惯/日报三模块共享(展示日报条目)|
| 9 | G3 全局通知中心 | 顶部铃铛 | AI 摘要完成通知降级展示 |

---

## 3. 设计 Token

### 3.1 复用(01-task-ui 已定义)

| 类别 | Token | 取值 | 用途 |
|---|---|---|---|
| 颜色 | `--bg` `--surface` `--border` | `#F7F7F8` `#FFF` `#E5E7EB` | 背景、卡片、描边 |
| 颜色 | `--text-1/2/3` | `#111827` `#6B7280` `#9CA3AF` | 主/次/占位文字 |
| 颜色 | `--brand` `--brand-soft` | `#4F46E5` `#EEF2FF` | 主色(激活 Tab、CTA、链接)|
| 颜色 | `--success` `--danger` `--warning` | `#10B981` `#EF4444` `#F59E0B` | 成功 / 删除 / 警告 |
| 字号 | `display 24/600` · `title 17/600` · `body 15/400` · `caption 13/400` · `micro 11/500` | — | 5 级 |
| 间距 | `4 · 8 · 12 · 16 · 24` | px | 5 档 |
| 圆角 | `8 · 12 · 16` | px | small/medium/sheet |
| 字体栈 | `PingFang SC` → `HarmonyOS Sans SC` → `Source Han Sans CN` → `Microsoft YaHei` → 系统无衬线 | — | 中文优先 |

### 3.2 日报新增 token

| Token | 值 | 用途 |
|---|---|---|
| `--mood-1` | `#EF4444` | 很差(1 星)— 红 |
| `--mood-2` | `#F97316` | 不好(2 星)— 橙 |
| `--mood-3` | `#A8A29E` | 一般(3 星)— **中性灰**(避免与 `--warning` 同色相)|
| `--mood-4` | `#84CC16` | 不错(4 星)— 浅绿 |
| `--mood-5` | `#10B981` | 非常好(5 星)— 绿 |
| `--mood-empty` | `#D1D5DB` | 未写心(空心灰)|
| `--ai` | `#8B5CF6` | AI 摘要强调色(紫)|
| `--ai-soft` | `#F3E8FF` | AI 卡片背景 |
| **`--ai-timeout-ms`(★ 新增)** | **`30000`** | **AI 摘要 B → D 超时阈值;前端 JS + CSS 同步读取;token 化便于调整**|

> **Emoji 优先于颜色**:心图标默认走 emoji 😞(1) → 🙁(2) → 😐(3) → 🙂(4) → 😄(5);颜色仅用于边框 / 背景 / 折线图。
>
> **心情 token 仅作图形装饰,不承载文字**(§10.2):心情 5 色允许未达 WCAG AA 4.5:1,因不直接承载文字;状态色(`--success/--danger/--warning`)始终满足 AA。

### 3.3 字号与间距(沿用 01-task-ui)

- 间距(5 档):`4` / `8` / `12` / `16` / `24px`
- 圆角(3 档):`8`(small) / `12`(medium) / `16`(sheet)

### 3.4 字体栈

```css
font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Source Han Sans CN', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
```

字体优先级:`PingFang SC`(macOS / iOS) → `HarmonyOS Sans SC`(华为生态) → `Source Han Sans CN`(Linux / 跨平台开源) → `Microsoft YaHei`(Windows) → 系统无衬线。

---

## 4. 核心组件

### 4.1 全局复用(沿用 01-task-ui 命名,无前缀)

| 组件 | 用途 | 状态 |
|---|---|---|
| `BottomTab5` | 5 项 Tab Bar | 当前激活、红点、Badge |
| `AppHeader` | 顶部栏 | 返回 / 标题 / 右侧操作 / 复合 Badge |
| `EmptyState` | 空态 | 分支文案(沿用任务模块 SVG 插画)|
| `Badge` | 通用红点 / 数字角标 | 数字 / 圆点 |
| `Sheet` | 全局 Sheet 基座 | 滑入 / 确认 / 取消 |

### 4.2 日报专属(统一 `DailyXxx` 前缀,11 个)

| 组件 | 用途 | 关键状态 |
|---|---|---|
| `DailyMoodSelector` | 心情选择(1-5 半星,半星可选)| **默认 3 星**(US-DR-01 AC-2)/ 1 / 1.5 / 2 / ... / 5;可一键改 |
| `DailyHighlightChips` | 亮点 tag(5 预设+自定义)| 未选 / 已选 N / 选满 3 余置灰 / 自定义输入 |
| `DailyWeatherChip` | 天气选择 | 未选 / 已选(可改)/ Sheet 唤起(7 种 emoji)|
| `DailyMarkdownEditor` | Markdown 编辑器 | 编辑 Tab / 预览 Tab / 工具栏启用·禁用 |
| `DailyAutoSaveStatusBar` | V3 底部保存状态栏 | 5 种状态(§7.1)+ 时间戳 |
| `DailyConflictBanner` | V3 顶部冲突提示 | 默认隐藏 / 展开冲突项 / 选择「保留我的」「采用服务端」|
| `DailyDraftRestoreToast` | V3 进入页草稿恢复 | 仅展示 / 保留 / 放弃 / 30s 超时自动放弃 |
| `DailyMonthlyMoodChart` | 月度心情图 | mini(时间线内)/ expanded(Drawer 全屏)|
| `DailyTimelineDayItem` | 时间线日列 | 4 种业务状态(§7.5.2)|
| `DailyAICard` | AI 摘要卡片 | 5 种状态(§7.5.1)|
| `DailySearchBarFilter` | 搜索 + 多筛选 | 输入态 / 激活筛 / 命中数 |

### 4.3 全局触发器(日报模块持有,调用 G2)

| 组件 | 用途 | 调用方式 |
|---|---|---|
| `DailyExportTrigger` | 日报模块内的导出入口按钮 | 唤起 `GlobalExportSheet`(G2);传入 `{ type:'daily', scope:'single'\|'month', date\|month }` |

### 4.4 命名空间规则

- **全局共享**:`Xxx`(无前缀)
- **日报专属**:`DailyXxx`
- **任务模块**:`TaskXxx`(沿用 01-task-ui 命名)

### 4.5 图标集

| 分类 | 图标 |
|---|---|
| Tab / 空状态 | 🏠📋🎯📝👤😊😐🙁🔥⭐🛏📖🧘 |
| 操作 | 🔍📤✏🗑✓⟲⚠🌙 |
| **心情(★)** | 😞(1) 🙁(2) 😐(3) 🙂(4) 😄(5) |
| **天气(★)** | ☀晴 ⛅多云 ☁阴 🌧雨 ⛈雷雨 🌨雪 🌫雾 |
| **状态(★)** | ✏草稿 ⚠冲突 ⟲重试 🤖AI |

---

## 5. 数据模型 + Mock 数据

### 5.1 实体字段

```js
// DailyReport —— 主存储实体
{
  id: "dr_a1b2c3d4-e5f6-7890-abcd-ef0123456789",  // 全局唯一 UUID
  userId: "u_001",
  reportDate: "2026-07-26",         // 自然日(用户时区);**唯一约束 (userId, reportDate)**
  mood: 4,                          // 1-5 整数或半星(0.5 粒度);null=未选
  highlights: [                     // 结构化对象数组
    { name: "工作", isPreset: true },
    { name: "学习", isPreset: true },
    { name: "复盘 Q3 OKR", isPreset: false }   // 自定义=!isPreset
  ],                                 // ≤3 条,每条 name ≤20 字
  weather: "☁ 多云",                 // 7 选 1;null=未选
  contentMd: "## 今日\n- 完成...",  // Markdown 正文(可空)
  contentText: "今日完成调研...",    // 派生:纯文本索引
  aiSummary: {                      // 派生·可缓存;不入永久依赖
    text: "用户今天主要...",
    generatedAt: "2026-07-26T22:30:00+08:00",
    modelVersion: "ollama-llama3-8b",
    isUserEdited: false             // 用户编辑过则不再自动覆盖
    // isStale 从 updatedAt > aiSummary.generatedAt 派生(不存储)
  },
  wordCount: 142,                   // 派生
  createdAt: "2026-07-26T08:12:00+08:00",
  updatedAt: "2026-07-26T22:30:00+08:00",
  deletedAt: null,                  // 软删除;走 G1 30 天
  version: 3                        // 自动保存版本号;冲突检测
}

// Draft —— 客户端 localStorage 模拟
{
  draftKey: "dr_draft_u_001_2026-07-26",
  reportDate: "2026-07-26",
  mood: 4,
  highlights: [
    { name: "工作", isPreset: true },
    { name: "学习", isPreset: true }
  ],
  weather: "☁ 多云",
  contentMd: "## 今日\n- 完成...",
  lastSavedAt: "2026-07-26T14:23:08+08:00",
  conflictBaseVersion: 3           // 进入页时服务端版本;冲突检测依据
}

// AiSummaryStatus —— AI 摘要运行时状态
{
  reportId: "dr_<uuid>",
  status: "idle" | "generating" | "success" | "failed" | "unavailable",
  startedAt: null | ISODate,
  finishedAt: null | ISODate,
  error: null | "timeout" | "ollama_down" | "validation_failed"
}

// SearchFilter —— URL 可序列化
{
  q: "深度工作",
  // **检索范围(US-DR-04 AC-1)**:title + contentMd + highlights.name 三字段
  // 模糊匹配(原型用 substring;真实实现用 PostgreSQL ILIKE 或全文索引)
  dateFrom: "2026-07-01",          // 含;null=不限
  dateTo: "2026-07-31",             // 含;null=不限
  moodMin: 4,                       // 含;null=不限;mood >= moodMin
  moodMax: 5,                       // 含;null=不限;mood <= moodMax
  highlights: ["工作", "学习"]      // tag 命中其一
}
```

**SearchFilter.moodMin/moodMax 语义**:

| moodMin | moodMax | 过滤结果 |
|---|---|---|
| null | null | 全部 |
| 4 | null | 心情 **≥ 4** |
| null | 5 | 心情 **≤ 5** |
| 4 | 5 | 心情 **∈ [4, 5]** |

实现:`dailyReport.mood >= moodMin AND dailyReport.mood <= moodMax`,任一为 null 时跳过对应比较。

### 5.2 业务状态判定函数(`isDraft` / `isSaveFailed` / `isConflict` **正交**)

```js
// getDayItemStatus(report, draft, lastSaveResult)
// 返回枚举: 'completed' | 'draft' | 'saveFailed' | 'conflict'
function getDayItemStatus(report, draft, lastSaveResult) {
  // 优先级: conflict > saveFailed > draft > completed
  if (draft && draft.conflictBaseVersion !== report.version) {
    return 'conflict';
  }
  if (lastSaveResult === 'failed') {
    return 'saveFailed';
  }
  if (!report.contentMd || report.contentMd.trim() === '') {
    return 'draft';
  }
  return 'completed';
}
```

**判定逻辑(★ 三者正交,不嵌套)**:

| 判定函数 | 公式 | 说明 |
|---|---|---|
| `isDraft` | `contentMd == null \|\| contentMd.trim() === ''` | 仅判断内容空 |
| `isSaveFailed` | 最近一次 save 请求失败 AND 本地草稿保留 | 与草稿正交 |
| `isConflict` | `draft.conflictBaseVersion !== report.version AND 未解决` | 与草稿/保存失败正交 |

**V2 时间线日列渲染规则**:

```text
if getDayItemStatus() === 'conflict'    → 「⚠ 冲突」角标
elif getDayItemStatus() === 'saveFailed' → 「⚠ 保存失败」角标
elif getDayItemStatus() === 'draft'      → 「✏ 草稿」
else                                     → 心情 emoji + 内容缩略
```

### 5.3 id 与 highlights 规则

- `DailyReport.id` = `dr_<uuid>`(全局唯一 UUID)
- 业务唯一性由 `(userId, reportDate)` 保证
- `highlights` 元素 = `{ name, isPreset }`:仅 2 个字段;`isPreset: false` 即「自定义」
- 5 预设:`工作` `学习` `生活` `运动` `社交`

### 5.4 Mock 数据量

| 实体 | 数量 | 覆盖场景 |
|---|---|---|
| `DailyReport` | **31 条**(2026-07 全月) | 每日一条;心情 1-5 全分布;含 3 未写日;AI 摘要 3 条;AI 不可用 1 天;自定义亮点 1 条;1 条软删除 |
| `highlights` 分布 | 5 预设全覆盖 + 1 自定义(7/22)| 5 预设 + 复盘 Q3 OKR |
| `aiSummary` | **3 条** | 7/1(未编辑)/ 7/7(已编辑)/ 7/14(完整极好日 + 未编辑)|
| AI 不可用日 | **1 天** | 7/13 |
| `Draft` | 1 条 localStorage | V3 DraftRestoreToast 演示 |

### 5.5 Mock 每日数据点

| 日 | 心情 | 亮点 | 天气 | AI 摘要 | 演示要点 |
|---|---|---|---|---|---|
| 7/1 | 4 | 工作·学习 | ☀晴 | 有(用户未编辑)| 已完成 + AI 成功 |
| 7/2 | 5 | 工作 | ☀晴 | — | 已完成无摘要 |
| 7/3 | ☐ | — | — | — | **未写日** |
| 7/4 | 3 | 生活·社交 | ⛅多云 | — | — |
| 7/5 | 2 | 工作 | 🌧雨 | — | 已完成心情偏低 |
| 7/6 | ☐ | — | — | — | **未写日** |
| 7/7 | 5 | 学习·运动 | ☀晴 | 有(用户已编辑)| AI 摘要"已编辑"渲染 |
| 7/8 | 4 | 工作·社交 | ⛅多云 | — | — |
| 7/9 | 4 | 工作·学习 | ☀晴 | — | — |
| 7/10 | ☐ | — | — | — | **未写日** |
| 7/11 | 3 | 工作 | ☁阴 | — | — |
| 7/12 | 2 | 生活 | 🌧雨 | — | — |
| 7/13 | 4 | 学习·运动 | ☀晴 | **不可用** | AI 不可用演示 |
| 7/14 | 5 | 工作·学习·社交 | ☀晴 | 有(完整未编辑)| 极好日 + 满 3 tag |
| 7/15-7/21 | 5-3 分布 | 多样 | 多种 | — | 覆盖完整心情曲线 |
| 7/22 | 4 | 运动 + 复盘 Q3 OKR(2 预设 + 1 自定义)| ☁阴 | — | **自定义亮点 demo** |
| 7/23-7/25 | 5-3 | 多样 | 多种 | — | — |
| 7/26 | 4 | 工作·生活 | ⛅多云 | — | **今日**;DraftRestore 入口 |
| 7/27 | ☐ | — | — | — | **未来日期(不可点击 / 灰态禁用)** |
| 7/28-7/30 | — | — | — | — | 未来日期(不可点击 / 灰态禁用)|
| 7/31 | — | — | — | — | 月末未来日(不可点击 / 灰态禁用)|

### 5.6 不模拟的部分

- 真实 Ollama 调用(原型用 1.5s setTimeout + 随机失败模拟)
- 服务端 last-write-wins 检测(原型用手动按钮「强制冲突」触发)
- 全文搜索(原型用 substring 匹配 + 高亮)
- 真实图片/语音(不在 MVP)

### 5.7 状态触发器(原型演示用,隐藏面板,仅 prototype 模式)

| 触发器 | 选项 / 行为 | 期望 UI 表现 |
|---|---|---|
| 「模拟 Ollama 健康」toggle | `available` / `unavailable` | unavailable 时 V4 摘要按钮置灰 + tooltip |
| **「强制冲突」button** | 当前 V3 `draft.conflictBaseVersion = N` → 点击后 Mock 服务端 `version` 改为 `N+1` → **下次自动保存请求返回 409** → V3 顶部 ConflictBanner 展开 | 演示用 |
| 「快进日期」select | 场景 1: `7/29`(临近月末 + 已写)| V1 卡片"今日已写";心情 5 |
| | 场景 2: `7/31`(月末未写)| V1 卡片"今日未写"(红点)|
| | 场景 3: `7/30`(未来最后边界)| V2 时间线 7/30 列"未来日期"灰态禁用 |
| 「清空草稿」button | — | V3 重新进入无 DraftRestoreToast |
| 「清空全部数据」button | — | 重置所有 Mock |

> **强调**:开关面板**是纯原型辅助**,绝不带入正式前端代码。

### 5.8 派生关系速查表

| 派生项 | 来源 | 用途 |
|---|---|---|
| `isStale` | `(report.updatedAt > aiSummary.generatedAt) && (aiSummary.isUserEdited === false)` | 摘要"⚠ 内容已更新" |
| `todayMood` | `report.mood` | V1 卡片 |
| `monthlyMoodAvg` | 月内 mood 平均 | V2 顶部 |
| `wordCount` | `contentMd` 字数 | V3 字数统计 |
| `getDayItemStatus(report, draft, lastSaveResult)` | 上述三状态优先级合并 | V2 日列枚举 `completed`/`draft`/`saveFailed`/`conflict` |

---

## 6. 8 条完整交互流

| # | 流程 | 路径 | 关键反馈 |
|---|---|---|---|
| **F1** | Dashboard → 写今日日报 → 自动保存 | V1 → V3 | debounce 500-800ms;**最迟 5s 自动保存一次**;离开页 flush(§7.2.2);非空+成功 → 红点消失 |
| **F2** | 日报 Tab → 月度时间线 → 切月 | Tab 📝 → V2 | 心情图 + 列表双区;月份切换独立加载 |
| **F3** | 时间线 → 点击某天 | V2 → V4(已写)/ V3(今日未写)/ V4"暂无"(历史未写)/ 灰禁用(未来)| 见 §1.4 F3 行为区分 |
| **F4** | 写日报 → 心情/亮点/天气 | V3 | 心情 1-5 半星;5 预设 + 自定义 ≤3;天气 Sheet |
| **F5** | 详情 → AI 摘要(显式触发) | V4 | 不为自动保存调用 AI;**isUserEdited=true 时再生成需确认** |
| **F6** | AI 摘要 5 状态 | V4 摘要卡 | §7.5 |
| **F7** | 搜索 → 多筛选 → 高亮 → 详情 | V2 顶部 V5 Overlay → V4 | 全文+日期+心情+tag;URL 与 V2 同源 |
| **F8** | 导出 | V2/V4 → `DailyExportTrigger` → G2 `GlobalExportSheet` | 单条 .md / 月度 .zip |

---

## 7. 关键状态机

### 7.1 V3 写日报 · 自动保存 5 状态

```
(用户编辑)「编辑中…」
   │ 输入停止 500-800ms / 最迟 5s 触发 / 离开页 flush
   ▼
(正在保存)「正在保存…」+ 旋转图标
   │
   ├── 成功 ─────► (草稿已保存 ✓)「草稿已保存 ✓ 14:23:08」
   │                  │ 内容非空
   │                  ▼
   │              (已完成)→ 红点消失
   │
   ├── 失败 ─────► (保存失败)红底「保存失败 ⟲ 重试」
   │                  │ 同时记 lastSaveResult='failed'
   │                  ▼
   │              V2 日列 getDayItemStatus() = 'saveFailed'
   │
   └── 409 Conflict ─► (冲突) 见 §7.4
```

### 7.2 V3 写日报 · debounce + 5s 兜底 + 离开页 flush

#### 7.2.1 触发时机

| 情形 | 行为 |
|---|---|
| 输入后立即停 500-800ms | **debounce 触发一次保存** |
| 连续输入不停 | debounce 被重置 → **满 5s 强制 flush** |
| 字段快速切换 | 切换结束 500-800ms 触发 |
| **离开页 flush** | 见下 |

#### 7.2.2 离开页 flush 三分支

```text
点返回/顶部 X
  │
  ├── save 成功 ──► 删 draft,清 conflictBaseVersion,正常离开
  │
  ├── save 失败 ──► 保留 draft,banner「有未同步修改」
  │
  └── save 409 ──► 阻塞离开
                    Dialog「存在冲突,请先解决后再离开」
                    [留在页面]    — 关弹窗,ConflictBanner 持续,编辑仍阻塞
                    [查看冲突]    — 跳 §7.4 解决流程
```

### 7.3 V3 写日报 · 草稿恢复

```
进入 V3
  │
  ├── localStorage 无 draft for this date ──► 正常进入
  │
  └── 找到 draft for this date ─────► DraftRestoreToast
        │
        ├─ [保留] → 用 draft 覆盖表单
        ├─ [放弃] → 删除 localStorage draft,空白表单进入
        └─ 30s 超时:从 Toast 出现瞬间起 30s 内无操作 → 自动 = 放弃
```

### 7.4 V3 写日报 · 冲突解决(3 个主选项)

```
自动保存请求 → 服务端 409 Conflict
  │
  ▼
ConflictBanner 展开(阻塞编辑)
  │
  ├── [强制保存我的版本]  本地 version + 1,重试 save
  │                        ├─ 成功 ──► 草稿已保存 ✓,banner 消失
  │                        └─ 仍 409 ─► banner 持续,允许无限重试
  │
  ├── [查看差异 / 合并]     打开版本对比 Drawer
  │                          左列本地 / 右列服务端 / 每段「采用此段」
  │                          [合并完成] → 草稿已保存 ✓
  │                                        (次按钮 [重新检测] → 再发 save 确认)
  │
  └── [放弃本地 / 采用服务端]  report 内容覆盖本地表单
                                删除 localStorage draft
                                banner 消失,无需 save
```

> 「强制关闭」从主流程移除——它是合并 Drawer 内的「暂不处理 / 关闭」次按钮。

### 7.5 V4 详情 · AI 摘要(严格 5 状态 + 业务子条件)

#### 7.5.1 5 状态机

```
(未生成) A
   │ 用户点「🤖 生成 AI 复盘」
   │ ★ 乐观执行 — 不再二次阻塞检查
   ▼
(生成中) B ★ 启动 --ai-timeout-ms(30s)超时计时
   │
   ├── 30s 内 Ollama 返回 ─► C(成功)/ D(失败)按实际结果
   │
   └── 30s 超时 ─────────► D. 生成失败(对齐 PRD P95 ≤ 30s)
```

**严格 5 状态(与 PRD §1.8 一一对应)**:

| 状态 | 显示 | 来源 |
|---|---|---|
| **A. 未生成** | 卡片「🤖 生成 AI 复盘」按钮 | 进入时 `/ai/health` 200 |
| **B. 生成中** | 骨架 +「AI 正在生成摘要…」+ 已等待时间 | 点击后乐观进入 |
| **C. 生成成功** | 200 字摘要 +「**编辑 / 删除 / 重新生成**」(US-DR-03 AC-2)| Ollama 返回 |
| **D. 生成失败** | 红卡「生成失败 ⟲ 重试」 | 超时 / 错误 |
| **U. AI 不可用** | 按钮置灰 + tooltip「AI 服务暂不可用」 | 进入时 `/ai/health` 失败 |

> A→U 是**一次性转换**(进入时根据 `/ai/health` 失败直接到 U),不进 5 状态表。

**健康检查时机**(诊断 ≠ 控制流):

| 时机 | 行为 |
|---|---|
| V4 进入时 | 同步检查 `/ai/health` → 失败 → U(置灰)|
| 用户点「生成」后 | **后台 fire-and-forget 异步再检查一次(仅写日志,不阻塞按钮,不进 U)** |

#### 7.5.2 C 子条件(`isUserEdited` × 派生 `isStale` 互斥)

| isUserEdited | 派生 isStale | UI 显示 |
|---|---|---|
| false | **true**(改正文未改 AI 摘要) | 「⚠ 内容已更新 → 重新生成」 |
| **true** | **false(失效)** | 「✏ 已编辑」 |
| false | false | 正常卡片 |

```js
// isStale 与 isUserEdited 互斥:用户编辑过 AI 摘要后,isStale 失效(视为已手动同步)
const isStale = (report.updatedAt > aiSummary.generatedAt)
              && (aiSummary.isUserEdited === false);
```

**重生成二次确认**:

| 场景 | 行为 |
|---|---|
| isUserEdited = false | 直接调用 AI |
| isUserEdited = true | Dialog「重新生成会覆盖你编辑过的内容,确定吗?」+ [取消] [确定重新生成] |

#### 7.5.3 B → D 超时 — token 化

```js
const AI_TIMEOUT_MS = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--ai-timeout-ms')) || 30000;
setTimeout(() => transitionToFailed(), AI_TIMEOUT_MS);
// CSS 层同步
:root { --ai-timeout-ms: 30000; }
```

> **`--ai-timeout-ms` 来源**:见 §3.2 token 表。所有 AI 摘要相关时间统一读 token,不允许硬编码。

### 7.6 V5 搜索 Overlay · V2 顶部下推(非独立页面)

#### 7.6.1 URL 格式(与 V2 同源)

```text
/daily/timeline?search=深度工作
                &dateFrom=2026-07-01
                &dateTo=2026-07-31
                &moodMin=4
                &moodMax=5
                &highlights=工作,学习
```

- 基础路由 = V2 路由 `/daily/timeline`
- Query 完全对应 `SearchFilter` 结构(§5.1)
- 进入 V2 时若有 query → 自动展开 Overlay
- 关闭 Overlay → URL query 保留,V2 列表按筛选过滤

#### 7.6.2 Overlay 交互流

```
V2 顶部 🔍 点开
   │
   ▼
SearchOverlay 从顶部下推,占 V2 上 50% 屏
URL:router.push('/daily/timeline?search=...&moodMin=4...')
下方 V2 列表仍可见(下半屏)
   │
   ├── 用户改筛选 ──► URL 更新;Overlay 命中数实时反应
   │
   ├── 命中 ──► V2 列表就地刷新(按筛选过滤)
   │
   ├── 0 结果 ──► Overlay 内"换个关键词"
   │
   └── 关闭 Overlay ──► 顶部 ✕ / 点击空白 / 按 Esc / 系统返回键
                       URL 保留 query,V2 列表按 URL 状态显示

点击命中 ──► V4 详情;返回自动回到 V2 当前月份
```

#### 7.6.3 系统返回键拦截 + 栈清理

```text
V2 进入时(无 query):
  入栈 [..., V2]
  │
  ├── 点 🔍 开 Overlay:
  │     1. 清理已存在的 Overlay-guard 项(无累积)
  │     2. pushState 压入虚拟历史项 [..., V2, Overlay-guard]
  │     3. URL 加 query ─ 但 history 保留 V2 占位
  │     4. 系统返回 ─► 回退 Overlay-guard,仅关闭 Overlay;
  │                   URL query 保留,栈恢复 [..., V2]
  │
  └── Overlay 关闭:
        栈回到 V2 入口状态;多次开关不累积虚拟项
```

> **明确规则**:每次开 Overlay 前清理已存在的 Overlay-guard,确保栈只在 Overlay 打开期间多一项,关闭后栈净。

**lg 桌面端行为**:Overlay 覆盖 V2 列表区(左半屏),V4 抽屉保持可见不被遮挡;关闭 Overlay 时 V4 抽屉焦点保持不变。

---

## 8. 边缘 / 错误状态清单

| 场景 | UI 表现 | 来源 |
|---|---|---|
| 今日未写 | V1「📝 今日未写」+「写今日日报」CTA;📝 Tab 红点 | PRD / §1.3 |
| 时间线当月 0 篇 | "本月暂无日报" +「写今日日报」CTA | §1.5 |
| 历史月份无记录 | "该月暂无日报" | §1.5 |
| 历史未写日 | V4 显示"暂无日报",**不提供补写 CTA** | §1.4 F3 |
| 未来日期 | 灰态禁用,不可点击,`aria-disabled=true` | §1.4 F3 / §3.5 |
| 编辑中 | DailyAutoSaveStatusBar:「编辑中…」 | §7.1 |
| 正在保存 | 「正在保存…」+ 旋转图标 | §7.1 |
| 草稿已保存 | 「草稿已保存 ✓ 14:23:08」;**红点消失**(内容非空)| §7.1 |
| 保存失败 | 红底「保存失败 ⟲ 重试」;V2 日列 'saveFailed' | §7.1 / §5.2 |
| 冲突 | ConflictBanner + 阻塞编辑;离开页阻塞 | §7.1 / §7.4 |
| AI 未生成 | 卡片显示「🤖 生成 AI 复盘」按钮 | §7.5.1 |
| AI 生成中 | 骨架 +「AI 正在生成摘要…」+ 已等待时间 | §7.5.1 |
| AI 生成失败 | 红卡「生成失败 ⟲ 重试」 | §7.5.1 |
| AI 不可用 | 按钮置灰 + tooltip「AI 服务暂不可用」 | §7.5.1 |
| AI 已生成(未编辑)| 卡片显示 +「编辑 / 重新生成」 | §7.5.1 |
| AI 已编辑 | 摘要卡上「✏ 已编辑」角标;**互斥使 isStale 失效** | §7.5.2 |
| AI 摘要 isStale(派生)| 摘要顶部「⚠ 内容已更新 → 重新生成」 | §7.5.2 |
| 草稿恢复(进入 V3)| DraftRestoreToast「已恢复上次草稿」+ [保留] [放弃] / **30s 超时 = 放弃** | §7.3 |
| 搜索无结果 | "换个关键词" | PRD US-DR-04 AC-3 |
| 搜索失败 | toast「搜索失败 ⟲ 重试」 | §1.5 |
| **Markdown 解析失败** | **toast「内容解析失败,已降级纯文本」+ 详情降级为 `<pre>`** | §8.1 |
| **检测到可疑脚本** | **静默剥离 + toast「已过滤不安全内容」** | §8.1 |
| 删除日报 | 弹窗确认 → 软删除 → V4 跳 V2 → V2 该日「🗑 已删除」灰态 + toast「日报已删除,30 天内可在回收站恢复」 | PRD DR-004 |
| 网络离线 | banner「⚠ 离线 · 草稿已本地保存」+ 状态栏「正在保存…」+ 每 10s 探测恢复 | §7.1 |
| 时间穿越 | 不处理;依赖服务端时钟 → 客户端相对时长(已等待 / debounce)短暂异常;服务端绝对时间戳逻辑(createdAt / updatedAt / generatedAt)不受影响 | PRD 风险 #5 |

### 8.1 Markdown 渲染 · XSS 防护(★ 解析失败 vs 可疑脚本两路)

```js
function renderMarkdown(md) {
  try {
    const html = marked.parse(md, { breaks: true, headerIds: false, mangle: false });
    const cleanHtml = DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['p','h1','h2','h3','ul','ol','li','strong','em','code','pre','blockquote','a','br','hr'],
      ALLOWED_ATTR: ['href','title'],
      ALLOW_DATA_ATTR: false,
      FORBID_TAGS: ['script','iframe','style','object','embed','form','input','button']
    });
    // 基于标签数对比(stripped 检测)
    function countTags(s) {
      return (s.match(/<\/?[a-z][^>]*>/gi) || []).length;
    }
    const stripped = countTags(html) > countTags(cleanHtml);
    return { ok: true, html: cleanHtml, stripped };
  } catch (e) {
    // 仅在 parse 异常时降级
    return { ok: false, html: `<pre>${escape(md)}</pre>`, error: e.message };
  }
}
```

**两种失败路径**:

| 失败类型 | 触发 | 行为 |
|---|---|---|
| 解析失败(parse error)| marked 抛出 | toast「内容解析失败,已降级纯文本」,展示 `<pre>` 纯文本 |
| 检测到可疑脚本(XSS)| `countTags(html) > countTags(clean)` | 静默渲染干净 HTML,**toast「已过滤不安全内容」**(无错误)|

### 8.2 跨模块数据流(轻描)

| 出 | 入 | 触发 |
|---|---|---|
| 任务模块「今日完成任务」 | V3 写日报页「快速插入今日成就」chip | **未列入 MVP**;仅在数据流章节留位 |

---

## 9. 响应式策略

### 9.1 断点

| 断点 | 宽度 | 设备 | 日报模块布局 |
|---|---|---|---|
| **sm** | ≤ 640px | 手机(主要目标) | 单列;底部 Tab Bar 5 项可见 |
| **md** | 641–1024px | 平板/小桌面 | V2 时间线居中(max 720);V4 单列;Overlay 占屏 50% |
| **lg** | ≥ 1025px | 桌面 | V2 时间线 + V4 详情左右分栏(详情可折叠抽屉) |

### 9.2 关键页面响应式

| 页面 | sm(移动) | md(平板) | lg(桌面) |
|---|---|---|---|
| **V1 Dashboard** | 单列纵向滚 | 同上 | 单列,最大 720 居中 |
| **V2 时间线(月)** | 单列;月份切换器顶部 | 单列,最大 720 居中 | 左列表 + **右详情抽屉**,Esc 关闭 |
| **V2 心情图(mini)** | mini | mini | mini + hover 日期 |
| **V2 月度心情图全屏** | **底部 Sheet 滑入占屏 80%** | **底部 Sheet 滑入占屏 80%** | **居中 modal(720 × 540,水平垂直居中)** |
| **V3 写日报** | 全屏滑入 | 全屏,max 720 居中 | 同 md |
| **V4 详情** | 单列 | 单列居中 | 左 V2 + 右 V4 抽屉(默认开,可关)|
| **V4 AI 摘要卡** | 内联 | 同上 | 同上 |
| **V5 搜索 Overlay** | 占 V2 上 50% 屏 | 同 sm | **覆盖 V2 列表区(左半屏),V4 抽屉不被遮挡;关闭 Overlay 时 V4 抽屉焦点保持不变** |
| **Sheet(Weather / Export)** | 自底滑入占屏 80% | 居中 600 宽 | 居中 560 宽 |
| **Dialog(冲突 / 重生成 / 删除)** | 自底全屏 | 居中 480 宽 | 居中 480 宽 |

### 9.3 5 项 Tab Bar 在 sm 下的处理

- 5 项 Tab 等距分布;图标 24px,文字 11px
- 选中态用 `--brand`;未选用 `--text-2`
- 📝 Tab 红点 = 4px 圆点,右上角定位,不挡图标
- Tab 顺序固定(§1.2),不提供隐藏/折叠入口

### 9.4 lg 桌面端特别说明

- **当前 PRD 用户画像全为移动场景**(白领、程序员、教师),桌面端仅做**「日间可读」** —— 无大屏专属布局
- V2 + V4 同屏分栏仅在 lg 自动启用,**不提供手动切换**
- 桌面端无 V1 三栏 Dashboard(任务模块有,但日报不引入)

---

## 10. 可访问性(WCAG 2.1 AA)

### 10.1 通用规则

| 项 | 规则 |
|---|---|
| 颜色对比度(文字)| 文字 vs 背景 ≥4.5:1;大文字 ≥3:1 | 全部文字用 `--text-1/2/3` 或 `--brand`,**已达 AA** |
| 焦点环 | 键盘聚焦时 2px `--brand` 描边 + 2px offset |
| 触摸目标 | ≥ 44×44px | 星标 ≥44px 间距;chip ≥36 高 + 边距 |
| 语义化 | `<button><header><nav><ul><article>` | V2 日列 `<article>`,V3 `<form>` |
| ARIA | Sheet `role=dialog` `aria-modal=true` `aria-label`;Toast `aria-live=polite`;Banner `role=alert aria-live=assertive` |
| 屏幕阅读器 | 状态变更用 `aria-live` 播报 |
| 键盘导航 | Tab 顺序合理;Sheet 打开焦点锁定;Esc 关闭;系统返回见 §7.6.3 |
| 未来日期 | 不可点击 = 不可聚焦 + `aria-disabled=true` |
| 红绿色盲 | 保存失败不只靠红色 ⚠ + 文案双信号 |

### 10.2 心情 token 用途明确(★ 与 §3.2 对齐)

**`--mood-1...--mood-5` 全部仅作图形装饰,不承载文字内容**。emoji 优先作为视觉信号,颜色仅用于边框 / 背景 / 折线图。

允许 `--mood-3: #A8A29E` / `--mood-2: #F97316` 等保留(在白底上对比度 < 4.5:1),因不直接承载文字;状态色 `--success / --danger / --warning` 始终满足 AA。

### 10.3 屏幕阅读器关键脚本

| 触发 | aria-live | 文本 |
|---|---|---|
| 进入 V3 | — | "今日日报,2026 年 7 月 26 日" |
| 进入 V3 检测到草稿 | **assertive** | "检测到上次未提交的草稿;选择保留继续编辑,或选择放弃清空;30 秒后将自动放弃" |
| 心情变更 | polite | "心情已改为 4 星,不错" |
| 自动保存中 | — | (工具栏内容更新) |
| 保存成功 | polite | "草稿已保存,14 点 23 分 08 秒" |
| 保存失败 | assertive | "保存失败,请重试;草稿已在本地保留" |
| 冲突 | assertive | "存在版本冲突,请选择保留我的版本,或查看差异合并,或采用服务端版本" |
| 冲突解决完成 | polite | "冲突已解决,内容已保存" |
| AI 摘要生成中 | polite | "AI 摘要正在生成,已等待 X 秒;最长约 30 秒" |
| AI 生成成功 | polite | "AI 摘要已生成,共 200 字" |
| AI 失败 | assertive | "AI 摘要生成失败,请重试" |
| AI 不可用 | assertive | "AI 服务暂不可用,无法生成摘要" |
| 删除日报确认 | assertive | "日报已删除,30 天内可在回收站恢复" |

---

## 11. 交付物

```
docs/lifewise/designs/
  ├─ 01-task-ui/
  │    ├─ 2026-07-26-task-ui-design.md
  │    └─ 01-task-ui-v2.html
  └─ 02-daily-ui/                                     ← 新建(对齐命名风格)
       ├─ 02-daily-ui-design.md                       ← 设计文档
       └─ 02-daily-ui-v1.html                         ← HTML 原型
```

| 产物 | 形式 | 大小目标 |
|---|---|---|
| 设计 Markdown | 12 章 + 附录 | 类 01-task-ui ~470 行 |
| HTML 原型 | 单文件,内联 CSS+JS,无外部资源依赖;marked.js / DOMPurify 可走 CDN | 90-150 KB |
| Mock 数据 | 原型内嵌 | 31 条 DailyReport + 1 草稿 |
| 状态触发器 | 原型右上角隐藏面板 | 仅 prototype 模式 |

---

## 12. 自检清单(交付前必过)

| 类别 | 检查项 | 通过条件 |
|---|---|---|
| **覆盖度** | 5 个核心视图全部可访问 | 每个页面有入口可点击到达 |
| | 8 条交互流全部可演示 | 每次流程能从入口走到结束 |
| | 内嵌状态全部可触发 | 演示面板 7 项触发器 + 主动触发 |
| | 边缘状态全部展示 | 见 §8 21 行每条都有演示路径 |
| **布局** | 移动端 375px 正确 | 无横向滚动;5 项 Tab 完整 |
| | 平板 768px 正确 | 自适应,V2/V4 单列居中 |
| | 桌面端 1280px 正确 | V2+V4 分栏可演示 |
| **设计系统** | Token 全部生效 | 所有颜色/字号/圆角/间距均用 CSS 变量,无硬编码 |
| | **`--ai-timeout-ms` 引入** | 原型读 token 控制 30s 超时(§3.2)|
| | 中文字体正确 | PingFang SC / HarmonyOS Sans 生效,有 fallback |
| **交互** | 自动保存完整 | debounce 500-800ms + 5s 兜底 + 离开页 flush 三分支可演示 |
| | 冲突 3 选项可演示 | 强制保存我的 / 查看差异合并 / 采用服务端 |
| | AI 重生成二次确认 | `isUserEdited=true` 时 Dialog 弹出 |
| | 健康检查 toggle | 「Ollama 健康」切换可演示 U 状态 |
| | Overlay 栈清理 | 多次开/关 Overlay 不累积(§7.6.3)|
| | 快进日期 3 场景 | 7/29 / 7/31 / 7/30 全部可演示 |
| **文案** | 无占位符 | 无 Lorem ipsum、无 TODO |
| | 中文文案完整 | 按钮、提示、错误信息全部中文 |
| | 心情 emoji 映射正确 | 😞(1)→🙁(2)→😐(3)→🙂(4)→😄(5) |
| **可访问性** | WCAG 2.1 AA 关键项通过 | 文字对比度、焦点环、触摸目标、SR 朗读(§10.3)|
| **性能(软目标)** | 移动端首屏 Lighthouse > 90 | Performance / A11y / BP / SEO 各 ≥ 90 |
| | **写日报 P95 ≤ 1.2s**(PRD §7 KPI)| **debounce 触发 → 本地 mock 返回 → UI「草稿已保存 ✓」端到端延迟**;原型 1.5s setTimeout 模拟,实测 ≈ 2.3s(mock);真实网络需另埋点 |
| | AI 摘要 P95 ≤ 30s | `--ai-timeout-ms` + 倒计时显式 |
| | **空状态插画** | **复用任务模块 EmptyState(任务模块已建 SVG);日报模块不新增 SVG** |

---

## 附录 A · 页面截图清单(原型的关键截图)

> 在浏览器打开 `02-daily-ui-v1.html` 后,可在以下路径截屏:

1. 移动端 V1 Dashboard(含📝快入口卡)
2. 移动端 V2 时间线(月)+ 双区(心情图 + 列表)
3. 移动端 V2 月度心情图全屏 Drawer
4. 移动端 V3 写日报页(含自动保存状态栏 5 状态、心情选择、亮点 chips、天气 Sheet、草稿恢复 Toast)
5. 移动端 V4 日报详情页(AI 摘要 5 状态切换)
6. 移动端 V5 Search Overlay(日期 + 心情 + tag 多筛)
7. 移动端 DailyConflictBanner
8. 移动端 DailyExportTrigger Sheet
9. 移动端未来日期灰态禁用 + 历史未写日"暂无日报"
10. 桌面端 V2 + V4 左右分栏 + Overlay 覆盖左半屏

---

## 附录 B · PRD 需求 → 设计 映射

| PRD 需求 | 视图 / 状态 |
|---|---|
| DR-001 创建当日日报 | V1 → V3 流程(F1)|
| DR-002 编辑 | V4 顶部「✏ 编辑」→ V3 预填(F6)|
| DR-003 查看详情(Markdown 渲染)| V4 渲染区(§8.1)|
| DR-004 软删除 30 天 | V4 → 确认 Dialog → 走 G1(§8)|
| DR-005 心情 1-5 半星 | `DailyMoodSelector`(§4.2)|
| DR-006 亮点 5 预设 + 自定义 ≤3 | `DailyHighlightChips`(§4.2)|
| DR-007 天气可选 | `DailyWeatherChip` + 7 种 emoji |
| DR-008 Markdown 编辑器 | `DailyMarkdownEditor` + 预览 Tab |
| DR-010 按月聚合时间线 | V2 双区 |
| DR-011 含 AI 摘要区 | V4 摘要卡(5 状态)|
| DR-012 月度心情折线图 | V2 顶部 mini + 全屏 Drawer |
| **DR-013 关键词云** | **待确认(暂不实现)**;V2 顶部 mini 心情图与列表之间**预留 56px 高度折叠区**;MVP 阶段该区域显示空白占位 |
| DR-020 日期范围筛选 | V5 Overlay(`dateFrom / dateTo`)|
| DR-021 心情区间筛选 | V5 Overlay(`moodMin / moodMax`)|
| DR-022 亮点 tag 筛选 | V5 Overlay(`highlights`)|
| DR-023 全文搜索 | V5 Overlay(`q`)|
| DR-030 单条 .md 导出 | `DailyExportTrigger` → G2 → 单条 .md |
| DR-031 月度 .zip 导出 | `DailyExportTrigger` → G2 → 月度 .zip |

---

## 附录 C · 待确认项

| 项 | 处理 |
|---|---|
| 关键词云(DR-013)| PRD 列在 MVP 范围但 RICE 0.21 + KANO "魅力型 COULD" 与"基本型 MUST"冲突。**V2 顶部预留 56px 折叠区**,暂不实现具体 UI |
| 写未写的历史日期 | **MVP 不支持**(避免引入历史补写) |
| `--mood-3: #A8A29E` | §3.2 + §10.2 明确为「图形装饰 token,不承载文字」 |
| PRD §6 AI 摘要映射 | 见 06-ai-analysis.md;采纳率指标(AI 摘要已被编辑/未编辑比)在 PRD §1.8 是 O1 KR1.2。本设计 §7.5 仅展示编辑/未编辑两态,采纳率统计不在原型内实现 |

---

*文档版本:v1.0*
*下一步:提交 git → 用户审稿 → 移交 writing-plans 生成实施计划*
