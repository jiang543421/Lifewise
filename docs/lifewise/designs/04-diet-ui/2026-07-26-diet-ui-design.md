# 饮食记录模块 UI 原型设计

> **模块代号**：`meal`
> **设计日期**：2026-07-26（修订日：2026-07-27）
> **文档版本**：v1.0
> **状态**：Approved Design
> **关联 PRD**：`docs/lifewise/specs/PRD/04-diet-tracking.md`
> **关联架构**：`docs/lifewise/architecture/business-architecture.md`、`docs/lifewise/architecture/technical-architecture.md`
> **交付物**：本设计文档 + `04-diet-ui-v1.html` 单文件原型
> **对齐模块**：`01-task-ui` 设计范式（命名风格 / Token 体系 / Sheet&Dialog 基座复用）；`02-daily-ui` 设计范式（Tab Bar / 章节结构 / 自动保存 / Markdown 渲染 / WCAG 复用）

---

## 0. 设计目标与范围

### 0.1 目标

基于 MVP PRD（餐次 CRUD + 食物库 + 营养统计 + 推荐摄入估算 + 月度 CSV 导出），交付**单文件 HTML UI 原型**，用于：

- 设计契约：产品、设计、研发、测试的视觉与交互共识
- 用户路径演示：让 stakeholder 在浏览器走完所有核心流程
- 工程参考：为后续前端实现提供布局、组件、交互的设计基准

### 0.2 交付范围（In Scope）

- **4 段 Segmented Control 主视图**（详见 §1）
- **7 条核心交互流**（详见 §6.1）
- 移动优先 + 响应式（平板 / 桌面，详见 §8）
- **6 个全局共享组件 + 10 个 Meal 模块专属组件 + 2 个 Meal 模块触发器 + 14 个新增设计令牌**（1 streak + 4 slot + 3 nutrient + 4 segment + 2 ring，详见 §3）
- **懒人模式** mode=`quick`（PRD US-MEAL-01 AC-3）
- **MealPickerSheet 四模态**（add / detail / edit / quick）
- **派生字段语义明确**：重算优先、可选缓存、不入永久存储（详见 §5.1）
- **草稿恢复** 进入态 toast（MealDraft）
- **业务唯一性规则**：5 分钟内同 slot 视为同一 Meal 追加（详见 §5.3）
- **Mifflin-St Jeor × 活动系数** 推荐摄入估算（PRD MEAL-024）
- **G2 全局导出** 触发器（v1 仅月度 CSV；单餐 / 周聚合留 v1.1）
- 中保真 + 伪交互（本地 JS 模拟状态切换）

### 0.3 不在范围（Out of Scope）

- 后端 API（原型使用本地 JS + localStorage）
- **数据按月分区 + 超 1 年自动归档（PRD §8 风险 #5）属于后端职责，前端不模拟**
- 真实 Mifflin-St Jeor 算法的服务端精确实现（前端按简化映射展示）
- 视觉识别（拍照 / 语音 / 条形码 = v1.2+，本次占位路由 disabled）
- AI 饮食建议（v1.1+，作为 H4 段内 chip「AI 洞察」入口占位不实现）
- 家庭成员档案（v1.3+）
- 月度 PDF 报告（v1.1+）
- 与消费模块联动 / 任务模块运动打卡联动（v1.3+）
- 体重 / 睡眠（v1.x；进 H4「档案」段内 chip 占位不实现）

---

## 1. 信息架构（IA）

### 1.1 顶层结构

```
App 入口
 └─ 今日 Dashboard（默认首屏）
      ├─ 「任务」入口 → 任务模块（沿用 01-task-ui）
      ├─ 「习惯」入口 → 习惯模块（沿用 01-task-ui）
      ├─ 「日报」入口 → 日报模块（沿用 02-daily-ui）
      └─ 「🌿 健康」入口 → 饮食模块（V1-V6）
           ├─ H1 今日（/health/today）        ← 默认 Segmented 第 1 段
           ├─ H2 本周（/health/week）         ← 第 2 段
           ├─ H3 食物库（/health/foods）      ← 第 3 段
           └─ H4 档案（/health/profile）      ← 第 4 段（含 chip：基础/目标/偏好 + 未来体重/睡眠/AI 洞察/提醒）
 └─ 全局回收站（G1，任务/习惯/日报/饮食共享）
 └─ 全局导出中心（G2，通过 MealExportTrigger 触发）
 └─ 全局通知中心（G3，降级）
```

### 1.2 6 项 Tab Bar（daily-ui v1.2 配套修订）

```
┌──────────────────────────────────────────────────┐
│ 🏠   │ 📋  │ 🎯  │ 📝  │ 🌿  • │ 👤              │
│ 今日 │ 任务 │ 习惯 │ 日报 │ 健康 │ 我              │
└──────────────────────────────────────────────────┘
                              ↑
                       徽章规则（§4 触发器）
```

- **5 → 6 项**：`02-daily-ui` v1.1 中 `BottomTab5` 同步升级为 `BottomTab6`（daily-ui 文档加一条 v1.2 修订记录）
- 顺序理由：饮食是基础健康数据，排在「📝 日报」之后、「👤 我」之前；与 PRD「饮食是个人健康基础数据」对齐
- **Tab 上限冻结为 6**：未来体重 / 睡眠 / AI 洞察**不进 Tab**，必须走 🌿 健康 Tab 内部子路由（详见 §1.6）

### 1.3 健康 Tab 红点规则（与 daily-ui §1.3 范式一致）

- 「🌿 •」徽章 = 当日未记录餐次 OR 当前段超出推荐 OR 食物库新增 OR 档案未完成（任一）
- 徽章消失**唯一**条件：今日 4 段餐次均**已加 + 营养未溢出**

### 1.4 核心视图（4 段）+ Sheet 视图（4 模态 2 Sheet）

**健康 Tab Segmented 4 段（锁死，不加段不溢出）**：

| # | 段 | 路由 | 默认 | 关联 PRD |
|---|----|------|------|---------|
| **H1** | 今日 | `/health/today` | ✅ | MEAL-001 ~ MEAL-006、MEAL-020、MEAL-024 |
| **H2** | 本周 | `/health/week` | — | MEAL-021、MEAL-022 |
| **H3** | 食物库 | `/health/foods` | — | MEAL-010 ~ MEAL-013 |
| **H4** | 档案 | `/health/profile` | — | MEAL-024 |

**Sheet 视图**：

| # | 视图 | 路由 | 模态 | 归属 | 关联 PRD |
|---|------|------|------|------|---------|
| **H1.x** | MealPickerSheet | `/health/today/meal?mode=add\|detail\|edit\|quick` | 4 模态 | H1 内 | MEAL-002、MEAL-003、MEAL-004、MEAL-006、AC-3 懒人 |
| **H3.x** | FoodDetailSheet | `/health/foods/:id?mode=view\|custom` | 2 模态 | H3 内 | MEAL-011、MEAL-012 |

### 1.5 未来扩展位（H4 段内 chip）

