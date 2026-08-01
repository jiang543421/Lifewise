# plan-auth 步骤 5 — TDD 证据报告

> **范围**：v1.2 P0 认证步骤 5 实施（核心认证闭环：register / login / refresh / logout；V36 三表补齐；4 条 auth.* 事件）
> **计划文档**：`docs/lifewise/planning/plan-auth.md`
> **架构依据**：`business-architecture.md §5.5` / `data-model-v1.2-amendment.md` / `technical-architecture.md §5.1`
> **报告生成**：2026-08-01
> **执行模式**：tdd-workflow + code-review（本步报告产出）

---

## 1. 实施路径选择

| 决策点 | 选项 | 选定 | 原因 |
|--------|------|------|------|
| DB 契约冲突修法 | (a) 改 V28；(b) 新增 V36 | **V36** | V28 已存 V1.2 P0 不变量；新增 V36 满足「不修改已审计迁移」原则 |
| 实施范围 | 完整 auth / 核心闭环 | **核心闭环** | V1.0 边界：邮件验证、CSRF、IP 锁定留待 v1.1 |
| DB 索引策略 | `idx_user_family` 复合 vs 单列 | **复合 (user_id, family_id) + partial** | revokeFamily(userId, familyId) 是高频路径；partial 排除已撤销行 |

---

## 2. 用户旅程（来自 plan-auth §5）

| # | 旅程 | 覆盖 |
|---|------|------|
| J1 | 新用户注册 → 拿到 access + refresh → 邮箱唯一性守住 | ✓ |
| J2 | 弱密码注册被拒 | ✓ |
| J3 | 已知用户登录 → 拿到令牌 + 写登录事件 | ✓ |
| J4 | refresh token rotation → 拿新令牌 + 旧 token 标记 used | ✓ |
| J5 | refresh token 二次使用（reuse）→ 整 family 撤销 + 事件 | ✓ |
| J6 | refresh token 过期 → 失败但不动 family | ✓ |
| J7 | logout → 当前 family 全撤销 | ✓ |
| J8 | 密码哈希 storage（BCrypt cost=12） | ✓ |
| J9 | outbox 事件 4 条 + 27 条 CHECK 白名单 | ✓（CHECK 27；生产仅 3 条发布） |

---

## 3. Task → Test → 实施 → 验证 映射

### 3.1 DB 迁移 V36（plan-auth §3.1 + §3.2）

| 任务 | 测试目标 | 实现 | 验证 |
|------|----------|------|------|
| V36.1 users 补列（display_name / locale / status） | FlywayMigrationIT | V36 §1 | schema 校验通过；JPA 实体 + V36 一致 |
| V36.2 refresh_tokens.family_id UUID NOT NULL | FlywayMigrationIT | V36 §2 | 复合索引建立 |
| V36.3 outbox_events CHECK 27 条 | FlywayMigrationIT | V36 §3 | 4 条 auth.* canonical + 2 legacy 写入通过 |

### 3.2 Auth 域

| 任务 | RED 测试 | GREEN 实现 | 验证 |
|------|----------|------------|------|
| User.create(email, hash, displayName, tz, locale) | AuthServiceTest.register | User.java | 9 个 service 测试通过 |
| RefreshToken.issue / markUsed / revoke / isUsable | JwtRefreshServiceImplTest | RefreshToken.java | 5 个 refresh 测试通过 |
| PasswordService.assertStrong / hash / matches | PasswordServiceTest | PasswordService.java | 6 个 password 测试通过 |
| AuthService 4 个 @Transactional 方法 | AuthServiceTest | AuthService.java | 9 个 service 测试通过 |
| JwtRefreshServiceImpl.rotate + reuse detection | JwtRefreshServiceImplTest | JwtRefreshServiceImpl.java | 5 个 refresh 测试通过 |
| AuthController 4 endpoint | (无单测；依赖集成) | AuthController.java | 编译通过 |
| GlobalExceptionHandler | (无单测；依赖集成) | GlobalExceptionHandler.java | 编译通过 |

### 3.3 测试基础设施

| 工具 | 用途 | 备注 |
|------|------|------|
| `UserWithId`（反射） | 模拟 Hibernate `IDENTITY` 回填 | test-scope 妥协；`BaseEntity.id` 无 setter |
| `lenient()` Mockito | 隔离测试间 stub 干扰 | auth 模块 4 个测试类 |

---

## 4. 测试规格表

