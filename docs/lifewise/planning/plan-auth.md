# plan-auth 实施方案

## 参考资料

- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §3.1 认证（JWT + Refresh Token）
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3.1 USER 实体
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) §0 主键 + V2 users / user_profiles
- [`docs/lifewise/designs/登录和注册/auth-design.md`](../designs/登录和注册/auth-design.md) — UI 设计契约
- `CLAUDE.md` §7.1 密钥 / §7.3 认证授权 / §7.4 速率限制 / §7.5 错误信息

## 参考目录

- backend：`app/src/main/java/com/lifewise/auth/`
  - `controller/` — AuthController / CsrfController / EmailController
  - `service/` — AuthService / JwtRefreshService / EmailService / PasswordService
  - `domain/` — User / RefreshToken / EmailVerification / PasswordReset
  - `repository/` — UserRepository / RefreshTokenRepository / EmailVerificationRepository / PasswordResetRepository
  - `dto/` — RegisterRequest / LoginRequest / TokenResponse / RefreshRequest
  - `event/` — UserRegistered / UserLoggedIn / PasswordResetRequested / TokenReuseDetected
- frontend：`docs/lifewise/designs/登录和注册/`
  - `login.html` — 登录页（邮箱 + 密码 + CSRF）
  - `register.html` — 注册页（含时区选择 + 同意条款）
  - `forgot-password.html` — 找回密码（输入邮箱）
  - `reset-password.html` — 重置密码（token + 新密码）

## 1. 模块边界 / 包结构

auth 模块是 6 业务模块**唯一的前置依赖**（必须先闭环才能开展业务）。

```
auth/
├── controller/
│   ├── AuthController.java             POST /api/auth/{register,login,refresh,logout}
│   ├── CsrfController.java             GET  /api/auth/csrf
│   ├── EmailController.java            POST /api/auth/{verify-email,forgot-password,reset-password}
│   └── dto/
│       ├── RegisterRequest.java        {email, password, timezone, locale}
│       ├── LoginRequest.java           {email, password}
│       ├── TokenResponse.java          {access_token, refresh_token, expires_in}
│       ├── RefreshRequest.java         {refresh_token}
│       └── MessageResponse.java        {message}
├── service/
│   ├── AuthService.java                注册 / 登录 / 登出
│   ├── JwtRefreshService.java          业务编排（调用 shared-infra JwtRefreshTokenService + 发布 `auth.token.reuse_detected` 事件 + 处理 password_reset 联动失效）
│   ├── PasswordService.java            BCrypt + 强度校验（zxcvbn ≥ 3）
│   ├── EmailService.java               邮件发送（注册验证 / 找回密码）
│   └── LoginAttemptService.java        5 次失败 → IP 锁定 15min（共享 ratelimit）
├── domain/
│   ├── User.java                       users 表实体
│   ├── RefreshToken.java               refresh_tokens 表实体
│   ├── EmailVerification.java          email_verifications 表实体
│   └── PasswordReset.java              password_resets 表实体
├── repository/
│   ├── UserRepository.java
│   ├── RefreshTokenRepository.java
│   ├── EmailVerificationRepository.java
│   └── PasswordResetRepository.java
└── event/
    ├── UserRegisteredEvent.java        payload: {user_id, email, occurred_at}
    ├── UserLoggedInEvent.java          payload: {user_id, ip, ua, occurred_at}
    ├── PasswordResetRequestedEvent.java payload: {user_id, email, occurred_at}
    └── TokenReuseDetectedEvent.java    payload: {user_id, family_id, ip, occurred_at}
```

## 2. API 契约

### 2.1 鉴权闭环 8 个端点

| Method | Path | Request | Response | 错误码 |
|---|---|---|---|---|
| POST | `/api/auth/register` | `{email, password, timezone, locale}` | `{access_token, refresh_token, expires_in: 900}` | `EMAIL_EXISTS` / `WEAK_PASSWORD` / `RATE_LIMITED` |
| POST | `/api/auth/login` | `{email, password}` | `{access_token, refresh_token, expires_in: 900}` | `INVALID_CREDENTIALS` / `USER_LOCKED` / `RATE_LIMITED` |
| POST | `/api/auth/refresh` | `{refresh_token}` | `{access_token, refresh_token, expires_in: 900}` | `TOKEN_INVALID` / `TOKEN_REUSED`（家族全失效） |
| POST | `/api/auth/logout` | `{refresh_token}` | `{message: "ok"}` | — |
| POST | `/api/auth/verify-email` | `{token}` | `{message: "ok"}` | `TOKEN_INVALID` / `TOKEN_EXPIRED` |
| POST | `/api/auth/forgot-password` | `{email}` | `{message: "ok"}`（恒定响应，防探测） | `RATE_LIMITED` |
| POST | `/api/auth/reset-password` | `{token, new_password}` | `{message: "ok"}` | `TOKEN_INVALID` / `TOKEN_EXPIRED` / `WEAK_PASSWORD` |
| GET | `/api/auth/csrf` | — | `{csrf_token}`（同时 Set-Cookie） | — |