**v1 chip 可见**（3 个）：基础 / 目标 / 偏好

**v1.x chip 占位**（4 个 disabled）：体重 / 睡眠 / AI 洞察 / 提醒

**约束（与 §1 一致）**：
- 顶部 Segmented Control 永远 4 段（今日 / 本周 / 食物库 / 档案）
- 不为体重 / 睡眠 / AI 洞察加新段
- 不留「…」溢出按钮

### 1.6 各核心视图的状态矩阵

| 视图 | 空 | 加载 | 失败 | 成功 | 备注 |
|------|----|----|------|------|------|
| **H1 今日** | ✅ 当日 0 餐 | ✅ skeleton 100ms | ✅ toast + 重试 | ✅ 4 餐分组 + 进度环 | MealProgressOverview 4 态（待配置/已用/接近/溢出） |
| **H2 本周** | ✅ 当周 0 餐 | ✅ skeleton 300ms | ✅ toast + 重试 | ✅ WeekChart + MacroDonut | MacroDonut 仅"非 quick 模式"数据入算 |
| **H3 食物库** | ✅ 无搜索结果 | ✅ skeleton 100ms | ✅ toast + 重试 | ✅ 分类筛选 + 搜索 | 自定义食物 CTA |
| **H4 档案** | ✅ 字段未填 | ✅ skeleton 100ms | ✅ toast + 重试 | ✅ 5 字段 + 估算结果 | MealProgressOverview 重算 |
| **H1.x Sheet** | ✅ mode=add 进入无草稿 | ✅ skeleton 100ms | ✅ toast + 重试 | ✅ 食物搜索 + 份量 | mode=`quick` 时跳过搜索 |
| **H3.x Sheet** | ✅ mode=custom 进入未填 | ✅ skeleton 100ms | ✅ toast + 重试 | ✅ 食物详情 + 营养 | mode=`custom` 表单校验 |

---

## 2. 页面清单（4 段 + Sheet + 全局）

| # | 视图 | 触发 | 备注 |
|---|------|------|------|
| 1 | H1 今日 | 🌿 健康 Tab（默认） | 顶部 MealProgressOverview + 4 餐分组 MealCard + MealFAB |
| 2 | H1.x MealPickerSheet | MealFAB 单击 / MealCard 已加态点击 / 长按 | 4 模态（add / detail / edit / **quick**）|
| 3 | H2 本周 | 健康 Tab → 本周 | WeekChart（7 天柱状 + 参考线）+ MacroDonut（3 色扇形 + 推荐区间）|
| 4 | H3 食物库 | 健康 Tab → 食物库 | 搜索框（拼音/中文/英文/别名） + CategoryFilter + FoodItem 列表 |
| 5 | H3.x FoodDetailSheet | H3 食物点击 / H3 搜索无结果 CTA | 2 模态（view / custom）|
| 6 | H4 档案 | 健康 Tab → 档案 | chip 切换：基础 / 目标 / 偏好；含 4 个 v1.x 占位 chip |
| 7 | MealExportTrigger | H1 / H2 顶部"📤" | 唤起 G2 GlobalExportSheet `{type:'meal', scope:'month'}`（v1 仅月度）|
| 8 | （占位）/health/camera | 健康 Tab 兜底入口 | v1 disabled + tooltip "拍照记餐 v1.2+ 上线" |
| 9 | G1 全局回收站 | 👤 我的 → 回收站 | 任务/习惯/日报/饮食四模块共享（展示饮食条目）|
| 10 | G3 全局通知中心 | 顶部铃铛 | 健康模块无需专门通知；通用降级展示 |

---

## 3. 设计 Token

### 3.1 复用（沿用 01-task-ui / 02-daily-ui）

| 类别 | Token | 取值 | 用途 |
|------|-------|-----|------|
| 颜色 | `--bg` `--surface` `--border` | `#F7F7F8` `#FFF` `#E5E7EB` | 背景、卡片、描边 |
| 文字 | `--text-1/2/3` | `#111827` `#6B7280` `#9CA3AF` | 主/次/占位 |
| 主色 | `--brand` `--brand-soft` | `#4F46E5` `#EEF2FF` | CTA、激活、链接 |
| 状态色 | `--success` `--danger` `--warning` `--ai` | `#10B981` `#EF4444` `#F59E0B` `#8B5CF6` | 完成 / 删除 / 警告 / AI |
| **连续打卡色**（★ 新增） | `--streak` | `#F59E0B`（**与 `--warning` 同色**） | 任务模块 streak 徽章；3.3 中 `--nutrient-carb` 同源引用 |
| 字号 | `display 24/600 · title 17/600 · body 15/400 · caption 13/400 · micro 11/500` | — | 5 级 |
| 间距 | `4 · 8 · 12 · 16 · 24` | px | 5 档 |
| 圆角 | `8 · 12 · 16` | px | small / medium / sheet |
| 字体栈 | `PingFang SC → HarmonyOS Sans SC → Source Han Sans CN → Microsoft YaHei → system sans` | — | 中文优先 |
| 动效 | `--motion-fast` | 150ms ease-out | SegmentedControl 切换 |

> ★ `--streak` 与 `--warning` 同色 `#F59E0B`：**复用同色避免引入新色相**，语义通过组件上下文区分（连续打卡 vs 警告）。

### 3.2 餐色 token（4 段 · **纯图形装饰**）

| Token | 值 | 用途 | Emoji 优先 |
|-------|----|------|-----------|
| `--slot-breakfast` | `#F59E0B` 暖琥珀 | 早餐卡条 / dot | 🍳 |
| `--slot-lunch` | `#10B981` 草绿 | 午餐卡条 / dot | 🥗 |
| `--slot-dinner` | `#6366F1` 蓝靛 | 晚餐卡条 / dot | 🍲 |
| `--slot-snack` | `#F472B6` 蜜桃粉 | 加餐卡条 / dot | 🍪 |

### 3.3 营养素色 token（3 色 · **纯图形装饰**）

| Token | 值 | 复用 | 用途 |
|-------|----|------|------|
| `--nutrient-protein` | `#EF4444` | = `--danger` | 蛋白质（饼图扇形 / 进度段 / 食材标签 dot） |
| `--nutrient-carb` | `#F59E0B` | = `--warning` = `--streak` | 碳水（同上） |
| `--nutrient-fat` | `#8B5CF6` | = `--ai` | 脂肪（同上） |

### 3.4 Segmented Control token（**新增 4 条**）

| Token | 值 | 用途 |
|-------|----|------|
| `--segment-bg` | `#F3F4F6` 浅灰胶囊底 | Segmented 整体背景条 |
| `--segment-active-bg` | `var(--brand)` | 选中段色块 |
| `--segment-text-active` | `#FFFFFF` | 选中段文字 |
| `--segment-text-idle` | `var(--text-2)` `#6B7280` | 未选段文字 |

> **4 段等宽锁死**（与 §1 一致）；高度 40px；圆角 999px（pill）；过渡 `var(--motion-fast)`。

