# 计划管理模块 UI 原型设计

> 模块代号：`plan`
> 所属产品：数字生活 Lifewise（项目代号：照片档）
> 文档版本：v1.0
> 状态：Design Draft
> 创建日期：2026-07-28
> 关联 PRD：`docs/lifewise/specs/PRD/05-plan-management.md`
> 关联架构：`docs/lifewise/architecture/`

---

## 0. 设计目标

将 PRD §1 的 SMART 目标「**让用户把长期目标拆解为里程碑并可视化进度**」转译为可交互的 UI 原型，对齐：

- **O1**：让用户「长期目标可视化、不再忘记」
- **KR1.1**：30 天内 ≥ 1 个计划的注册用户 ≥ 40%
- **KR1.2**：有里程碑完成的计划 ≥ 50%
- **KR1.3**：临近 deadline 推送响应率 ≥ 30%

设计基线：

- 沿用 01-task-ui / 03-expense-ui 的全局 Token 与组件命名
- Tab 3 = 「🎯 计划」（替换原占位）
- 三视图：瀑布（默认）/ 时间轴 / 看板
- 不引入手帐元素（遵循现有体系）
- 单文件 HTML 原型（`05-plan-ui-v1.html`）

---

## 1. 信息架构（IA）

### 1.1 主导航

| Tab | 名称 | 图标 | 备注 |
|---|---|---|---|
| 1 | Home | 🏠 | 现有 |
| 2 | Task | 📋 | 现有 |
| **3** | **Plan** | **🎯** | **本次新增**（替换原「目标」占位） |
| 4 | Daily | 📝 | 现有 |
| 5 | Me | 👤 | 现有，回收站入口在此 |

> 跨模块影响：06-ai 设计时也需遵循新的 Tab 顺序；03-expense-ui 设计文档 Tab 5「👤 Me」已兼容。

### 1.2 Plan 模块内部结构

```
Plan Tab
├─ V1 PlanHub（默认首屏）
│   ├─ Sub-Tab：瀑布 / 时间轴 / 看板
│   ├─ 顶部 FilterBar（状态 / 分类 / 排序）
│   ├─ 「+ 计划」FAB
│   └─ EmptyState（0 计划时）
├─ V2 PlanSheet（创建 / 编辑 半屏 Sheet）
│   ├─ 表单字段：标题 / 描述 / 分类 / 起止 / 状态
│   └─ 提交按钮区
├─ V3 PlanDetail（右侧 Drawer / 移动端全屏 Sheet）
│   ├─ Hero 区：标题 / 分类 / 起止 / 进度环 / 剩余天数
│   ├─ Sub-Tabs：里程碑 / 关联任务 / 历史
│   ├─ V3a MilestoneList（长按 300ms 拖拽 + 自动保存）
│   ├─ V3b LinkedTasks（关联任务 Sheet 选择器）
│   ├─ V3c History（变更日志）
│   └─ 「+ 里程碑」按钮
├─ V4 MilestoneSheet（创建 / 编辑里程碑 半屏 Sheet）
│   ├─ 表单字段：标题 / 计划完成日 / 关联任务入口
│   └─ 提交按钮区
├─ V5 TaskPickerSheet（关联任务选择器）
│   ├─ 顶部搜索栏
│   ├─ 首组：今日 / 本周
│   ├─ 全部任务列表（可滚动）
│   └─ 多选 + 底部「确认 N 项」
├─ V6 MISSED AdjustSheet（MISSED 调整）
│   ├─ 三选：调整 deadline / 拆为子里程碑 / 跳过
│   └─ 「取消」按钮
├─ V7 PlanKanban（按分类）
│   ├─ 6 列（学习 / 工作 / 健康 / 生活 / 财务 / 其他）
│   └─ 卡片拖动跨列 = 改分类
└─ V8 PlanTimeline（时间轴）
    ├─ 桌面：横轴 = ±6 月，圆点表示里程碑状态
    └─ 移动端：纵向时间倒序（最新在上）
```

### 1.3 回收站入口

`👤 Me` → 设置 → 回收站（**3 类共用**：计划 / 任务 / 记账；日记为即时式记录，不进入回收站，对齐决策 11）

### 1.4 视图数量

- 主屏（V1）+ 7 辅助视图（V2–V8）= **8 个视图**
- 详情抽屉（V3）内含 3 个子视图（V3a / V3b / V3c）

