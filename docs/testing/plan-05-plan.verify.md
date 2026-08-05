# plan-05-plan 模块测试报告 (Verify)

> 日期: 2026-08-05
> 范围: plan 模块（com.lifewise.plan.**）
> 命令: `mvn verify -Dtest='com.lifewise.plan.**' -DskipITs=true -DfailIfNoTests=false`
> 构建状态: BUILD SUCCESS / All coverage checks have been met.

---

## 1. 测试范围

| 类型     | 状态              | 备注                                                                 |
|----------|-------------------|----------------------------------------------------------------------|
| 单元测试 | ✅ 已跑           | 66 tests, 0 failures                                                 |
| 集成测试 | ⚠️ plan 模块无 IT | 全栈 IT 受 StatsController bean 冲突阻断（非 plan 模块问题）        |
| E2E 测试 | ❌ 未引入         | 项目未引入 Playwright（CLAUDE.md §6.2 “若引入”）                     |

---

## 2. 单元测试结果（66/66 通过）

| 测试类                                            | Tests | Failures | Errors | Skipped |
|---------------------------------------------------|------:|---------:|-------:|--------:|
| MilestoneControllerWebMvcTest                     |     6 |        0 |      0 |       0 |
| PlanControllerWebMvcTest                          |     7 |        0 |      0 |       0 |
| ProgressControllerWebMvcTest                      |     2 |        0 |      0 |       0 |
| TaskChangedConsumerTest                           |     2 |        0 |      0 |       0 |
| TaskCompletedConsumerTest                         |     1 |        0 |      0 |       0 |
| TaskReopenedConsumerTest                          |     1 |        0 |      0 |       0 |
| PlanReadPortAdapterTest                           |     4 |        0 |      0 |       0 |
| LastActivityRefresherTest                         |     3 |        0 |      0 |       0 |
| MilestoneServiceTest                              |    11 |        0 |      0 |       0 |
| MilestoneTaskLinkServiceTest                      |     6 |        0 |      0 |       0 |
| MissedMilestoneJobTest                            |     2 |        0 |      0 |       0 |
| PlanServiceTest                                   |     9 |        0 |      0 |       0 |
| PlanStaleNotifyJobTest                            |     1 |        0 |      0 |       0 |
| ProgressEvaluatorTest                             |     4 |        0 |      0 |       0 |
| TaskReadPortFacadeTest                            |     7 |        0 |      0 |       0 |
| **合计**                                          | **66**|   **0**  |  **0** |   **0** |

**通过率 100%（66/66），0 failures, 0 errors, 0 skipped。BUILD SUCCESS。**

---

## 3. 覆盖率统计

### 3.1 BUNDLE 级（CLAUDE.md §6.1 阈值 ≥ 80% 行覆盖）

| 度量       | 数值                                       |
|------------|--------------------------------------------|
| 行覆盖     | **81%**（751 missed / 4,038 total）        |
| 指令覆盖   | 81%（3,513 missed / 19,395 total）         |
| 分支覆盖   | 58%（535 missed / 1,296 total）            |

✅ **通过 jacoco 80% 行覆盖 gate**（“All coverage checks have been met”）

### 3.2 plan 模块 9 个 package 行覆盖明细

| 包                                          | 行覆盖       | missed / total |
|---------------------------------------------|--------------|----------------|
| com.lifewise.plan.controller                | **96.0%**    | 1 / 25         |
| com.lifewise.plan.domain                    | 79.1%        | 33 / 158       |
| com.lifewise.plan.dto                       | 96.2%        | 1 / 26         |
| com.lifewise.plan.event                     | 74.5%        | 13 / 51        |
| com.lifewise.plan.event.payload             | **100%**     | 0 / 44         |
| com.lifewise.plan.port.out                  | 96.4%        | 1 / 28         |
| com.lifewise.plan.service                   | **96.8%**    | 7 / 217        |
| com.lifewise.plan.service.notification      | **0%** ⚠️   | 5 / 5          |
| com.lifewise.plan.web                       | **39.7%** ⚠️ | 41 / 68        |
| **plan 模块加权**                           | **≈ 83.6%**  | **102 / 622**  |

✅ plan 模块加权行覆盖 83.6% ≥ 80% 阈值

---

## 4. 已知覆盖率 gap（plan 模块范围内）

### 4.1 P1 — 显著 gap（建议下一轮补）

**plan.service.notification（0% / 5 lines）**
- `NoopPlanNotifier`：v1.0 推送兜底实现，无生产调用方（CLAUDE.md §7.6 隐私约束，本地推送后续接入）
- 风险等级：低（兜底类不需要逻辑覆盖）
- 处置：留到 v1.1 接入真实推送时一并补测试

**plan.web（39.7% / 41 missed lines）**
- 包含 `PlanGlobalExceptionHandler` + `PlanWebMvcConfig` + `CurrentUserArgumentResolver`
- 现状：controller WebMvc 测试触发异常路径 → 隐式覆盖部分 handler
- 风险等级：中（happy path 间接覆盖，但精确映射未测）
- 处置：建议下一轮补 `@WebMvcTest(PlanGlobalExceptionHandler.class)` 单元覆盖 8 个异常 → 8 个 ErrorCode 映射

### 4.2 P2 — 边缘 gap（可不动）