### 3.5 进度环 token（**新增 2 条**）

| Token | 值 | 用途 |
|-------|----|------|
| `--ring-track` | `#F3F4F6` | ProgressRing 背景轨道 |
| `--ring-progress` | `var(--brand)` | ProgressRing 进度色；溢出 = `--danger` |

### 3.6 装饰 vs 文字原则（与 daily-ui §10.2 一致）

- **餐色 4 档 / 营养素 3 色 / (daily-ui) 心情 5 色 / 进度环装饰色**：仅用于图形装饰（卡条 / dot / 饼图扇形 / 进度条）；**不承载文字内容**
- 文字色 100% 走 `--text-1/2/3` 或 `--brand`，**始终满足 WCAG AA 4.5:1**
- WCAG 4.5:1 例外：emoji + 装饰 dot 不算"文字承载"

### 3.7 字号 / 间距 / 圆角 / 字体栈

全部沿用 daily-ui §3.3 / §3.4 / §3.7，不新增。

### 3.8 图标集

| 分类 | 图标 |
|------|------|
| Tab（与 §1 一致） | 🏠📋🎯📝🌿👤 |
| **餐段 4 emoji（新增）** | 🍳 早 / 🥗 午 / 🍲 晚 / 🍪 加 |
| **营养素 3 emoji（新增）** | 🥩 蛋白 / 🍞 碳水 / 🧈 脂肪 |
| **食物分类 6 emoji（新增）** | 🥩 肉类 / 🥬 蔬菜 / 🍚 主食 / 🍎 水果 / 🥤 饮品 / 🍫 零食 |
| 操作 | 🔍📤✏🗑✓⟲⚠🌙➕ |
| 状态 | ✏草稿 ⚠冲突 ⟲重试 📤导出 |

> **Emoji 优先于纯色**：餐段以 🍳🥗🍲🍪 为主视觉，餐色仅作卡条 / dot 辅助。

---

## 4. 核心组件

### 4.1 全局共享组件（6 个 · 跨子模块复用 = 全局）

| 组件 | 用途 | 关键 props / 状态 |
|------|------|-------------------|
| `SegmentedControl` | 健康 Tab 顶部 4 段 | props: `segments[]`, `activeKey`；**lock 4 段**（与 §1 YAGNI 一致，不加段不溢出）；h=40px，pill 圆角 999px；过渡 `var(--motion-fast)` |
| `ProgressRing` | 环形进度环（健康进度 / 未来体重） | props: `value`, `max`, `color=brand`, `overflowColor=danger`；中央 `%` + 文本 |
| `BottomTab6` | 底部 6 项 Tab Bar | daily-ui v1.2 配套：`BottomTab5` → `BottomTab6` 改名（顺序 🏠📋🎯📝🌿👤）；prop: `badges[6]` |
| `AppHeader` | 顶部栏 | 返回 / 标题 / 右侧操作；右侧 slot 支持 `📤` 导出 / `➕` 入口 |
| `EmptyState` | 空态 | 主图（沿用 daily-ui SVG）+ 标题 + 副文案 + CTA |
| `Toast` | 顶部短提示 | success / failure / info；2s 自消失；底部动作 slot |

> **与 §1 / §3 联动**：本表中的 `SegmentedControl` 与 `ProgressRing` 即"触发了 §1 YAGNI 升级条件的真复用对象"，升级为全局；其余模块专属仍带 `Meal` 前缀（YAGNI 仍生效）。

### 4.2 Meal 模块专属组件（统一 `Meal` 前缀，10 个）

| 组件 | 用途 | 关键状态 |
|------|------|---------|
| `MealCard` | H1 / H1.x 餐次卡片 | 4 形态（早/午/晚/加餐，色条 + emoji）；空（未加）/ 已加（kcal + 条目数）；长按编辑 / 删除 |
| `MealListItem` | H1.x / H3.x 食物条目行 | emoji + 名称 + 份量 + kcal；**v1 不实现左滑删除动画**（v1.1+） |
| `MealPickerSheet` | H1.x 加餐 / 详情 / 编辑 / **懒人模式** **四模态 Sheet** | mode=`add` / `detail` / `edit` / `quick`；顶部 4 chip 切换 |
| `MealProgressOverview` | H1 顶部今日营养概览（包 ProgressRing + 文字层） | 4 状态：待配置 / 已用 / 接近 / 溢出 |
| `WeekChart` | H2 7 天热量柱状图（含参考线） | 加载 / 数据 / 缺数据 |
| `MacroDonut` | H2 宏量营养素饼图 | 加载 / 数据 / 缺数据 / 仅懒人 |
| `FoodItem` | H3 / H3.x 食物条目 | 分类 emoji + dot + 名称 + 分类 + kcal/100g + 三营养素；内置 / 「🌟 我的」（自定义）双重态 |
| `FoodDetailSheet` | H3.x 食物详情 / 自定义表单 **二模态 Sheet** | mode=`view` / `custom` |
| `ProfileField` | H4 chip 字段 | 5 种类型：text / number / select / slider / readonly |
| `CategoryFilter` | H3 分类横向筛选 | 全选 + 6 分类（主食/肉类/蔬菜/水果/饮品/零食） |

### 4.3 全局触发器（Meal 模块持有 · 组件化触发器）

> 本表为 Meal 模块持有的"组件化触发器"（独立组件）。§2 中列出的 Tab 红点 / H1 长按 / H3 字母索引 / 拍照禁用等 UI 触发场景，分别归属以下组件 prop / 行为：
> - **Tab 红点** → `BottomTab6.props.badges[5]`（健康 Tab 索引 5）
> - **H1 长按** → `MealCard.onLongPress` → 唤起 `MealPickerSheet` mode=`edit`
> - **H3 字母索引** → H3 页面级 scrollSpy，不单列组件
> - **拍照禁用** → `/health/camera` 占位页面级 `disabled`

| 组件 | 用途 | 调用方式 |
|------|------|---------|
| `MealExportTrigger` | H1 / H2 顶部"📤"导出触发 | 唤起 G2 `GlobalExportSheet`；传 `{type:'meal', scope:'month'}`（v1 仅月度，单餐/周聚合留 v1.1） |
| `MealFAB` | H1 顶部"➕ 餐次" FAB | 当前时段自动判定餐段 → 唤起 `MealPickerSheet` mode=`add`；**长按 → mode=`quick`**；超推荐 N+1 时弹 toast "今日 [段] 已记录 [N] 次" |

### 4.4 命名空间规则（3 层）

| 类型 | 前缀 | 示例 | 范围 | 命名升级触发 |
|------|------|------|------|------|
| 全局共享（跨子模块复用） | 无 | `SegmentedControl` `ProgressRing` `BottomTab6` `AppHeader` `EmptyState` `Toast` | 全 App | 跨多子模块使用 |
| Meal 模块独立组件（有跨子调用潜力） | `Meal` | `MealCard` `MealListItem` `MealPickerSheet` `MealProgressOverview` `MealExportTrigger` `MealFAB` | 仅 Meal 模块 | 未来跨子模块用再升级全局 |
| Meal 模块子组件（语义内聚，未来不预期跨子） | 无（但 PascalCase） | `WeekChart` `MacroDonut` `FoodItem` `FoodDetailSheet` `ProfileField` `CategoryFilter` | 仅 Meal 模块内 | 未来体重/睡眠复用 → 升 Meal 前缀 + 评估升全局 |