---

## 2. 视图设计

### 2.1 V1 PlanHub（瀑布模式）

**桌面布局**（1440px）：

```
┌─────────────────────────────────────────────────┐
│ [🎯 计划]      [瀑布|时间轴|看板]    [+ 计划] │
├─────────────────────────────────────────────────┤
│ FilterBar: [状态▾] [分类▾] [排序▾] [N 个计划]  │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐           │
│  │计划A │ │计划B │ │计划C │ │计划D │           │
│  │ 65%  │ │ 30%  │ │ 100% │ │ 80%  │           │
│  └──────┘ └──────┘ └──────┘ └──────┘           │
│                                                  │
│  ┌──────┐ ┌──────┐                              │
│  │计划E │ │计划F │                              │
│  └──────┘ └──────┘                              │
│                                                  │
│                              [+] FAB             │
└─────────────────────────────────────────────────┘
```

**每张 PlanCard 内容**（完整可视化）：

| 位置 | 内容 | 样式 |
|---|---|---|
| 顶 | 状态条 4px | `status-bar`，ACTIVE = primary，DONE = success，ABANDONED = text-3 |
| 主 | 大标题（2 行截断） | `fs-display`，14px 行高 |
| 中 | 分类 emoji + 分类名 | `CategoryTag` |
| 进度区 | 左侧大圆环 + 右侧「3/5 已完成」 | `ProgressRing`（64×64） |
| 底 | 剩余天数徽章 + 「上次更新 N 天前」 | `DayBadge` + `LastUpdated`（>14d warning） |

**DayBadge 色梯度**：

| 区间 | 颜色 | 语义 |
|---|---|---|
| `> 14d` | `--plan-primary` | 富余 |
| `7–14d` | `--warning` | 临近 |
| `< 7d` | `--danger` | 紧迫 |
| 已过期 | `--plan-missed-ink` | MISSED |

**EmptyState**：

- 大插图（🌱 萌芽）
- 「还没有计划，把第一件想做的事立起来吧」
- 主按钮「+ 新建计划」

### 2.2 V1 Sub-Tab 顺序（按使用频率）

`瀑布 → 时间轴 → 看板`

理由：

- **瀑布** = 默认首屏，最高频（每日打开）
- **时间轴** = 长周期回顾（PRD §1 SMART「长期目标可视化」），中高频
- **看板** = 按分类管理（中频场景，主要用于跨分类调整）

### 2.3 V7 PlanKanban

**桌面**：6 列网格，每列宽度 = `(100% - 5*gap) / 6`

- 列头：`CategoryEmoji + 分类名 + 计数`
- 卡片：同 PlanCard 略简，只显示进度环 + 标题 + 剩余天数
- 拖动：跨列 = 改分类（右侧「✓ 已改」Toast）

**移动端**：横滑为主 + 顶部圆点指示器

```
┌─────────────────────────────────────┐
│ ●学习(3) ○工作(2) ○健康(4) ○... →  │  ← ●=当前列，点击可跳
├─────────────────────────────────────┤
│                                     │
│         当前列：学习                │
│         ┌──────┐                    │
│         │ 卡片 │                    │
│         └──────┘                    │
│                                     │
│         ← 左滑看工作 →              │
└─────────────────────────────────────┘
```

### 2.4 V8 PlanTimeline

**桌面**：横轴 = ±6 个月，悬停圆点看详情

```
Jan   Feb   Mar   Apr   May   Jun   Jul   Aug   Sep   Oct   Nov   Dec
              ●─────○                📍              ●
              ↑              ↑                       ↑
           planA start    planA mid                planA end
```

- 每行 = 一个计划，左侧缩略信息（分类 emoji + 标题前 12 字）
- 在 deadline 月份画 8px 圆点 + 状态色
- 悬停圆点（PC） / 点击圆点（移动） → 浮窗显示里程碑列表 + 「查看」按钮

**移动端**：纵向时间倒序（最新在上）

```
2026-08  ← 最新在上
●───○───📍

2026-07
●───●

2026-06
○

         ↓ 上滑看历史
```

---

## 3. 设计 Token

### 3.1 复用 01-task-ui 现有 Token

`--bg / --surface / --border / --text-1/2/3 / --success / --warning / --danger / fs-display~micro / sp-1~6 / r-sm~lg`