### 2.2 JWT payload

```json
{
  "sub": 123,                // user_id
  "email": "u@x.com",
  "role": "USER",
  "iat": 1722323400,
  "exp": 1722324300,         // 15min
  "jti": "uuid-v4"           // 用于 revoke
}
```

### 2.3 Refresh Token 规则

- 长度：64 字节随机（Base64URL）
- 存储：`token_hash = SHA-256(refresh_token)`（不存原文）
- TTL：30 天
- Rotation：每次 refresh 生成新 token，旧 token `used_at` 记录并失效
- Family：同一登录会话共享 `family_id`，任一成员 reuse → 整个 family 失效

## 3. 数据模型

### 3.1 复用自 plan-data-flyway

- `users`（V1）— `id / email / password_hash / email_verified / timezone / locale / status / created_at`
- `user_profiles`（V2）— 1:1 用户资料 + Push/AI 开关

### 3.2 新增 3 张表（V28 由本模块引入）

```sql
-- refresh_tokens（Refresh rotation + reuse detection）
CREATE TABLE refresh_tokens (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id       UUID NOT NULL,
    token_hash      TEXT NOT NULL UNIQUE,            -- SHA-256(refresh_token)
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  BIGINT REFERENCES refresh_tokens(id),
    ip              INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_user_family ON refresh_tokens(user_id, family_id);
CREATE INDEX idx_refresh_unused ON refresh_tokens(expires_at) WHERE used_at IS NULL AND revoked_at IS NULL;

-- email_verifications（邮箱验证 token）
CREATE TABLE email_verifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- password_resets（找回密码 token）
CREATE TABLE password_resets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,            -- 1 小时
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pwreset_unused ON password_resets(expires_at) WHERE consumed_at IS NULL;
```

### 3.3 写入策略

- 同一事务：业务 INSERT/UPDATE + refresh_token INSERT
- 删除：物理删除（refresh token 不归档）
- 清理：日终 Job 删除 `expires_at < NOW() - 7 days` 的记录

## 4. Outbox 事件

| event_type | 触发时机 | payload |
|---|---|---|
| `auth.user.registered` | AuthService.register() 提交后 | `{user_id, email, timezone, locale, occurred_at}` |
| `auth.user.logged_in` | AuthService.login() 成功后 | `{user_id, ip, user_agent, occurred_at}` |
| `auth.user.password_reset_requested` | EmailController.forgotPassword() 后 | `{user_id, email, occurred_at}` |
| `auth.token.reuse_detected` | JwtRefreshService 检测到 reuse | `{user_id, family_id, ip, occurred_at}` |

消费者：
- `auth.user.registered` → 触发欢迎邮件 + onboarding 引导
- `auth.user.logged_in` → 异常登录地点告警
- `auth.user.password_reset_requested` → 邮件已发，避免重复
- `auth.token.reuse_detected` → 强制下线 + Web Push 告警

## 5. 关键验收场景（TDD 种子）

### 5.1 注册 / 登录

- `auth_should_register_with_valid_credentials`：合法邮箱 + 强密码 → 返回 access + refresh
- `auth_should_reject_duplicate_email`：邮箱已存在 → `EMAIL_EXISTS`
- `auth_should_reject_weak_password`：zxcvbn < 3 → `WEAK_PASSWORD`
- `auth_should_send_verification_email`：注册后调用 `EmailService.send()`
- `auth_should_verify_email_with_token`：合法 token → 标记 user.email_verified=true
- `auth_should_login_with_correct_password`：邮箱 + 密码匹配 → 返回 token
- `auth_should_reject_wrong_password`：密码错误 → `INVALID_CREDENTIALS`
- `auth_should_lock_after_5_fails`：5 次失败 → 第 6 次 `USER_LOCKED`（15min）
- `auth_should_login_with_csrf_cookie`：CSRF cookie + header 配对通过

### 5.1.1 LoginAttemptService（5 次失败 → IP 锁定 15min）