### 4.5 图标集

沿用 §3.8。

### 4.6 MealPickerSheet 四模态详细交互

| mode | 关键交互 |
|------|---------|
| `add` | 食物搜索框 + 分类 chip + 食物列表（`FoodItem`） + 选中后「份量滑块（0.5 步进）+ 营养预览」+ 「确认」 |
| `detail` | 内嵌 Tab: 详情 / 编辑；详情 Tab 显示全部 `MealListItem` 与营养明细；点「✏ 编辑」跳 `edit` |
| `edit` | 同 add 但表单已预填当前 Meal 数据 |
| `quick`（**懒人模式，PRD AC-3**） | 单输入框「约 [ ] kcal」+ 备注（≤200 字）+ slot 显示当前时段（早/午/晚/加餐）；**跳过食物库搜索与份量滑块**；不显示 `MacroDonut` / 三大营养素（数据不足） |

**进入态行为**（mode=`add`）：
- 检测 `MealDraft.items` 非空 → 顶部 toast "检测到上次未提交草稿，是否恢复？" + 二选一按钮（恢复 → 预填 draft items；新建 → 清空 draft）；30s 无操作 = 自动 = 新建

---

## 5. 数据模型 + Mock 数据

### 5.1 实体字段

```js
// Meal —— 主存储
{
  id: "meal_<uuid>",                  // 全局唯一
  userId: "u_001",
  slot: "breakfast" | "lunch" | "dinner" | "snack",  // 必填
  takenAt: "2026-07-26T08:30:00+08:00",  // 时点；±12h 可调（MEAL-005）
  note: "煎饼加油条",                     // ≤200 字（MEAL-006）；可空
  // 派生字段（重算优先；可选缓存；不入永久存储；UI 可读不可写）
  kcal: 540,                            // = sum(MealItem.servings × Food.kcalPer100g / 100)
  nutrients: { protein: 22, carb: 68, fat: 18 },  // g
  itemCount: 3,                         // = items.length
  createdAt: "2026-07-26T08:30:05+08:00",
  updatedAt: "2026-07-26T08:31:22+08:00",
  deletedAt: null                       // 软删除；30 天回收站（MEAL-002）
}

// MealItem —— 餐次下食物条目
{
  id: "mi_<uuid>",
  mealId: "meal_<uuid>",
  foodId: "f_042",                      // 可选；用户自定义 → f_<uuid>
  nameSnapshot: "煎饼",                   // 冗余；food 删除/改名前用于历史保留
  // nameSnapshot 永远存「当时记录的名字」——不漂移（PRD §8 风险 #1）
  servings: 1.0,                        // 份数；0.5 步进；必填 >0（MEAL-004）
  unit: "份",                            // 单位显示：份=100g 或 自定义
  // 派生（同 Meal）
  kcal: 320,
  nutrients: { protein: 8, carb: 50, fat: 10 },
  createdAt: "...",
  deletedAt: null
}
// 当 foodId 对应的 Food 被删除：
//   - meal_item.foodId 置 NULL
//   - nameSnapshot 保持原值
//   - MealItem.kcal / nutrients 维持历史，不重新计算（PRD §US-MEAL-04 AC-3）
// UI 显示（MealListItem 当 foodId=NULL 时）：
//   - 显示「⚠ 已删除」label + 保留 nameSnapshot + 锁定 kcal/nutrients 数值
//   - 不出现任何"已修改" / "已同步"提示

// Food —— 食物（含内置 / 自定义）
{
  id: "f_042",                          // f_<内置序号> 或 f_<uuid 自定义>
  name: "煎饼",                          // 中文名
  nameAliases: ["煎饼果子", "jianbing"],  // 别名（中文 + 拼音 + 英文）；用于搜索
  category: "主食",                       // 6 分类（主食/肉类/蔬菜/水果/饮品/零食）
  kcalPer100g: 320,                      // 每 100g 千卡
  proteinPer100g: 8,                    // g/100g
  carbPer100g: 50,                      // g/100g
  fatPer100g: 10,                       // g/100g
  source: "中国食物成分表第 6 版",         // 数据来源（PRD §8 风险 #1）
  isCustom: false,                      // false=内置 / true=用户自定义（MEAL-012）
  ownerId: null,                        // 仅 isCustom=true 时 = userId
  createdAt: "...",
  deletedAt: null
}

// NutritionTarget —— 用户营养目标（MEAL-024）
{
  userId: "u_001",
  height: 165,                          // cm
  weight: 55,                           // kg
  sex: "female",                        // male | female
  age: 28,
  activityLevel: "light",               // sedentary | light | moderate | active | very_active
  // 派生
  estimatedKcal: 1750,                  // Mifflin-St Jeor × 活动系数
  estimatedProtein: 65,                 // g；约 1.2g/kg（轻量减脂）
  estimatedCarb: 220,                   // g；45-65% 总热量
  estimatedFat: 60,                     // g；20-35% 总热量
  // 推荐区间
  proteinRange: [10, 15],               // % 总热量
  carbRange: [45, 65],
  fatRange: [20, 35],
  updatedAt: "..."
}

// MealDraft —— 客户端 localStorage 模拟（H1.x 加餐 Sheet 草稿）
{
  draftKey: "meal_draft_u_001_2026-07-26_breakfast",
  slot: "breakfast",
  items: [/* MealItem[] 草稿态 */],
  note: "...",
  lastSavedAt: "..."
}
```

**派生字段语义**：
- **重算优先**：`MealItem.servings × Food.XPer100g / 100` 为单一真实来源
- **可选缓存**：实现时可作为只读缓存（前端 state / 视图层 memo）以减少重算
- **不入永久存储**：后端持久化 schema 不含这些字段（持久化设计归 writing-plans 阶段）；UI 可读不可写

### 5.2 派生计算

```js
// Meal.kcal = Σ MealItem.kcal
// Meal.nutrients = Σ MealItem.nutrients
// MealItem.kcal = servings × Food.kcalPer100g / 100
// MealItem.nutrients.X = servings × Food.XPer100g / 100

// 今日已用（kcal）
todayConsumed = meals.filter(m => sameDay(m.takenAt))
                  .reduce((s, m) => s + m.kcal, 0)
// 注意：mode=quick 的 Meal 计入 todayConsumed，但不计入 MacroDonut

// 今日剩余（kcal）
todayRemain = todayConsumed - nutritionTarget.estimatedKcal
//   < 0                  → "剩余 N kcal"（已用 <target）
//   ≥ target × 0.85      → "接近"（橙）
//   > target × 1.1       → "溢出"（红，进度环变 danger）

// 本周聚合
weeklyAggregate = meals.filter(m => within(m.takenAt, thisWeek))
                    .reduce((s, m) => ({ kcal:+m.kcal, p:+m.protein, c:+m.carbs, f:+m.fat }), zeros)
// 注意：aggregate 同样排除 quick 模式 Meal

// 宏量占比
macroPct = weeklyAggregate.nutrient / totalKcal
// 用于 MacroDonut 扇形

// 推荐摄入估算（Mifflin-St Jeor）
//   male:   BMR = 10×weight + 6.25×height - 5×age + 5
//   female: BMR = 10×weight + 6.25×height - 5×age - 161
//   TDEE = BMR × activityFactor:
//     sedentary 1.2 / light 1.375 / moderate 1.55 / active 1.725 / very_active 1.9
estimatedKcal = round(BMR × activityFactor)
```