### 3.2 新增（plan 模块专属）

```css
/* 主色：靛蓝，「目标/长期」语义 */
--plan-primary: #6366F1;
--plan-primary-soft: #EEF2FF;

/* 计划状态 */
--plan-status-active: var(--plan-primary);
--plan-status-done: var(--success);
--plan-status-abandoned: var(--text-3);

/* MISSED 软调色 */
--plan-missed-soft: #FEF3C7;  /* 米黄底 */
--plan-missed-ink: #92400E;   /* 重描文字 */

/* 6 分类 */
--cat-study: #6366F1;     /* 学习 靛蓝 */
--cat-work: #0EA5E9;      /* 工作 天蓝 */
--cat-health: #10B981;    /* 健康 翠绿 */
--cat-life: #F59E0B;      /* 生活 琥珀 */
--cat-finance: #8B5CF6;   /* 财务 紫 */
--cat-other: #6B7280;     /* 其他 灰 */
/* 注：--cat-study 与 --plan-primary 同色 #6366F1，学习分类在 plan 模块中可能与品牌色混淆。
   MVP 阶段可接受；v1.1 视觉精修时考虑为学习分类换色（如 #3B82F6 蓝）。*/

/* 进度环 */
--progress-track: #E5E7EB;
--progress-fill: var(--plan-primary);
--progress-done: var(--success);
--progress-missed: var(--warning);

/* 剩余天数徽章梯度 */
--day-fresh: var(--plan-primary);    /* >14d */
--day-soon: var(--warning);          /* 7-14d */
--day-urgent: var(--danger);         /* <7d */
--day-overdue: var(--plan-missed-ink); /* 过期 */

/* emoji 兼容性：
 * - 🟢🟡🔴 等色块 emoji 在 Windows 默认字体（Segoe UI Emoji）下显示一致，无需 fallback
 * - 📍🎯📋📝🏠👤 等图形 emoji 各系统（Windows / macOS / iOS / Android）一致
 * - MVP 阶段接受平台差异；v1.1 视觉精修时若发现不一致可统一为 SVG 图标
 */
```

### 3.3 字号与行高

| 用途 | Token | 桌面 / 移动 |
|---|---|---|
| 卡片大标题 | `fs-display` | 24px / 20px |
| 进度环中心数字 | `fs-h2` | 32px / 24px |
| 里程碑行 | `fs-body` 14px | 行高 48px（拖拽热区） |

---

## 4. 组件清单（11 组件）

| # | 组件 | 说明 | 引用视图 |
|---|---|---|---|
| 1 | **PlanCard** | 核心卡片（瀑布 / 看板 / 抽屉引用） | V1, V3, V7 |
| 2 | **ProgressRing** | SVG 圆环进度，中心可显示数字 | PlanCard |
| 3 | **StatusBar** | 卡片顶部 4px 状态条 | PlanCard |
| 4 | **CategoryTag** | 分类 emoji + 名，可选纯色块 | PlanCard, V7 列头 |
| 5 | **DayBadge** | 剩余天数徽章，4 种色 | PlanCard |
| 6 | **LastUpdated** | 「上次更新 N 天前」，>14d warning | PlanCard |
| 7 | **MilestoneRow** | 里程碑行：checkbox + 拖拽手柄 + 标题 + deadline + 状态 | V3a |
| 8 | **MilestoneLongPressDrag** | 长按 300ms 拖拽控制器，自动保存 | V3a |
| 9 | **TaskPickerSheet** | 关联任务选择器（搜索 + 首组 + 多选） | V5 |
| 10 | **Drawer** | 右侧抽屉容器（桌面右侧 / 移动端全屏） | V3 |
| 11 | **FilterBar** | 顶部筛选（状态 / 分类 / 排序） | V1, V7, V8 |

每个组件的标准属性：props、states（default / hover / active / focus / disabled / loading / empty / error）、accessibility（ARIA、键盘焦点）。

---

## 5. 交互流（8 条主线）

### F1 创建计划

```
「+ 计划」FAB
  ↓
V2 PlanSheet 弹出（半屏）
  ↓
填写表单（标题 / 起止 必填，描述 / 分类 / 状态 选填）
  ↓
保存
  ↓
V1 新卡片乐观插入 + Toast「已创建」
```

埋点：`plan_create_p95`