- `attempt_should_record_failure_ip`：失败 → Redis IP key INCR + 设 TTL 15min
- `attempt_should_clear_after_success`：登录成功 → Redis IP key DEL
- `attempt_should_lock_after_5_fails`：第 6 次请求 → `USER_LOCKED`（即使密码正确也拒绝）
- `attempt_should_unlock_after_window`：15min TTL 过期 → 自动解锁
- `attempt_should_count_per_ip_not_user`：userId dim 不在 v1.0 范围，按 IP 维度计数（与 §6 验收对齐）
- `attempt_should_emit_failed_login_event`：连续失败 → 触发 `notify.budget.threshold.80` 类告警（或安全监控通道）

### 5.2 Refresh Token

- `auth_should_refresh_with_rotation`：合法 refresh → 新 access + 新 refresh + 旧 used_at 写入
- `auth_should_reject_reused_refresh`：旧 refresh 二次使用 → 全 family 失效 + `TOKEN_REUSED`
- `auth_should_revoke_family_on_reuse`：reuse 检测到 → 该 family 所有 token revoked_at 写入
- `auth_should_publish_reuse_event`：触发 `auth.token.reuse_detected` 事件
- `auth_should_reject_expired_refresh`：`expires_at < NOW()` → `TOKEN_EXPIRED`

### 5.3 找回密码

- `auth_should_send_reset_email`：邮箱存在 → 发邮件（恒定响应时间，防探测）
- `auth_should_return_same_response_for_unknown_email`：邮箱不存在 → 同样 200 + 同样延迟
- `auth_should_reset_with_valid_token`：合法 token + 强密码 → 更新密码 + token 失效
- `auth_should_reject_expired_reset_token`：1 小时过期 → `TOKEN_EXPIRED`
- `auth_should_revoke_all_refresh_after_reset`：重置后该用户所有 refresh 失效

### 5.4 CSRF

- `auth_should_issue_csrf_token`：GET /api/auth/csrf → Set-Cookie + body
- `auth_should_validate_csrf_on_post`：POST/PUT/DELETE 缺 CSRF → 403
- `auth_should_skip_csrf_on_get`：GET 不校验

### 5.5 UI 集成（浏览器手动验证）

- `ui_login_should_render`：浏览器打开 login.html 显示完整表单
- `ui_register_should_capture_timezone`：timezone 下拉默认 Asia/Shanghai
- `ui_forgot_should_mask_email_display`：找回页只显示部分邮箱
- `ui_responsive_mobile`：移动端布局自适应

## 6. 验收标准

- [ ] 注册 / 登录 / 找回密码流程端到端跑通
- [ ] JWT 15 分钟短期 + Refresh Token 30 天 rotation + reuse detection 演练
- [ ] CSRF 默认开启（dev profile 可关闭）
- [ ] 密码 BCrypt strength=12 + zxcvbn 强度校验
- [ ] 5 次失败锁定 15 分钟（per IP；userId dim not in v1.0 scope）
- [ ] UI 4 个页面在浏览器手动验证（Chrome + Firefox + Safari）
- [ ] 邮件发送失败重试 3 次 + 日终兜底
- [ ] Outbox 4 条 auth 事件全部注册
- [ ] 单测覆盖率 ≥ 85%

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 邮件投递失败 | 中 | 重试 3 次 + 日终兜底任务 + 监控 bounce 率 |
| 弱密码 | 中 | zxcvbn ≥ 3 + 长度 ≥ 12 + 字符多样性 |
| refresh token 泄露 | 高 | rotation + reuse detection + 全 family 失效 |
| CSRF 与 CORS 冲突 | 中 | SameSite=Lax + 显式 Origin 白名单 |
| 邮箱枚举攻击 | 中 | 找回密码恒定响应时间 + 恒定消息 |
| 并发登录同一用户 | 低 | 允许多端登录；同 family 内每次 rotation 正常 |
| 数据库注入（密码字段） | 低 | BCrypt + 唯一索引 + 长度限制 |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（CSRF cookie 走 nginx）
  - `plan-data-flyway.md`（users / user_profiles / 新增 V28 三表）
  - `plan-shared-infra.md`（@RequireAuth / @RateLimit / @Auditable 注解 + JwtTokenProvider）
  - `plan-shared-integration.md`（OutboxWriter 发布 auth.* 事件 + ApiResponse 信封）
- 下游：
  - `plan-01-task.md` ~ `plan-06-ai.md`（所有 controller 入口依赖 `@RequireAuth`）
  - `plan-observability-backup.md`（监控 5 次失败告警 + auth 事件流）