### 5.3 id / 单位 / 命名规则

| 规则 | 值 |
|------|---|
| `Meal.id` | `meal_<uuid>` 全局唯一 |
| `MealItem.id` | `mi_<uuid>` 全局唯一 |
| `Food.id` | `f_001..f_NNN`（内置）/ `f_<uuid>`（自定义） |
| **业务唯一性** | `Meal (userId, slot)` 在 **5 分钟内** 连续 add → 视为同一 Meal 追加 MealItem（**不创建新 Meal**）；**5 分钟外**允许同 slot 多 Meal（如深夜加餐 → 凌晨加餐各自一条） |
| 份数 precision | 0.5 步进；最多 2 位小数 |
| 营养单位 | 展示统一 g；后端存 float g；UI 录入强校验 `g` vs `mg`（PRD §8 风险 #4） |
| 食物命名 | 中文 + 中文别名 + 拼音 + 英文；搜索归一化（PRD §8 风险 #3） |

### 5.4 Mock 数据范围

| 实体 | 数量 | 用途 |
|------|------|------|
| `Food` 内置 | 30+（代表 200+ 总量做截断演示） | H3 食物库 + H1.x 加餐 |
| `Food` 自定义 | 5（"🌟 我的食物"标签演示） | H3 「我的食物」 |
| `Meal` | **31 条（2026-07 全月）** + 当日 0-3 餐次多样 | H1 今日 + H2 本周 |
| `MealItem` | 平均 2-3 条/餐，覆盖内置+自定义食物 | H1.x 详情 |
| `NutritionTarget` | 1（"小米" 减脂期） | H4 显示 + 估算计算 |
| `MealDraft` | 0-1（演示草稿恢复） | H1.x 进入态 |

### 5.5 Mock 每日数据点（核心场景）

| 日 | 餐次 | 食物演示 | 演示要点 |
|----|------|---------|---------|
| 7/1 | 早+午+晚 | 燕麦 / 鸡胸 / 西兰花 | **完整日 + 内置食物** |
| 7/4 | 早+晚 | 煎饼 / 小米粥 | 早午晚 4 段分布 |
| 7/7 | 早+午+晚+加 | **4 段全满 + 自定义食物**（南瓜小米糊） | 自定义食物 demo + 健康 Tab 红点（4 段全满）|
| 7/10 | 0 餐 | — | **当 0 餐** + EmptyState |
| 7/14 | 早+午（懒人） | 早：手填"咖啡+面包" 350 kcal（**懒人模式 quick**）/ 午：内置 | **懒人模式**（PRD AC-3）|
| 7/15 | 早+午+晚 | 多种 | 完整周覆盖 |
| 7/20 | 0 餐 + 高摄入日 | — | 推荐摄入溢出演示 |
| 7/26（今日） | 0 餐 + 草稿 | — | **H1 草稿恢复 demo + 「记第一餐」CTA** |
| 7/31 | — | — | 月末 + 切换周 demo |

### 5.6 不模拟的部分

- 真实后端 API（原型用 localStorage + setTimeout 模拟）
- Mifflin-St Jeor 后端算法（v1 前端简化映射：`estimatedKcal = TDEE`，UI 仅展示非可调）
- 视觉识别（拍照 / 语音 / 条形码 = v1.2+，不在 Mock）
- AI 饮食建议（v1.1+，不在 Mock）

### 5.7 状态触发器（原型演示用，隐藏面板，仅 prototype 模式）

| 触发器 | 行为 | 期望 UI |
|--------|------|---------|
| 「重置今日餐次」 | 清空 7/26 全部 Meal | H1 回到 0 餐态 |
| 「快速加 1 餐」 | 在当前 slot 添加 1 个 MealItem（自带份数 = 1） | MealCard 显示已加态 |
| 「快速懒人加餐」 | mode=quick：350 kcal + 备注 | MealCard 显示已加（无营养明细） |
| 「切换档案」 | 模拟 3 套 NutritionTarget（减脂/维持/增肌） | MealProgressOverview 重算剩余 |
| 「清空草稿」 | 清 localStorage MealDraft | H1.x 进入无草稿恢复 toast |
| 「快进日期」 | 选择 7/29 / 7/31 演示周/月切换 | H2 周/月演示 |
| 「搜索词」 | mock 拼音「jf」→ 「鸡饭」；「jb」→ 「煎饼」 | H1.x 搜索命中 + 归一化验证 |
| 「拍照占位」 | /health/camera disabled + tooltip | 演示 v1 占位态 |

### 5.8 派生关系速查表

| 派生项 | 来源 | 用途 |
|--------|------|------|
| `Meal.kcal` | Σ MealItem.kcal | MealCard 右下；H2 柱状 |
| `Meal.nutrients` | Σ MealItem.nutrients | H1.x 详情 |
| `todayConsumed` | filter today + Σ | H1 MealProgressOverview |
| `todayRemain` | todayConsumed − target | H1 顶部"剩余 N" |
| `weeklyAggregate` | filter week + Σ（**排除 quick**） | H2 WeekChart + MacroDonut |
| `macroPct` | nutrient / totalKcal | H2 MacroDonut 扇形 |
| `estimatedKcal` | Mifflin-St Jeor × activity | H4 档案显示 |
| `nameSnapshot fallback` | Food 删则用 nameSnapshot | H1.x 单餐显示历史条目 |
| `MealProgressOverview 溢出` | todayConsumed > target ×1.1 | 进度环变 danger 色 |
| `MealProgressOverview 接近` | todayConsumed ≥ target ×0.85 | 文字变橙 |
| `MealProgressOverview 待配置` | NutritionTarget 缺失必填字段 | 显示"⚙ 完成档案以获取推荐" CTA |

---

## 6. 关键状态机 / 交互流

### 6.1 7 条核心交互流