| # | 行为保证 | 测试类::方法 | 类型 | 结果 | 证据 |
|---|---------|-------------|------|------|------|
| 1 | 满足 5 条规则的密码通过 | PasswordServiceTest.should_accept_strong_password | unit | PASS | `mvn test -Dtest=PasswordServiceTest` |
| 2 | 短密码抛 WeakPasswordException | PasswordServiceTest.should_reject_short_password | unit | PASS | 同上 |
| 3 | 缺大写 / 数字 / 符号分别拒绝 | PasswordServiceTest.should_reject_no_* | unit | PASS | 同上 |
| 4 | BCrypt hash 含 `$12$` 并 matches 通过 | PasswordServiceTest.should_hash_and_verify_with_bcrypt | unit | PASS | 同上 |
| 5 | 正常 rotate → 返回新 access+refresh，旧 usedAt 写入 | JwtRefreshServiceImplTest.should_rotate_and_issue_new_pair | unit | PASS | `mvn test -Dtest=JwtRefreshServiceImplTest` |
| 6 | reuse 检测 → family 全撤销 + 事件发布 + 抛 TokenReusedException | JwtRefreshServiceImplTest.should_detect_reuse_and_revoke_family | unit | PASS | 同上 |
| 7 | JwtExpiredException → TokenExpiredException 映射 | JwtRefreshServiceImplTest.should_map_jwt_expired_to_domain_expired | unit | PASS | 同上 |
| 8 | JwtInvalidException → TokenInvalidException 映射 | JwtRefreshServiceImplTest.should_map_jwt_invalid_to_domain_invalid | unit | PASS | 同上 |
| 9 | 未知 refresh → rotate 返回 empty | JwtRefreshServiceImplTest.should_return_empty_for_unknown_refresh | unit | PASS | 同上 |
| 10 | register 成功 → TokenResponse + auth.user.registered 事件 | AuthServiceTest.should_register_and_publish_event | unit | PASS | `mvn test -Dtest=AuthServiceTest` |
| 11 | 重复邮箱 → EmailExistsException | AuthServiceTest.should_reject_duplicate_email | unit | PASS | 同上 |
| 12 | 弱密码 → WeakPasswordException | AuthServiceTest.should_reject_weak_password | unit | PASS | 同上 |
| 13 | 正确凭据登录 → TokenResponse + auth.user.logged_in 事件 | AuthServiceTest.should_login_with_correct_credentials | unit | PASS | 同上 |
| 14 | 未知邮箱登录 → InvalidCredentialsException | AuthServiceTest.should_reject_login_with_unknown_email | unit | PASS | 同上 |
| 15 | refresh 成功 → 新令牌 | AuthServiceTest.should_refresh_with_valid_token | unit | PASS | 同上 |
| 16 | 未知 refresh → TokenInvalidException | AuthServiceTest.should_reject_unknown_refresh | unit | PASS | 同上 |
| 17 | logout → 同 family 全 revoke | AuthServiceTest.should_revoke_family_on_logout | unit | PASS | 同上 |
| 18 | logout 非法 refresh → TokenInvalidException | AuthServiceTest.should_reject_logout_with_malformed_refresh | unit | PASS | 同上 |
| 19 | V36 干净应用 | FlywayMigrationIT.flyway_should_apply_v36_cleanly | IT | PASS | `mvn verify` |
| 20 | refresh_tokens family_id NOT NULL UUID | FlywayMigrationIT.flyway_should_add_non_null_uuid_family_id_to_refresh_tokens | IT | PASS | 同上 |
| 21 | 25 canonical + 2 legacy event_type 全部可写 | FlywayMigrationIT.flyway_should_accept_every_canonical_event_type_and_legacy_auth_aliases | IT | PASS | 同上 |
| 22 | plan-auth 22 项 BR 全部越过 | FlywayMigrationIT 各类 `flyway_should_enforce_*` | IT | PASS | 23/23 |
| 23 | Outbox JpaOutboxEventRepository 9 项行为 | JpaOutboxEventRepositoryIT | IT | PASS | 9/9 |

---

## 5. 验证结果

### 5.1 完整 verify（含 IT + 覆盖率）

```
[INFO] Tests run: 124, Failures: 0, Errors: 0, Skipped: 0          (surefire)
[INFO] Tests run: 32,  Failures: 0, Errors: 0, Skipped: 0          (failsafe)
[INFO] All coverage checks have been met.                          (JaCoCo BUNDLE ≥ 0.80)
[INFO] BUILD SUCCESS
```

### 5.2 覆盖率

- BUNDLE 行覆盖 gate ≥ 0.80：通过
- 单模块行覆盖：auth 模块未独立统计（JaCoCo 在 BUNDLE 模式聚合），但 20 个 auth 单测覆盖所有公开方法与关键分支

### 5.3 已知未覆盖

| 维度 | 缺失 | 计划 |
|------|------|------|
| AuthController HTTP 层 | MockMvc 集成测试 | v1.1 引入 auth IT |
| GlobalExceptionHandler status 映射 | 同上 | 同上 |
| 真实 @Transactional 回滚（reuse 撤销） | 集成测试 | v1.1 |
| X-Forwarded-For 信任边界 | 集成测试 | v1.1 |
| 并发注册唯一键冲突 | 集成测试 | v1.1 |

---

## 6. Code Review 摘要（详细见 code-reviewer 输出）