### F2 切换视图模式

```
V1 顶部 Sub-Tab「瀑布 / 时间轴 / 看板」点击
  ↓
切换对应视图
  ↓
筛选 / 排序状态保留
```

埋点：`plan_view_change`

### F3 编辑计划

```
V1 卡片点击 → V3 Drawer 打开
  ↓
顶部「✎ 编辑」按钮
  ↓
V2 Sheet 预填当前数据
  ↓
保存 → Drawer 实时刷新
```

### F4 创建里程碑

```
V3 Drawer → 里程碑 Tab → 「+ 里程碑」按钮
  ↓
V4 MilestoneSheet 弹出
  ↓
填写（标题 / 计划完成日 必填）
  ↓
保存 → 列表末尾乐观插入
```

### F5 里程碑拖拽排序

```
V3 里程碑 Tab → 长按 MilestoneRow（300ms）
  ↓
该行半透明 + 缩放反馈（transform: scale(0.96)）
  ↓
拖动 → 其他行显示插入位竖线
  ↓
松开 → 自动保存顺序（无「保存」按钮）
  ↓
Toast「顺序已保存」
```

> 长按阈值 **300ms**：与 iOS Force Touch 短按、Telegram 拖拽对齐
> - 短按（<300ms）→ 视为文本选中
> - 长按（≥300ms）→ 触发拖拽

**边界**：
- 仅在当前 Plan 内拖拽（**不跨 Plan**；跨 Plan 拖拽 = no-op + 短暂抖动反馈）
- 拖回原位置 = 无操作，**不触发保存**（order 字段无变化）
- MVP 阶段无网络层，拖拽即时落本地状态（localStorage 暂存 + 同步刷新 UI）

埋点：`milestone_drag`

### F6 关联任务

```
V3 里程碑 Tab → MilestoneRow 右侧「🔗」图标
  ↓
V5 TaskPickerSheet 弹出
  ├─ 顶部搜索栏
  ├─ 首组：今日 / 本周
  └─ 全部任务列表（可滚动）
  ↓
多选任务
  ↓
底部「确认 N 项」
  ↓
Sheet 关闭 → MilestoneRow 出现「已关联 N 项」标记
```

埋点：`task_link`

### F7 MISSED 调整

```
过期里程碑 → 软调色背景 + 右侧「📍 调整」按钮
  ↓
点击 → V6 MISSED AdjustSheet
  ├─ 调整 deadline（弹出日期选择 → 重置为 PENDING）
  ├─ 拆为子里程碑（V4 Sheet 预填父信息）
  └─ 跳过（标记 ABANDONED，从进度计算中移除）
  ↓
确认 → 状态变更 + Toast
```

埋点：`milestone_missed_adjust`

### F8 查看回收站

```
👤 Me → 设置 → 回收站
  ↓
选「计划」Tab
  ↓
列表展示已软删除计划（含剩余恢复天数倒计时）
  ↓
选「恢复」/「永久删除」
  ↓
二次确认（永久删除需输入「确认删除」）
```

---

## 6. 状态机

### 6.1 计划状态机（Plan）

```
   [创建]
     ↓
  ACTIVE ←──────→ DONE
     ↓              ↓
  ABANDONED ←──────┘
     ↓
  [软删除]
     ↓
  [回收站 30d]
     ↓
  [永久删除]
```

**转移规则**：

| From | To | 触发条件 |
|---|---|---|
| ACTIVE | DONE | 所有里程碑 DONE 或用户手动标记 |
| ACTIVE | ABANDONED | 用户主动放弃 |
| DONE | ABANDONED | 已完成计划用户放弃维护（如用日记代替，不再追踪 plan） |
| DONE | ACTIVE | 「重新启动」（v1.1+，MVP 不实现） |
| ABANDONED | ACTIVE | 「重启」 |
| 任意 | [软删除] | 用户点击「删除」（30 天回收站） |
| [软删除] | 恢复前状态 | 30 天内从回收站恢复 |
| [软删除] | [永久删除] | 30 天后自动 / 用户主动 |

### 6.2 里程碑状态机（Milestone）

```
   [创建]
     ↓
  PENDING ──────→ DONE ──→ [不可逆:防 PRD §8 风险 3「循环触发」]
     ↓
  MISSED ──→ PENDING（用户调整 deadline）
     ↓
  [软删除] → [永久删除]
```