| # | 流程 | 路径 | 关键反馈 |
|---|------|------|---------|
| **F1** | 30 秒加餐 | MealFAB 单击 → MealPickerSheet mode=`add` → 食物搜索 → 份量滑块 → 确认 | Sheet 关闭 + MealCard 已加态 + MealProgressOverview 重算 |
| **F2** | 懒人模式（PRD AC-3） | MealFAB 长按 → MealPickerSheet mode=`quick` → kcal + 备注 → 确认 | Sheet 关闭 + MealCard 已加（无营养明细） |
| **F3** | 食物库搜索 | H3 搜索 → 拼音/中/英/别名归一化 → 选食物 → FoodDetailSheet mode=`view` → 加入餐次 | 加入后回到 H1.x 加餐 Sheet |
| **F4** | 自定义食物 | H3 搜索无结果 → CTA → FoodDetailSheet mode=`custom` → 名称/分类/100g 营养 → 保存 | "🌟 我的" 标签演示 |
| **F5** | 档案配置 | H4 → chip 切换 `[基础 \| 目标 \| 偏好]` → 基础 chip 录入 5 字段 → 保存 \| 目标 chip 设置能量/蛋白/碳水/脂肪目标值 → 保存 \| 偏好 chip 设置饮食偏好（素食/低钠/低糖 等 v1 占位）→ 保存 → MealProgressOverview 重算剩余 + H4 显示估算结果 |
| **F6** | 本周营养汇总 | 健康 Tab → 本周 → WeekChart + MacroDonut | 7 天柱状 + 宏量扇形 + 推荐区间偏离提示 |
| **F7** | 月度 CSV 导出 | H1 / H2 顶部"📤" → MealExportTrigger → G2 GlobalExportSheet (scope=`month`) | 下载 CSV |

### 6.2 MealCard 状态机（4 形态 × 4 状态）

```
[空]    未加餐  → 显示 "➕ 记 [段]餐" CTA → 唤起 MealPickerSheet mode=`add`
[已加]  已加餐  → 显示 emoji + 段名 + 总 kcal + 条目数
                 → 长按: 编辑 / 删除菜单 → 唤起 mode=`edit` 或删除（二次确认）
[草稿]  draft 恢复中 → MealCard 显示 "⚠ 草稿" + localStorage draft 提示
[当前]  正在 MealPickerSheet 编辑 → MealCard 弱化显示
```

### 6.3 MealPickerSheet 四模态

```
mode=add       → 食物搜索 + 份量 + 营养预览 + 确认
mode=detail    → 全部 MealListItem + 营养明细 + 「✏ 编辑」按钮
mode=edit      → 同 add，但 MealItem 已预填
mode=quick     → 单 kcal 输入 + 备注 + 确认（懒人模式，跳过食物库 + 份量）
```

### 6.4 MealProgressOverview 4 状态机

```
待配置
  │ NutritionTarget 不完整（缺身高/体重/性别/年龄/活动量任一）
  │ → "⚙ 完成档案以获取推荐" CTA + 跳 H4
  ▼
已用
  │ todayConsumed < target × 0.85
  │ → "已用 X%" + "剩 N kcal"
  ▼
接近（橙）
  │ todayConsumed ≥ target × 0.85 且 ≤ target × 1.1
  │ → "已用 X%" + "剩 N kcal"（橙）
  ▼
溢出（红）
  │ todayConsumed > target × 1.1
  │ → "已超 +X%" + 进度环变 danger 色
```

### 6.5 WeekChart 3 状态 + MacroDonut 4 状态

```
WeekChart:
  [加载]   < 300ms skeleton
  [数据]   7 柱 + 推荐区间参考线（虚线） + 柱顶 kcal 数字
  [缺数据] 当周 0 餐 → "本周暂无记录" + 「记第一餐」CTA

MacroDonut:
  [加载]   < 300ms skeleton
  [数据]   3 扇形 + 推荐区间标注 + 偏离 dot 提示
  [缺数据] 本周 0 餐 → "数据不足"
  [仅懒人] 本周仅 quick 模式无食物明细 → 不显示扇形比例，显示"数据不足 — 待录入食物明细"（PRD §8 风险 #2 文案）
```

### 6.6 搜索 4 状态（H3 食物库 / H1.x add 模式）

```
[初始]    空字符串 → 显示"试试 [拼音/中文/英文]"
[搜索中]  防抖 300ms → loading
[命中]    列表更新
[无结果]  显示"换个关键词" + 自定义食物 CTA
```

### 6.7 懒人模式 vs 普通模式的营养显示

| 模式 | Meal 营养字段 | 三大营养素 | UI 显示 |
|------|--------|------------|---------|
| add / edit | 通过 Food.kcalPer100g 等计算 | 有 | MacroDonut 完整显示 |
| quick | 仅手填总 kcal | 无（数据不足） | MealCard 仅显示总 kcal；H2 不计入 MacroDonut |

---

## 7. 边缘 / 错误状态清单

| 场景 | UI 表现 | 触发位置 |
|------|---------|---------|
| 当日 0 餐 | H1 EmptyState + "记第一餐" CTA + 🌿 健康 Tab 红点 | H1 |
| 当前段已加满 | MealFAB 仍可加，toast "今日 [段] 已记录 [N] 次" | MealFAB |
| 加餐 Sheet 未提交关闭 | 二次确认（防误触） | H1.x |
| H1 MealCard 长按 | 编辑 / 删除菜单 | H1 |
| 删除餐次 | 二次确认 → 软删除 → 30 天回收站 | H1 |
| H2 当周 0 餐 | "本周暂无记录" + 「记第一餐」CTA | H2 |
| H2 历史月 0 餐 | "本月暂无记录" | H2 |
| 历史未写日 | 不可点击 / 不提供补写 CTA | H2 |
| 未来日期 | 不可点击 / 灰态禁用 | H2 |
| H3 搜索无结果 | "换个关键词" + 自定义食物 CTA | H3 / H1.x add |
| 搜索失败 / 网络错误 | toast "搜索失败 ⟲ 重试" | H3 / H1.x add |
| H3 食物库首次加载 | LoadingSkeleton（≤ 300ms） | H3 |
| H4 档案未填完整 | H1 MealProgressOverview = "待配置" + H4 顶部"完成档案以获取推荐" | H4 + H1 |
| H4 档案已填 | MealProgressOverview 显示估算结果 | H4 + H1 |
| 自定义食物表单校验失败 | 行内错误 + 「保存」按钮置灰 | H3.x custom |
| 懒人模式 kcal = 0 或空 | 「确认」按钮置灰 | H1.x quick |
| 网络中断 | ErrorState + 离线缓存 localStorage | 全局 |
| 拍照记餐（v1 占位） | /health/camera disabled + tooltip "拍照记餐 v1.2+ 上线" | /health/camera |
| MealCard 草稿恢复 | 顶部 toast "检测到上次未提交草稿" + 30s 自动 = 新建 | H1.x add 进入 |
| MealFAB 长按 | mode=`quick` 懒人模式 | H1 |

### 7.1 防误触与二次确认

| 操作 | 二次确认 |
|------|---------|
| 删除 MealCard | 必须二次确认 |
| 加餐 Sheet 未提交关闭 | 二次确认（防误触；30s 草稿超时 = 新建） |
| FoodDetailSheet 自定义食物保存 | 可选（表单未变更时直接关闭） |
| H4 档案重置 | 二次确认 |

---