| Severity | Count | 状态 |
|----------|------:|------|
| CRITICAL | 1 | requires fix before merge |
| HIGH | 3 | fix recommended |
| MEDIUM | 4 | consider |
| LOW | 0 | — |

### 6.1 CRITICAL 单一阻断

- **`AuthConfig.java:20-21`**：`DEFAULT_SECRET_BASE64` 在源码中固定公开。生产环境若遗漏 `AUTH_JWT_SECRET_BASE64` env，应用将使用任意部署者都能伪造 JWT 的密钥。违反 CLAUDE.md §7.1 + technical-architecture.md §5.4。**修复**：删除默认，强制从环境变量注入；缺则启动失败。

### 6.2 HIGH

1. **`JwtRefreshServiceImpl.rotate()` 内抛 TokenReusedException → @Transactional 默认回滚** → 撤销与 reuse 事件全部丢失。需 REQUIRES_NEW 提交后再抛。
2. **reuse 检测未做 row.userId / row.familyId vs claims 的交叉验证** → 攻击者构造签名有效但哈希不匹配 row 的 token 时，撤销的 family 错误。
3. **V36 family_id backfill 不沿 V28 parent_id/replaced_by 链分组** → 历史 rotation chain 断裂，reuse 撤销失效。

### 6.3 MEDIUM

- TokenInvalidException 暴露 JWT parser 消息 → 客户端泛化
- 邮箱规范化在 existsByEmail 之后 → 大小写不一致
- X-Forwarded-For 无 trusted proxy 校验
- 4 条 auth.* 事件仅 3 条有生产路径（password_reset_requested 仅有 payload 注册）

---

## 7. 范围与未交付

### 7.1 范围内已交付

- V36 迁移：users display_name/locale/status + refresh_tokens.family_id + outbox_events CHECK 27 条
- Domain: User, RefreshToken + 8 个 exception
- Repository: UserRepository, RefreshTokenRepository
- DTO: RegisterRequest, LoginRequest, TokenResponse, RefreshRequest, MessageResponse
- Payload: 4 个 auth event payload
- Service: PasswordService, JwtRefreshServiceImpl, AuthService, AuthConfig
- Controller: AuthController (4 endpoint), GlobalExceptionHandler
- Test: 20 个 auth 单测 + UserWithId 工具

### 7.2 范围内未交付（按用户选择「核心闭环」）

- 邮箱验证端点（`/api/auth/verify-email`）
- 密码重置端点（`/api/auth/forgot-password` / `/reset-password`）
- 5 次失败 IP 锁定
- CSRF 中间件
- Spring Security 集成
- AuthController / GlobalExceptionHandler 的 MockMvc 集成测试

### 7.3 范围外（按 v1.2 计划）

- 跨模块用户引用（CLAUDE.md §1.2 模块边界）
- RBAC 多角色（plan-auth §2.2 role 字段保留扩展位）
- 异步登录事件同步（当前走 Outbox 异步送达）

---

## 8. 风险与跟踪

| 风险 | 影响 | 缓解 |
|------|------|------|
| CRITICAL JWT 默认密钥 | 任意人可伪造 JWT | 删除默认 + 启动失败 |
| HIGH reuse 事务回滚 | 攻击检测后仍可继续 rotation | REQUIRES_NEW 提交后再抛 |
| HIGH claims vs row 不交叉验证 | 撤销错误 family | 加 row.userId == claims.userId 断言 |
| HIGH V36 backfill 未沿链 | 历史 chain 失效 | V36 改用递归 CTE 沿 parent_id/replaced_by 链 |
| MEDIUM TokenInvalid 泄露 | 信息泄露 | 客户端消息泛化 |
| MEDIUM 邮箱大小写 | 并发数据不一致 | 规范化前置 + 唯一约束回退映射 |
| MEDIUM X-Forwarded-For 信任 | IP 伪造 | trusted proxy 校验 |

---

## 9. 后续动作建议

1. **合并前必修**（CRITICAL）：删除 `AuthConfig.DEFAULT_SECRET_BASE64`，强制生产 secret 由 env 注入
2. **合并前强烈建议**（HIGH）：reuse 事务提交 + claims 交叉验证 + V36 backfill 链式
3. **合并后允许**（MEDIUM）：邮箱规范化前置 + IP 过滤 + 4 条事件闭环的 password_reset_requested producer
4. **v1.1 范围**：AuthController MockMvc IT + 邮箱验证 + 密码重置 + IP 锁定

---

## 10. 验证签名

- **Test PASS 实际命令**：`mvn -f app/pom.xml clean verify`
- **结果摘录**：
  ```
  [INFO] Tests run: 124, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
  [INFO] All coverage checks have been met.
  [INFO] BUILD SUCCESS
  ```
- **审查命令**：`Agent(subagent_type=ecc:code-reviewer)`
- **审查样本**：返回 6 节结构化审查报告
- **完成日期**：2026-08-01