**转移规则**：

| From | To | 触发条件 |
|---|---|---|
| PENDING | DONE | 仅用户手动（关联任务全部完成时显示「可标记完成」轻引导文案，由用户主动确认，对齐决策 7「不自动」）；入口为 V3a MilestoneRow checkbox |
| PENDING | MISSED | deadline 已过 24h 仍未 DONE（每日 21:00 用户本地时区检查；<br>**MVP Mock**：使用客户端时区，浏览器关闭则跳过当日检查；用户在 deadline 当天 21:00-23:59 之间完成 = 视为按时完成，不触发 MISSED；跨时区出差/旅行由用户手动调整 deadline） |
| DONE | PENDING | **不允许**（防 PRD §8 风险 3「循环触发」）；双层防御：前端状态机阻止 + 后端 API 强校验（与 §7.7 一致） |
| MISSED | PENDING | 用户「📍 调整」并选择「调整 deadline」（新 deadline > today） |
| MISSED | DONE | 用户手动完成；入口为 V3a MilestoneRow checkbox（与 PENDING → DONE 同入口；§5 F7 三选菜单仅处理「过期未完成」场景） |
| 任意 | [软删除] | 用户删除 |

### 6.3 视图加载状态

| 视图 | loading | empty | error |
|---|---|---|---|
| V1 | 骨架卡片（3 张） | 🌱 EmptyState | Snackbar「加载失败」+「重试」 |
| V3 Drawer | 顶部骨架 + Tab 骨架 | — | Toast「详情加载失败」 |
| V7 / V8 | 同 V1 | 分类型 empty（图标不同） | 同 V1 |

---

## 7. 验收标准

### 7.1 PRD §1 SMART 对齐

| 指标 | 目标 | 验证方式 |
|---|---|---|
| 创建计划到列表出现 | P95 < 2.0s | 埋点 `plan_create_p95` |
| 30 天内 ≥ 1 计划用户占比 | ≥ 40% | 行为日志聚合 |
| 活跃计划 30 天仍在跟踪 | ≥ 60% | `last_updated_at < 30d` 占比 |
| 里程碑完成率 | ≥ 50% 计划有里程碑完成 | 服务端统计 |
| 关联任务使用率 | ≥ 30% 里程碑关联任务 | 服务端统计 |
| 推送响应率 | ≥ 30% | Push 回执 |

### 7.2 US-PLAN-01 年度目标可视化

- **AC-1**：V1 PlanHub 显示标题 / 分类 / 进度环 / 剩余天数 / 上次更新时间
- **AC-2**：必填仅标题和起止时间
- **AC-3**：创建后立即出现在列表（乐观插入）

### 7.3 US-PLAN-02 里程碑拆解

- **AC-1**：V4 MilestoneSheet 标题 + 计划完成日 + 排序（默认按 deadline 升序）
- **AC-2**：长按 300ms 触发拖拽，松手自动保存顺序（无「保存」按钮）
- **AC-3**：未完成正序排前，完成置灰

### 7.4 US-PLAN-03 关联任务

- **AC-1**：V5 TaskPickerSheet 顶部搜索 + 今日 / 本周首组 + 全部任务列表
- **AC-2**：多选，底部「确认 N 项」
- **AC-3**：产品行为（决策 7）：全部关联任务完成 → 显示「✓ 可标记完成」轻引导 → 用户主动确认
- **AC-4**：MVP 原型说明：因 mock 无后端，演示时点击关联任务 checkbox 即触发里程碑 DONE（仅原型）

### 7.5 US-PLAN-04 临近提醒

- **AC-1**：里程碑 deadline 前 2 天 09:00 用户本地时区 Web Push（MVP Mock：显示 Toast「📌 里程碑临近」，与 §6.2 时区规则一致）
- **AC-2**：点击 Push 跳转里程碑（V3 Drawer 自动定位）

### 7.6 KANO / RICE 关键功能

| 需求 | KANO | 决策 |
|---|---|---|
| 进度条 | MUST | ✅ MVP |
| 倒计时 | MUST | ✅ MVP |
| 关联任务 | SHOULD | ✅ MVP |
| 看板 | SHOULD（设计稿全量；MVP 工程实现阶段 8，可简化版） | ✅ MVP UI 完整设计 |

### 7.7 风险应对（PRD §8）