## 8. 响应式策略

### 8.1 断点

| 断点 | 宽度 | 设备 | 饮食模块布局 |
|------|------|------|-------------|
| **sm** | ≤ 640px | 手机（主要目标） | 单列；底部 6 项 Tab Bar |
| **md** | 641–1024px | 平板/小桌面 | 单列；max 720 居中 |
| **lg** | ≥ 1025px | 桌面 | 单列；Sheet / Dialog 居中 |

### 8.2 关键页面响应式

| 页面 | sm（移动） | md（平板） | lg（桌面） |
|------|----------|-----------|-----------|
| **H1 今日** | 单列纵向滚；6 项 Tab 完整 | 单列最大 720 居中 | 同 md |
| **H2 本周** | 单列；WeekChart 全宽 | 单列最大 720 居中 | 单列最大 960 居中（图表不放大） |
| **H3 食物库** | 单列；CategoryFilter 横滚 | 单列最大 720 居中 | 单列最大 720 居中 |
| **H4 档案** | 单列；chip 横滚 | 单列最大 720 居中 | 同 md |
| **MealPickerSheet** | 自底滑入占屏 80% | 居中 600 宽 | 居中 600 宽 |
| **FoodDetailSheet** | 自底滑入占屏 80% | 居中 480 宽 | 居中 480 宽 |
| **ConfirmDialog**（删除等） | 自底全屏 | 居中 480 宽 | 居中 480 宽 |

### 8.3 6 项 Tab Bar 在 sm 下的处理

- 6 项 Tab 等距分布；图标 24px，文字 11px
- 选中态用 `--brand`；未选用 `--text-2`
- 🌿 Tab 红点 = 4px 圆点，右上角定位，不挡图标
- Tab 顺序固定，不提供隐藏 / 折叠入口
- **与 daily-ui v1.1 兼容**：daily-ui v1.2 加修订记录说明 BottomTab5 → BottomTab6

### 8.4 lg 桌面端特别说明

- **当前 PRD 用户画像 A/B/C 全为移动场景**，桌面端仅做"日间可读"——无大屏专属布局
- 健康 Tab 不提供手动切换布局（与 daily-ui §9.4 一致）
- 桌面端无 V1 三栏 Dashboard（任务模块有，但饮食不引入）

---

## 9. 可访问性（WCAG 2.1 AA）

### 9.1 通用规则

| 项 | 规则 |
|----|------|
| 颜色对比度（文字） | 文字 vs 背景 ≥4.5:1；大文字 ≥3:1；全部文字用 `--text-1/2/3` 或 `--brand`，**已达 AA** |
| 装饰色例外 | 餐色 4 档 / 营养素 3 色 / 进度环装饰色仅作图形，不承载文字；emoji 优先 |
| 焦点环 | 键盘聚焦时 2px `--brand` 描边 + 2px offset |
| 触摸目标 | ≥ 44×44px |
| 语义化 | H1 `<section>` 4 餐次；`MealCard` `<article>`；Sheet `<form>` |
| ARIA | Sheet `role=dialog aria-modal=true aria-label`；Toast `aria-live=polite`；ProgressRing `role=progressbar aria-valuenow aria-valuemax` |
| 屏幕阅读器 | 状态变更用 `aria-live` 播报 |
| 键盘导航 | Tab 顺序合理；Sheet 打开焦点锁定；Esc 关闭 |

### 9.2 屏幕阅读器关键脚本

| 触发 | aria-live | 文本 |
|------|-----------|------|
| 进入 H1 | — | "今日饮食，2026 年 7 月 26 日" |
| H1 进度环变化 | polite | "今日已用 1200 千卡，占推荐 67%；剩余 550 千卡" |
| 加餐成功 | polite | "早餐已记录 3 项，共 540 千卡" |
| 懒人模式加餐成功 | polite | "早餐已记录，共 350 千卡" |
| 搜索命中 | polite | "搜索『jf』命中 2 条：煎饼、鸡饭" |
| 搜索无结果 | assertive | "搜索无结果，可自定义食物" |
| 档案未填完整 | assertive | "档案未填完整，无法获取推荐摄入估算" |
| MealProgressOverview 溢出 | assertive | "今日热量已超出推荐 15%" |
| MealFAB 长按 | — | "已切换至懒人模式" |
| MealCard 删除 | assertive | "餐次已删除，30 天内可在回收站恢复" |
| Sheet 二次确认未提交关闭 | assertive | "检测到未提交修改，是否保存草稿" |

### 9.3 红绿色盲

- 营养素偏离提示不只靠红色 ⚠ + 文案"超出推荐"双信号
- MealProgressOverview 溢出除变红外，中央文字 + 进度环描边加粗

---

## 10. 交付物

```
docs/lifewise/designs/
  ├─ 01-task-ui/
  │    ├─ 2026-07-26-task-ui-design.md
  │    └─ 01-task-ui-v2.html
  ├─ 02-daily-ui/
  │    ├─ 02-daily-ui-design.md
  │    └─ 02-daily-ui-v1.html
  ├─ 03-expense-ui/                                  （已存在，expense 模块设计）
  └─ 04-diet-ui/                                     ← 新建
       ├─ 2026-07-26-diet-ui-design.md               ← 设计文档
       └─ 04-diet-ui-v1.html                         ← HTML 原型
```

| 产物 | 形式 | 大小目标 |
|------|------|---------|
| 设计 Markdown | 12 章 + 4 附录（A 截图 / B PRD 映射 / C 跨模块协调 / D 待确认） | 800-900 行 |
| HTML 原型 | 单文件，内联 CSS+JS，无外部资源依赖；Chart.js 可走 CDN | 90-150 KB |
| Mock 数据 | 原型内嵌 | 31 条 Meal + 30+ Food + 5 自定义 + 1 NutritionTarget |
| 状态触发器 | 原型右上角隐藏面板 | 仅 prototype 模式 |
| daily-ui 修订 | 加 v1.2 修订记录（`BottomTab5 → BottomTab6`） | 在 `02-daily-ui-design.md` §0 修订记录追加 |

---

## 11. 自检清单（交付前必过）