**plan.domain（79.1% / 33 missed lines）**
- 主要是 JPA entity getter/setter 与 lifecycle hook（@PrePersist / @PreUpdate）
- 风险等级：低（样板代码）
- 处置：可通过 entity 测试覆盖 lifecycle hook（可选）

**plan.event（74.5% / 13 missed lines）**
- 主要是 `PlanEventConsumerConfig` 的 Bean wiring 代码
- 风险等级：低（配置代码）
- 处置：可不补（bean wiring 失败会让 Spring 启动报错，自然兜底）

---

## 5. 集成测试（IT）状态

### 5.1 plan 模块 IT

❌ **plan 模块无 IT 文件**（`app/src/test/java/com/lifewise/plan/**/*IT.java` 无结果）。

设计上 plan 模块**未引入 IT**：
- 所有外部依赖（plan ↔ task 跨模块读）通过 `PlanReadPortAdapter` / `TaskReadPortFacade` 接口隔离
- 单元测试用 Mockito 覆盖跨模块调用，无需起 Spring Context
- Flyway 真实数据库 schema 验证归到 `shared/integration` IT 体系

### 5.2 全栈 IT（SharedIntegrationContextTest）

⚠️ **当前 blocked**：4 个错误（expense.controller.StatsController 与 diet.controller.StatsController bean 名冲突）。

- 根因：两个 `@RestController` 类名相同 → Spring bean name 默认冲突（`ConflictingBeanDefinitionException`）
- 修复方向（独立 PR，非 plan-05 范围）：给两个 `@RestController` 加 `value=` 改名（`expenseStatsController` / `dietStatsController`）
- 按 CLAUDE.md §10 红线：**plan-05 不擅自修**，单独 issue 跟踪 `fix/expense-diet-controller-bean-name`

---

## 6. E2E 测试状态

### 6.1 项目 E2E 现状

❌ **项目未引入 E2E 框架**（无 Playwright、无 `*E2E.java` 文件）。

CLAUDE.md §6.2：“E2E 测试 | Playwright（若引入）” — 当前 v1.0 未引入。

### 6.2 plan 模块 E2E 缺口

缺失 6 模块主流程 E2E。plan 模块受影响路径：

- 创建 plan → 关联 milestone → 关联 task → task 完成后 progress 自动刷新
- plan 14 天阈值触发 stale 通知
- 跨模块事件（task.completed → plan 内部 LastActivityRefresher）

风险等级：中（业务正确性目前由单元测试 + 跨模块事件契约保证）。
处置：项目级决策（v1.0 是否引入 Playwright），不应 plan-05 单模块驱动。

---

## 7. 失败的测试及原因

**无失败测试**（66/66 全通过）。

---

## 8. 评审发现与覆盖率 gap 的对应

三 reviewer 综合 verdict 中的 HIGH 问题与覆盖率分布关联：

| HIGH # | 问题                                              | 覆盖率关联                                                              |
|--------|---------------------------------------------------|-------------------------------------------------------------------------|
| 3      | PlanService.update 不拒 CANCELLED                 | `plan.service` 96.8% 覆盖，但该路径未触发（缺测试）                     |
| 4      | ProgressEvaluator.completedTasks 全局计数         | `plan.service` 96.8% 覆盖，但**测试断言写错了**——Mockito 假数据未反映真实 plan scope |
| 5      | Job 缺 @Scheduled cron                            | 待核实（job 类未在 9 包覆盖率列表单独统计）                             |
| 10     | TaskChangedConsumer eventType 不匹配              | `plan.event` 74.5% 覆盖，部分 consumer method 未被调用                  |

**结论**：覆盖率达标 ≠ 行为正确。ProgressEvaluator #4 是典型——测试都过了，但断言本身写错。建议下一轮修复 HIGH #3 + #4 时同步补针对路径的测试。

---

## 9. 结论

| 项                              | 结果                                                                       |
|---------------------------------|----------------------------------------------------------------------------|
| 单元测试                        | ✅ 66/66 通过                                                              |
| 行覆盖率（BUNDLE 级）           | ✅ 81% ≥ 80% 阈值                                                          |
| 行覆盖率（plan 模块）           | ✅ 83.6% ≥ 80% 阈值                                                        |
| 集成测试（plan 范围内）         | ⚠️ 无 IT（设计如此，Mockito + 接口隔离覆盖跨模块）                        |
| 集成测试（全栈 SharedInfra IT） | ⚠️ StatsController 阻塞，非 plan 范围                                      |
| E2E 测试                        | ❌ 项目未引入 Playwright                                                    |
| 评审 HIGH 问题                  | ⚠️ 5 个待修（#1 #3 #4 #5 #10）                                             |
| **综合 Verdict**                | **PASS for plan module scope**，CONDITIONAL for repo-wide IT / E2E       |

### 9.1 plan-05 落地建议

1. **修复 5 个 HIGH 后 push**（#1 dead code + #3 CANCELLED guard + #4 progress scope + #5 job cron + #10 consumer wiring）
2. **plan.web 39.7% 留 TODO**，下一轮补 `@WebMvcTest(PlanGlobalExceptionHandler.class)` 8 个异常映射
3. **plan.service.notification 0% 不阻塞**，留到 v1.1 推送接入时一并补
4. **StatsController bean 冲突独立 PR 处理**（issue `fix/expense-diet-controller-bean-name`）
5. **E2E 项目级决策后再决定** plan 模块是否纳入 Playwright 覆盖（不在 plan-05 范围）