| 风险 | 应对 |
|---|---|
| 「14 天未更新」 | `LastUpdated` 组件 warning 色 |
| MISSED 不强制负面 | 软调色 + 「📍 调整」（非「⚠ 警告」） |
| 循环触发 | 后端强校验（MVP 用注释说明） |
| ACTIVE 计划过多 | 排序「按剩余天数」，DONE / ABANDONED 默认折叠 |

### 7.8 可访问性

- **ARIA**：`PlanCard role=article` / `MilestoneRow role=listitem` / `Drawer role=dialog aria-modal=true`
- **键盘**：Drawer Tab 循环 / 里程碑行 ↑↓ + Enter / 列表项 Esc 关闭 Drawer
- **焦点环**：Tab 焦点环 2px primary 色，`:focus-visible` 实现

### 7.9 性能

- V1 首屏 ≤ 30 卡片时无虚拟滚动，瀑布布局 CSS `columns` 实现
- Drawer 打开 < 200ms（本地 mock 数据）
- 圆环进度 SVG 路径缓存，仅数字变化时重绘

**性能基准（测试条件：Chrome 桌面端 1440×900 + iPhone 13 Safari，本地 mock 数据）：**

| 指标 | 目标 | 测量方法 |
|---|---|---|
| V1 30 卡片首屏 Time to Interactive | < 500ms | Lighthouse / Performance API `performance.timing` |
| Drawer 打开响应 | P95 < 200ms（点击 → 内容可见） | 自定义计时（`performance.now()` 包裹点击处理） |
| 圆环进度数字变化 | 仅重绘 SVG `<text>`，不重建 `<path>` | DevTools 重绘区域高亮（paint flashing） |
| 拖拽自动保存 | < 100ms（拖拽松手 → UI 反馈 + localStorage 写入） | 自定义计时 |
| Sub-Tab 切换 | < 50ms（无网络请求，本地状态切换） | 自定义计时 |

---

## 8. 数据模型（对接 PRD §3）

```ts
type PlanStatus = 'ACTIVE' | 'DONE' | 'ABANDONED'
type PlanCategory = 'study' | 'work' | 'health' | 'life' | 'finance' | 'other'
type MilestoneStatus = 'PENDING' | 'DONE' | 'MISSED'

interface Plan {
  id: string                    // UUID
  title: string                 // 必填，≤40 字
  description?: string          // 选填，≤500 字
  category: PlanCategory        // 默认 'other'
  status: PlanStatus            // 默认 'ACTIVE'
  startAt: string               // ISO date
  endAt: string                 // ISO date
  createdAt: string
  updatedAt: string
  deletedAt?: string            // 软删除时间
}

interface Milestone {
  id: string
  planId: string
  title: string                 // 必填，≤30 字
  dueDate: string               // 必填，ISO date
  status: MilestoneStatus       // 默认 'PENDING'
  order: number                 // 0-based 排序
  linkedTaskIds: string[]       // 关联任务 ID 列表
  createdAt: string
  updatedAt: string
  completedAt?: string          // DONE 时间戳，用于 §7.1 完成率精确统计（与 updatedAt 区分：DONE 后编辑不刷新此字段）
  lastNotifiedAt?: string       // Web Push 最近通知时间，避免重复推送（§7.5 AC-1）
  deletedAt?: string
}
```

### Mock 数据（原型阶段）