| 类别 | 检查项 | 通过条件 |
|------|--------|---------|
| **覆盖度** | 4 段 H1-H4 全部可访问 | 每个页面有入口可点击到达 |
|  | MealPickerSheet 四模态全部可演示 | mode 切换 chip 完整 |
|  | H3.x FoodDetailSheet 二模态全部可演示 | mode 切换完整 |
|  | 7 条交互流全部可演示 | F1-F7 每次能从入口走到结束 |
|  | 内嵌状态全部可触发 | 演示面板 8 项触发器 + 主动触发 |
|  | 边缘状态全部展示 | 见 §7 22 行每条都有演示路径 |
| **布局** | 移动端 375px 正确 | 无横向滚动；6 项 Tab 完整 |
|  | 平板 768px 正确 | 自适应；H1-H4 单列居中 |
|  | 桌面端 1280px 正确 | H1-H4 单列；Sheet 居中 |
| **设计系统** | Token 全部生效 | 所有颜色/字号/圆角/间距均用 CSS 变量，无硬编码 |
|  | **`--streak` 引入** | 与 `--warning` 同色复用；3.3 `--nutrient-carb` 引用 |
|  | Segmented token 4 条 | `--segment-bg / --segment-active-bg / --segment-text-active / --segment-text-idle` |
|  | ProgressRing token 2 条 | `--ring-track / --ring-progress` |
|  | 中文字体正确 | PingFang SC / HarmonyOS Sans 生效，有 fallback |
| **交互** | MealPickerSheet 4 模态切换 | add / detail / edit / quick 全部可演示 |
|  | MealFAB 长按 = quick | 长按 1.2s 唤起 mode=quick |
|  | 草稿恢复 toast | mode=add 进入有 draft 时弹 toast |
|  | 懒人模式营养缺失 | quick 模式确认后 MealCard 显示 kcal 不显示营养明细 |
|  | MacroDonut 排除 quick | H2 统计时 quick 模式 Meal 不计入扇形比例 |
|  | 健康 Tab 红点 | 4 段未满 / 溢出 / 档案未填 任一 → 红点 |
|  | 拍照占位 disabled | /health/camera 进入 disabled + tooltip |
| **文案** | 无占位符 | 无 Lorem ipsum、无 TODO |
|  | 中文文案完整 | 按钮、提示、错误信息全部中文 |
|  | 餐段 emoji 映射正确 | 🍳(早)→🥗(午)→🍲(晚)→🍪(加) |
| **可访问性** | WCAG 2.1 AA 关键项通过 | 文字对比度、焦点环、触摸目标、SR 朗读（§9.2）|
| **性能（软目标）** | 加餐 P95 ≤ 1.2s | 设计目标高于 PRD §7 KPI（≤ 1.5s），为工程实现留工程缓冲；HTML 原型 mock 模拟 ≈ 0.8s |
|  | 营养汇总 P95 ≤ 500ms | H2 WeekChart + MacroDonut 加载 ≤ 500ms |

---

## 附录 A · 页面截图清单（原型的关键截图）

> 在浏览器打开 `04-diet-ui-v1.html` 后，可在以下路径截屏：

1. 移动端 H1 今日（含 MealProgressOverview + 4 段 MealCard）
2. 移动端 H2 本周（WeekChart + MacroDonut + 推荐区间）
3. 移动端 H3 食物库（搜索 + CategoryFilter + 食物列表 + 自定义食物标签）
4. 移动端 H4 档案（5 字段 + 推荐摄入估算）
5. 移动端 H1.x MealPickerSheet mode=add（食物搜索 + 份量滑块 + 营养预览）
6. 移动端 H1.x MealPickerSheet mode=quick（懒人模式：单 kcal + 备注）
7. 移动端 H1.x MealPickerSheet mode=detail（详情 + 编辑入口）
8. 移动端 MealProgressOverview 待配置 / 已用 / 接近 / 溢出 4 态
9. 移动端 草稿恢复 toast（H1.x add 进入）
10. 移动端 拍照占位 disabled + tooltip
11. 桌面端 H1 + H2 + H3 + H4 单列布局

---

## 附录 B · PRD 需求 → 设计 映射

| PRD 需求 | 视图 / 状态 |
|---------|-----------|
| MEAL-001 新增餐次（早/午/晚/加餐） | H1.x MealPickerSheet mode=add |
| MEAL-002 编辑 / 软删除餐次 | H1.x mode=`edit`；H1 MealCard 长按 |
| MEAL-003 餐次下添加食物条目 | H1.x mode=add 内嵌 FoodItem + 份量 |
| MEAL-004 食物份数 | H1.x mode=add 份量滑块 0.5 步进 |
| MEAL-005 餐次时间 | Meal.createdAt 取当前；±12h 可调 |
| MEAL-006 餐次备注 | H1.x 任意 mode（备注 ≤200 字） |
| **lazy 模式（PRD AC-3）** | **H1.x mode=quick（懒人打卡）** |
| MEAL-010 内置 200+ 食物库 | H3 FoodItem（mock 截 30+） |
| MEAL-011 食物库搜索（拼音首字母） | H3 / H1.x add 搜索框（拼音/中文/英文/别名归一化） |
| MEAL-012 用户自定义食物 | H3.x FoodDetailSheet mode=custom |
| MEAL-013 食物分类筛选 | H3 CategoryFilter（6 分类 chip） |
| MEAL-020 餐次卡路里合计 | MealCard 右下（auto） |
| MEAL-021 今日/本周热量趋势 | H2 WeekChart（7 天柱状 + 参考线） |
| MEAL-022 本周宏量营养素饼图 | H2 MacroDonut（3 色扇形 + 推荐区间） |
| MEAL-023 单餐营养详情卡 | H1.x mode=detail |
| MEAL-024 每日推荐摄入估算 | H4 基础 chip + Mifflin-St Jeor + MealProgressOverview |
| MEAL-030 月度 CSV 导出 | MealExportTrigger → G2 → `{type:'meal', scope:'month'}` |

---

## 附录 C · 跨模块协调备忘

### daily-ui v1.2 配套修订

`docs/lifewise/designs/02-daily-ui/02-daily-ui-design.md` 中 §0 修订记录需追加：

```markdown
- v1.2 修 1 项 Tab 兼容：① BottomTab5 → BottomTab6 改名（健康 Tab 编号 5）；② 顺序追加 🌿 健康 Tab 于 📝 日报 与 👤 我 之间；③ 「🌿 健康」Tab 红点规则独立（详见 04-diet-ui §1.3）
```

### 与 03-expense-ui 边界（假设后续接入）

- 饮食模块**不进** expense 模块；两者无 v1.0 联动
- 食物分类 emoji 与 expense 餐饮分类**不共享**——各自维护，避免跨模块漂移

### 与 06-ai-analysis 边界

- AI 饮食建议（v1.1+）由 06-ai-analysis 模块提供；本设计 §0.3 明确**不在 MVP**

---

## 附录 D · 待确认项

| 项 | 处理 |
|----|------|
| MEAL-031 月度 PDF 报告 | **v1.1+** 推迟（依据 PRD §4 RICE 评分 0.06）；v1 仅月度 CSV（scope=`month`）；writing-plans 阶段定 PDF 库选型（如 jsPDF + html2canvas） |
| 体重 / 睡眠 / AI 洞察 chip | PRD 不在 MVP；H4 段内 v1.x 占位不实现（与 §1.5 一致） |
| 单餐 / 周聚合 CSV 导出 | v1.1+（v1 仅月度） |
| Mifflin-St Jeor 算法的精度 | v1 前端简化映射；writing-plans 阶段定后端精确实现 |
| `nameSnapshot` 历史保留上限 | 暂未限制；按需在 v1.1+ 加归档策略 |

---

*文档版本：v1.0*
*下一步：生成 `04-diet-ui-v1.html` → 自审 → git commit → 用户审稿 → 移交 writing-plans*