```js
const PLANS = [
  {
    id: 'p1',
    title: '学完一本英文书',
    description: '2026 下半年读完一本英文原版书并输出读书笔记',
    category: 'study',
    status: 'ACTIVE',
    startAt: '2026-07-01',
    endAt: '2026-12-31',
    createdAt: '2026-07-15T10:00:00Z',
    updatedAt: '2026-07-25T14:30:00Z',
  },
  {
    id: 'p2',
    title: '减重 5kg',
    description: '通过控制饮食 + 每周 3 次有氧运动',
    category: 'health',
    status: 'ACTIVE',
    startAt: '2026-06-01',
    endAt: '2026-12-31',
    createdAt: '2026-06-01T08:00:00Z',
    updatedAt: '2026-07-20T19:15:00Z',
  },
  { id: 'p3', title: '新房装修', category: 'life', status: 'ACTIVE', /* ... */ },
  { id: 'p4', title: 'CFA Level 3 备考', category: 'study', status: 'ACTIVE', /* ... */ },
  { id: 'p5', title: '读 24 本书（年度）', category: 'other', status: 'DONE', /* ... */ },
  { id: 'p6', title: '学钢琴', category: 'health', status: 'ABANDONED', /* ... */ },
]

const MILESTONES = [
  {
    id: 'm1',
    planId: 'p1',
    title: '读完前 5 章',
    dueDate: '2026-07-31',
    status: 'DONE',
    order: 0,
    linkedTaskIds: [],
    createdAt: '2026-07-15T10:00:00Z',
    updatedAt: '2026-07-25T14:30:00Z',
  },
  {
    id: 'm2',
    planId: 'p1',
    title: '完成第 6-10 章',
    dueDate: '2026-08-31',
    status: 'PENDING',
    order: 1,
    linkedTaskIds: ['t1'],
    createdAt: '2026-07-15T10:00:00Z',
    updatedAt: '2026-07-15T10:00:00Z',
  },
  // 每个 plan 配 3-5 条里程碑
]
```

---

## 9. 实现路线（单文件 HTML 原型分阶段）

| 阶段 | 内容 | 依赖 |
|---|---|---|
| 1 | 骨架 + Token（§3）+ V1 PlanHub 容器 + Sub-Tab 切换 | — |
| 2 | V1 瀑布模式（PlanCard + ProgressRing + StatusBar + DayBadge + LastUpdated） | 1 |
| 3 | V2 PlanSheet 创建表单 | 2 |
| 4 | V3 Drawer + V3a MilestoneList（读模式） | 2, 3 |
| 5 | V4 MilestoneSheet + 拖拽排序（300ms 自动保存） | 4 |
| 6 | V5 TaskPickerSheet 关联任务 | 5 |
| 7 | V6 MISSED AdjustSheet + 软调色 | 5 |
| 8 | V7 Kanban（6 列网格 CSS + 跨列拖动改分类 + 移动端横滑 + 圆点指示器） | 2 |
| 9 | V8 Timeline（±6 月，移动端纵向倒序） | 2 |
| 10 | FilterBar / EmptyState / Toast / 回收站入口 | 1-9 |
| 11 | ARIA + 键盘 + 性能验收 | 1-10 |

每阶段可独立 PR，原型在 `05-plan-ui-v1.html` 持续集成。

---

## 10. 验收清单（可直接对照 03-expense-ui）

- [ ] 8 视图齐全（V1–V8）
- [ ] 11 组件封装
- [ ] 三模式可切换（瀑布 / 时间轴 / 看板）
- [ ] 长按 300ms 拖拽 + 自动保存
- [ ] MISSED 软调色 + 「📍 调整」
- [ ] 关联任务 Sheet + 多选 + 首组
- [ ] 完整可视化卡片（标题 / 分类 / 状态 / 进度环 / 剩余天数 / 上次更新时间）
- [ ] Tab 3 = 「🎯 计划」
- [ ] 回收站入口（👤 Me → 设置）
- [ ] ARIA + 键盘可达
- [ ] 性能：Drawer 打开 < 200ms，瀑布 ≤ 30 卡片无虚拟滚动

---

## 11. 跨模块影响

| 模块 | 影响 |
|---|---|
| 01-task-ui | 任务被「关联」时需提供 ID + 状态订阅（`task_done` 事件）；<br>**MVP 方案 A**：localStorage 事件 + EventEmitter，单文件原型内可演示 |
| 03-expense-ui | 回收站入口共用，需在「👤 Me」页加 Tab 切分类 |
| 06-ai（规划中） | Tab 顺序需遵循 🏠📋🎯📝👤 |
| Web Push | VAPID 基础设施复用 03-expense-ui |

---

## 12. 待确认事项

- [x] PRD §6「30 天回收站」是否需要支持「批量恢复」？ → **已决策：MVP 单条恢复**，与 03-expense-ui 一致
- [x] PRD §3 PLAN-005「计划状态」MVP 是否包含 DONE / ABANDONED？ → **已决策：MVP 包含三种状态**（§6.1 Plan 状态机已定义）
- [ ] PRD §7 KPI「模块 NPS ≥ 35」在 MVP 是否纳入？（建议 v1.1 调研）

---

*文档版本：v1.0-design-draft*
*下一步：移交 writing-plans 生成实施计划*