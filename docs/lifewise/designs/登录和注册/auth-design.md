# Lifewise 认证与权限设计 v1.0

> **文档目的**：解决技术架构 §6.5「用户认证与权限设计」缺口；为研发提供单文件可开工的设计输入。
>
> **锁定日期**：2026-07-28
> **适用版本**：v1.0 MVP
> **依赖文档**：技术架构 §0、§5.1、§5.4；数据模型 v1.1.1 + v1.2 (auth amendment, §11)；业务架构 §2、§6.1、§11
> **不在范围**：OAuth 第三方登录、Passkey、双因素、短信验证、SSO（MVP 不做；§13 留扩展口）

---

## §0 设计目标与范围

### 0.1 目标
1. 落实架构 §0「邮箱密码 + JWT + Refresh Token」决策
2. 解决 PRD §6.5「用户认证与权限设计」缺口（高优先级）
3. 不引入新的强外部依赖：SMTP 走可选降级路径，不阻塞本地部署
4. 为未来扩展（OAuth、Passkey、双因素）预留接口

### 0.2 范围（MVP）
- 首次管理员注册（部署 §5.4 已埋点）
- 普通用户注册
- 账号/邮箱 + 密码登录
- 找回密码（邮箱验证码）
- 主动改密
- 改 username（7 天冷却期）
- 首次登录引导（昵称 + 时区）
- AI/通知同意 Modal
- 主动登出

### 0.3 不在范围（MVP 留口）
- OAuth 第三方登录（微信、Google、Apple）—— 架构 §0「可扩展 OAuth」
- Passkey（WebAuthn）
- 双因素（TOTP / SMS）
- 短信找回密码
- SSO / SAML / OIDC

### 0.4 与已有约定的关系
- 架构 §0 已锁：「邮箱密码 + JWT + Refresh Token」——本文档是其落地方案
- 架构 §5.1 已锁：bcrypt cost=12、JWT HS256、登录限流、失败锁定——本文档引用 + 补充实现细节
- 业务架构 §6.1：`User`、`UserPreference`、`UserProfile` 三表分离——本文档对齐
- 数据模型 v1.1.1：`users` 已占位、`user_profiles` 表已存在（H-3）——本文档扩展 `users` 表
- 数据模型 v1.2 (auth amendment)：本文档 §11 提议

---

## §1 已锁定决策表（18 项）

| # | 决策项 | 方案 | 来源 |
|---|---|---|---|
| 1 | 登录凭证 | username **或** email + 密码（二选一登录） | 本次 |
| 2 | username 规则 | 注册时自定；3-20 字符；小写字母/数字/下划线；保留字禁用 | 本次 |
| 3 | username 改名 | 7 天冷却期可改；不走重定向 | 本次 |
| 4 | 昵称 (nickname) | 首次进入系统设置；随时可改；永远可改 | 本次 |
| 5 | email 字段 | 保留 `email TEXT UNIQUE NOT NULL`（架构已锁） | 架构 §0 |
| 6 | 密码强度 | 8 位起 + 大小写字母 + 数字 | 本次 |
| 7 | 密码哈希 | bcrypt cost=12 | 架构 §5.1 |
| 8 | JWT | Access 15min HS256 + Refresh 30天 HttpOnly Secure SameSite=Strict Cookie | 架构 §5.1 |
| 9 | 找回密码 | 邮箱 6 位数字验证码；5min 过期；单次使用 | 本次 |
| 10 | 找回密码 SMTP 降级 | 本地未配 SMTP 时，验证码写入 admin console 系统消息 | 本次 |
| 11 | 注册流程 | username + email + password + 服务条款勾选 → 创建 | 本次 |
| 12 | 首次登录引导 | 2 步轻引导（昵称 + 时区） | 本次 |
| 13 | AI/通知同意 | 进系统后弹 Modal（不阻塞主流程） | 业务架构 §6.1 |
| 14 | 角色 | USER / ADMIN（架构已锁）；首个注册 = ADMIN | 架构 §5.1 + 部署 §5.4 |
| 15 | 协议勾选 | 注册页底部单一复选框（合并服务条款 + 隐私政策） | 本次 |
| 16 | 第三方登录 | MVP 不做；架构 §0 已留 OAuth 扩展口 | 架构 §0 |
| 17 | 限流 | 登录 10 req/min/IP；失败 5 次锁 15min（架构已锁） | 架构 §5.1 |
| 18 | 跨用户访问 | 统一返回 404（防枚举） | 架构 §5.1 |

---

## §2 账号体系

### 2.1 users 字段语义

| 字段 | 含义 | 时机 | 可改性 |
|---|---|---|---|
| `username`（亦称 login_id） | 登录账号 | 注册时定 | 7 天冷却期可改 |
| `email` | 找回通道、可选通知 | 注册时定（UNIQUE NOT NULL） | 当前 MVP 不允许改（v1.1+ 再议） |
| `password_hash` | 密码哈希 | 注册/改密 | — |
| `role` | USER / ADMIN | 注册时根据「是否首位」决定 | 不可改（管理员后台手动改） |
| `status` | ACTIVE / LOCKED / DISABLED | 登录失败锁定 / 管理员封禁 | — |
| `last_username_change_at` | 上次改 username 时间 | 改 username 时写 | — |
| `token_version` | JWT 版本号 | 注册时=0；改密 / 找回密码后 +1 | 由系统自动维护，用于 Refresh 校验（详见 §5.5） |

### 2.1.1 user_profiles 字段语义

| 字段 | 含义 | 时机 | 可改性 |
|---|---|---|---|
| `nickname` | 展示名 | 首次进入系统设置 | 永远可改 |
| `timezone` / `locale` | 时区 / 语言 | 首次进入设置 | 随时可改 |
| `is_password_reset_pending` | 找回密码后强制改密标记 | 找回密码成功后置 true；强制改密后置 false | 由系统自动维护 |
| `profile_complete` | 首次登录引导完成标志 | onboarding 两步均完成后置 true | 由系统自动维护 |
| `ai_interpretation_enabled` | AI 解读开关 | onboarding / 默认 ON | 设置页可改 |
| `notification_email_enabled` | 邮件通知开关 | onboarding / 默认 ON | 设置页可改 |
| `notification_inapp_enabled` | 站内通知开关 | onboarding / 默认 ON | 设置页可改 |

### 2.2 username 命名规则

| 项 | 规则 |
|---|---|
| 长度 | 3-20 字符 |
| 字符集 | `[a-z0-9_]` |
| 开头 | 不能以数字开头（避开纯数字混淆用户名/编号） |
| 保留字 | `admin`、`root`、`system`、`api`、`auth`、`login`、`register`、`null`、`undefined`、`nan` 及其大小写变体 |
| 唯一性 | 全库 UNIQUE；不区分大小写（应用层强制 `lower(username)` 比较与写入） |
| 修改 | 7 天冷却期（详见 §7.3） |

### 2.3 角色矩阵

| 资源/操作 | USER | ADMIN |
|---|---|---|
| 注册 | ✅（首位除外） | ✅ |
| 登录 | ✅ | ✅ |
| 找回密码 | ✅（本人） | ✅（本人 + 重置任意 USER） |
| 改密（本人） | ✅ | ✅ |
| 改 username（本人） | ✅ | ✅ |
| 改 nickname（本人） | ✅ | ✅ |
| 业务 CRUD（本人数据） | ✅（隔离） | ✅（隔离） |
| 重置任意 USER 密码 | ❌ | ✅ |
| 锁定 / 解锁任意 USER | ❌ | ✅ |
| 查看审计日志（本人） | ✅（仅本人） | ✅（全部） |
| 列表所有 USER | ❌ | ✅ |

> **原则**：ADMIN 仅用于「运营动作」，无业务数据访问特权。

---

## §3 首次注册管理员流程

### 3.1 触发条件
- 服务启动 + `SELECT COUNT(*) FROM users = 0` → 进入「首次管理员注册」特殊页（前端 SPA 检测、后端独立端点强制）
- 不允许并发创建多个首位 ADMIN（注册接口持分布式锁）
- **分布式锁实现**：
  - Key：`lifewise:bootstrap:admin`
  - Backend：Redis
  - 加锁：`SET lifewise:bootstrap:admin <requestId> NX EX 30`
  - 解锁：Lua 脚本 CAS 删除（仅当 value == requestId）
  - 拿锁后再次校验 `COUNT(*) = 0`，否则释放锁并返回 `SETUP_ADMIN_RACE`
  - 整个流程（校验 + 写入）必须在锁 TTL 内完成，否则回滚事务并释放锁

### 3.2 流程
1. 用户访问 `https://localhost/` → 检测到首位注册态 → 进入 `/setup/admin` 路由
2. 强制填写：username、email、password、勾选服务条款
3. 服务端加分布式锁 → 校验首位条件（仍是首位）→ 写入 `users` 表（role=ADMIN）
4. 写入 Outbox 事件 `user.admin_created`（v1.1+ 用于审计与告警）
5. 写入 `user_profiles`（user_id 关联、字段待首次登录引导补全）
6. 释放分布式锁 → 跳转登录页 → 自动登录（返回 Set-Cookie refresh_token）

### 3.3 不变量
- 首位 ADMIN 创建后，**`POST /api/v1/setup/admin` 立即返回 409 + `SETUP_ADMIN_ALREADY_DONE`**（防探测）
- 首位 ADMIN 创建后，普通注册入口开放
- 后续注册均 role=USER
- ADMIN 角色变更只能通过管理员后台手动设置（不在 MVP UI 范围）

---

## §4 注册流

### 4.1 输入字段

| 字段 | 客户端校验 | 服务端校验 |
|---|---|---|
| `username` | 3-20 字符、`[a-z0-9_]`、不数字开头、保留字检查（实时异步） | Bean Validation @Pattern；DB UNIQUE |
| `email` | RFC 5322 简单正则、trim lowercase | Bean Validation @Email；DB UNIQUE |
| `password` | 8+ 长度、大写+小写+数字 | 同步服务端正则 |
| `agreedToTerms` | 必须 true | Bean Validation @AssertTrue |

### 4.2 流程
1. 客户端 POST `/api/v1/auth/register`
2. 服务端 Bean Validation + 唯一性校验
3. 密码 bcrypt(cost=12) 哈希
4. 写入 `users` 表（status=ACTIVE、role=USER 或 ADMIN 见 §3）
5. 写入 `user_profiles`（AI/通知默认开启、nickname NULL）
6. 写入 Outbox 事件 `user.registered`
7. 返回标准 API 信封 `{ code: 0, data: { userId, username } }`

### 4.3 防枚举
- 重复 username / email：响应统一 `REGISTER_FAILED`，不区分「账号已存在」或「email 已存在」
- 内部：写审计日志记录具体原因
- 客户端：仅给一句「注册失败，请检查输入或换个账号」

### 4.4 服务条款
- 注册页底部单一复选框「我已阅读并同意《服务条款》和《隐私政策》」
- 两份文档链接为 SPA 内折叠页（不弹窗，避免视觉打断）
- 复选框 unchecked 时禁用提交按钮

---

## §5 登录流

### 5.1 输入
- `identifier`：username **或** email（前端自动 trim + lowercase）
- `password`

### 5.2 流程
1. 客户端 POST `/api/v1/auth/login`
2. 服务端按 `identifier` 形态路由：
   - 含 `@` → email 匹配
   - 否则 → `lower(username) = lower(?)`
3. 用户不存在 → 走「未注册路径」（fake bcrypt 100ms 延迟 + 失败计数 +1）
4. 存在 → bcrypt 校验密码
5. 失败计数（Redis `rl:auth:fail:{userOrIp}`）
6. 校验通过：
   - 清空失败计数
   - 生成 JWT Access（HS256、payload `{ sub: userId, role, tokenVersion }`、15min）
   - 生成 JWT Refresh（同 payload、30 天、jti 随机）
   - 下发 Refresh：Set-Cookie `refresh_token`；HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh
   - 响应体 `{ accessToken, user: { id, username, nickname, role, timezone } }`
7. 写入审计日志 `auth.login`

### 5.3 错误响应

| 错误 | HTTP | code | 客户端文案 |
|---|---|---|---|
| 账号或密码错误 | 401 | `AUTH_INVALID_CREDENTIALS` | 账号或密码错误 |
| 账号被锁定 | 423 | `AUTH_LOCKED` | 登录失败次数过多，请 15 分钟后再试 |
| 账号已禁用 | 403 | `AUTH_DISABLED` | 账号已被禁用，请联系管理员 |
| 限流 | 429 | `RATE_LIMITED` | 请求过于频繁，请稍后再试 |

### 5.4 JWT 结构（payload）
```json
{
  "sub": "1001",
  "role": "USER",
  "tokenVersion": 1,
  "iat": 1788000000,
  "exp": 1788000900,
  "jti": "uuid"
}
```
- `tokenVersion`：密码修改后 +1，使旧 token 失效
- `jti`：Refresh token 唯一标识，登出时入黑名单

### 5.5 Refresh 旋转
- POST `/api/v1/auth/refresh`（强制 Cookie 路径）
- 校验顺序：
  1. 签名 + 过期（JWT 库）
  2. 黑名单（Redis `rl:auth:refresh:bl:{jti}`）
  3. **`tokenVersion` 一致性**：payload 的 `tokenVersion` 必须等于 `users.token_version` 当前值；不匹配则拒绝并审计 `auth.refresh_token_version_mismatch`
- 校验通过 → 发新 Refresh + 新 Access
- 旧 Refresh 立即入黑名单 30 天
- 异常码：`AUTH_REFRESH_INVALID` / `AUTH_REFRESH_TOKEN_VERSION_MISMATCH`

---

## §6 找回密码

### 6.1 触发
- 登录页「忘记密码」链接 → 输入账号或 email

### 6.2 流程

#### 步骤 1：申请验证码
1. POST `/api/v1/auth/forgot-password`（Body：identifier）
2. 服务端按 identifier 形态查用户
3. 用户不存在：响应统一 `OK`，不暴露存在性（但写「未注册路径」延迟 + 审计）
4. 用户存在：
   - 生成 6 位数字验证码
   - 写入 `password_reset_tokens`（token_hash、identifier、expires_at、used_at NULL、attempt_count=0）
   - **有 SMTP** → 发送邮件「验证码：XXXXXX，5 分钟内有效」
   - **无 SMTP**（本地降级） → 写入 `admin_console_messages` 表 + 在 ADMIN 控制台「系统消息」中可见
5. 响应 `{ ok: true }`（无论是否存在用户）

#### 步骤 2：提交验证码 + 新密码
1. POST `/api/v1/auth/reset-password`（Body：identifier + code + newPassword）
2. 校验验证码：未过期、未使用、未超 5 次错误
3. 校验密码强度（§4.1）
4. bcrypt 哈希新密码 → 更新 `users.password_hash`
5. `tokenVersion++` → 使所有旧 token 失效
6. 标记 token `used_at = NOW()`、记录 IP
7. 写入 `user_profiles.is_password_reset_pending = true`（首次登录检测，强制改密）
8. 写入审计日志 `auth.password_reset`

### 6.3 限流
- `/forgot-password`：1 req/30s/identifier + 5 req/h/identifier
- `/reset-password`：1 req/min/identifier + 10 req/h/identifier

### 6.4 安全
- 验证码：6 位数字、`lower()` 比较
- 单次使用：标记 used_at 后失效
- 错误 5 次后该验证码提前作废
- 新密码不可与旧密码相同（同密码拒绝）

### 6.5 本地无 SMTP 降级细节
- 配置项 `app.smtp.enabled`（默认 false）
- 应用启动时检测：`true` → 走邮件；`false` → 降级路径
- 验证码文本追加在 ADMIN 控制台顶部横幅（仅 ADMIN 可见）+ 持久化到 `admin_console_messages`
- ADMIN 用户通过 `/admin/console-messages`（管理后台）查看
- 隐私：仅 ADMIN 可见（role=ADMIN 才返回列表）

---

## §7 改密与改 username

### 7.1 主动改密（已登录）
1. POST `/api/v1/users/me/password`（Body：oldPassword + newPassword）
2. 校验旧密码
3. 校验新密码强度
4. 新密码不可与旧密码相同
5. bcrypt 哈希 → 更新
6. `tokenVersion++` → 当前会话 Access 失效，强制下次请求走 Refresh 旋转
7. 限流：5 req/h/user
8. 写入审计 `auth.password_change`

### 7.2 找回后强制改密
- `user_profiles.is_password_reset_pending = true` 时，所有非 `/auth/*` 请求中间件拦截 → 跳转 `/password-change-required`
- 用户提交新密码（无需旧密码）→ 清标记

### 7.3 改 username
1. POST `/api/v1/users/me/username`（Body：newUsername）
2. 校验新 username 合规（§2.2）
3. 校验冷却期：
   - `last_username_change_at IS NULL` → 允许
   - `last_username_change_at > NOW() - INTERVAL '7 days'` → 拒绝 `USERNAME_CHANGE_COOLDOWN`
4. 唯一性：DB UNIQUE（应用层并发靠 `SELECT FOR UPDATE`）
5. 更新 `username` + `last_username_change_at = NOW()`
6. 写入审计 `user.username_changed`
7. 限流：3 req/h/user

---

## §8 首次登录引导

### 8.1 触发条件
- 登录响应中 `user.profileComplete: false`（来自 `user_profiles.profile_complete` 标志，详见 §2.1.1）
- 注册时 `profile_complete = false`；onboarding 两步完成后 `profile_complete = true`
- 中间件检测到 → 跳转 `/onboarding`

### 8.2 2 步轻引导

| 步骤 | 字段 | 校验 |
|---|---|---|
| 第 1 步 | `nickname` | 必填、1-30 字符、不含控制字符 |
| 第 2 步 | `timezone` | IANA 时区列表、默认 `Asia/Shanghai` |

- 两步可分别保存，单步完成即写一次 DB
- 第 2 步保存后写 `profileComplete = true`

### 8.3 AI/通知同意 Modal（不阻塞）
- 完成引导后进入主页 → 立即弹 Modal：
  - 「是否启用 AI 解读？」+ 「是否启用通知提醒？」两个独立 toggle，默认 ON
  - 「不再提醒」勾选 → 本次会话不再弹
- 「同意 / 跳过」二选一即可关掉
- 写入 `user_profiles.ai_interpretation_enabled` 和 `*_push_enabled`
- Modal 走过一次后不再弹（除非用户重置偏好）

### 8.4 业务架构对齐
业务架构 §6.1 要求"AI/通知同意状态可撤回"——Modal 写入字段后可在 `/settings/notifications` 与 `/settings/ai` 单独撤回；不在首次引导内强制。

---

## §9 API 契约表

| # | Method | Path | 鉴权 | Body | 响应 | 限流 | 错误码 |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | 公开 | `{username,email,password,agreedToTerms}` | `{userId,username}` | 5/h/IP | `REGISTER_FAILED` |
| 2 | POST | `/api/v1/auth/login` | 公开 | `{identifier,password}` | `{accessToken,user{id,username,nickname,role,timezone,profileComplete}}` + Set-Cookie refresh_token | 10/min/IP | `AUTH_INVALID_CREDENTIALS` / `AUTH_LOCKED` / `AUTH_DISABLED` |
| 3 | POST | `/api/v1/auth/refresh` | Refresh Cookie | — | `{accessToken}` + Set-Cookie | 30/min/user | `AUTH_REFRESH_INVALID` / `AUTH_REFRESH_TOKEN_VERSION_MISMATCH` |
| 4 | POST | `/api/v1/auth/logout` | 已登录 | — | `{ok}` + 清 Cookie | — | — |
| 5 | POST | `/api/v1/auth/forgot-password` | 公开 | `{identifier}` | `{ok}` | 1/30s/identifier + 5/h/identifier | `RATE_LIMITED` |
| 6 | POST | `/api/v1/auth/reset-password` | 公开 | `{identifier,code,newPassword}` | `{ok}` | 1/min/identifier + 10/h/identifier | `RESET_CODE_INVALID` / `RESET_CODE_EXPIRED` / `RESET_CODE_USED` / `PASSWORD_TOO_WEAK` / `PASSWORD_SAME_AS_OLD` |
| 7 | GET | `/api/v1/setup/admin-required` | 公开 | — | `{required:boolean}` | — | — |
| 7a | POST | `/api/v1/setup/admin` | 公开 | `{username,email,password,agreedToTerms}` | `{userId,username}` + Set-Cookie refresh_token | 1 total/lifetime | `SETUP_ADMIN_ALREADY_DONE` / `SETUP_ADMIN_RACE` |
| 8 | POST | `/api/v1/users/me` | 已登录（且 `is_password_reset_pending=false`） | `{nickname?,timezone?}` | `{user{...}}` | 60/min/user | `VALIDATION_FAILED` |
| 9 | POST | `/api/v1/users/me/password` | 已登录 | `{oldPassword,newPassword}` | `{ok}` | 5/h/user | `AUTH_INVALID_OLD_PASSWORD` / `PASSWORD_TOO_WEAK` / `PASSWORD_SAME_AS_OLD` |
| 9a | POST | `/api/v1/users/me/password/reset-required` | 已登录 + `is_password_reset_pending=true` | `{newPassword}` | `{ok}` | 5/h/user | `PASSWORD_TOO_WEAK` / `PASSWORD_SAME_AS_OLD` |
| 10 | POST | `/api/v1/users/me/username` | 已登录 | `{username}` | `{username}` | 3/h/user | `USERNAME_INVALID` / `USERNAME_TAKEN` / `USERNAME_CHANGE_COOLDOWN` |
| 11 | POST | `/api/v1/users/me/profile/onboarding/complete` | 已登录（且 `profile_complete=false`） | — | `{ok}` | 60/min/user | `VALIDATION_FAILED` |
| 12 | GET | `/api/v1/admin/console-messages` | ADMIN | — | `{messages:[{id,type,payload,createdAt,readAt}]}` | 60/min/user | `FORBIDDEN` |
| 13 | POST | `/api/v1/admin/console-messages/{id}/read` | ADMIN | — | `{ok}` | 60/min/user | `FORBIDDEN` |

> **公共错误码**：标准 API 信封，`code` 非 0 即失败；HTTP 状态码映射见各端点文段。

---

## §10 安全策略

### 10.1 密码与认证
- 密码 bcrypt cost=12（架构 §5.1）
- 密码强度：8+、大写+小写+数字（§1 决策 6）
- JWT：HS256、Access 15min、Refresh 30 天（架构 §5.1）
- Refresh 旋转：每次刷新作废旧 Refresh、入黑名单
- 主动登出黑名单：Refresh jti 入 Redis 黑名单 30 天
- 改密 / 找回密码后：`tokenVersion++` → 旧 token 全失效

### 10.2 限流总表

| 端点 | 限流 | 实现 |
|---|---|---|
| `POST /auth/login` | 10 req/min/IP | Redis token-bucket |
| `POST /auth/register` | 5 req/h/IP | Redis counter |
| `POST /auth/forgot-password` | 1/30s + 5/h/identifier | Redis token-bucket + counter |
| `POST /auth/reset-password` | 1/min + 10/h/identifier | Redis counter |
| `POST /auth/refresh` | 30/min/user | Redis token-bucket |
| `POST /setup/admin` | 1 total/lifetime | Redis 标志位 + DB 兜底（详见 §3.1） |
| `POST /users/me/password` | 5/h/user | Redis counter |
| `POST /users/me/password/reset-required` | 5/h/user | Redis counter |
| `POST /users/me/username` | 3/h/user | Redis counter |
| `POST /users/me` | 60/min/user | Redis token-bucket |
| `POST /users/me/profile/onboarding/complete` | 60/min/user | Redis token-bucket |
| `GET /admin/console-messages` | 60/min/user | Redis token-bucket |
| `POST /admin/console-messages/{id}/read` | 60/min/user | Redis token-bucket |

### 10.3 失败锁定
- 登录失败 5 次锁 15 分钟（架构 §5.1）
- Redis key：`rl:auth:lock:{userOrIp}`、TTL=900s
- 解锁：等待 TTL 自然过期 / 管理员后台手动
- ADMIN 不锁定（防误锁唯一管理员）

### 10.4 跨用户访问
- 业务接口一律从 `SecurityContext.userId` 取当前用户
- 跨用户访问统一返回 404（防枚举）+ 写安全审计

### 10.5 审计日志

| 事件 | 字段 |
|---|---|
| `auth.login` | userId, ip, userAgent, result, errorCode |
| `auth.logout` | userId, ip |
| `auth.register` | userId, ip, userAgent |
| `auth.password_reset` | userId, ip |
| `auth.password_change` | userId, ip, self, source：`self=true` 主动改密（§7.1），`self=false` 找回密码后改密（§7.2） |
| `auth.refresh_token_version_mismatch` | userId, expected, actual, ip |
| `user.username_changed` | userId, oldUsername, newUsername, ip |
| `user.locked` / `user.unlocked` | actorUserId, targetUserId, reason, ip |
| `auth.cross_user_access_attempt` | actorUserId, targetUserId, resourceType, ip |

- 保留 180 天（架构 §5.1）
- 仅 ADMIN 可见审计；普通 USER 仅看本人日志

### 10.6 双因素预留
- `users.two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE`
- `users.two_factor_secret VARCHAR(255)`（加密）
- `users.two_factor_backup_codes TEXT[]`
- 端点 `/api/v1/auth/2fa/*` 留 stub（MVP 不实现 UI）

### 10.7 防滥用
- 注册：同 IP 5 次/小时
- 找回：同 identifier 30 秒/次、5 次/小时
- 所有端点：超限返 `RATE_LIMITED` + 审计「ratelimit_exceeded」
- 验证码错误计数独立于登录失败计数

---

## §11 数据模型迁移

### 11.1 新增迁移 `V2_1__add_username_to_users.sql`

```sql
-- v1.2 amendment: 扩展 users 表支持 username 登录 + 改名冷却期 + JWT 失效控制 + 2FA 预留

ALTER TABLE users
    ADD COLUMN username TEXT,
    ADD COLUMN last_username_change_at TIMESTAMPTZ,
    ADD COLUMN token_version INT NOT NULL DEFAULT 0,
    -- 双因素预留（v1.0 不启用 UI，字段先埋好；详见 §10.6）
    ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN two_factor_secret VARCHAR(255) NULL,
    ADD COLUMN two_factor_backup_codes TEXT[] NULL;

-- 旧账号无 username 的回填策略（v1.0 上线前 users 表为空，理论影响 0 行；
-- 仍写回填以防测试库已有数据）
-- 用 lpad 固定 14 位数字 + 'user_' 前缀 = 19 字符，避免 BIGINT id 超过 20 字符上限（详见 §2.2）
UPDATE users SET username = 'user_' || lpad(id::text, 14, '0') WHERE username IS NULL;

ALTER TABLE users
    ALTER COLUMN username SET NOT NULL,
    ADD CONSTRAINT users_username_unique UNIQUE (username),
    -- 应用层已强校验 3-20 字符；DB 层加 CHECK 兜底
    ADD CONSTRAINT users_username_length CHECK (char_length(username) BETWEEN 3 AND 20);

CREATE INDEX idx_users_username_lower ON users (lower(username));

-- 强制改密标记 + onboarding 完成标志
ALTER TABLE user_profiles
    ADD COLUMN is_password_reset_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN profile_complete BOOLEAN NOT NULL DEFAULT FALSE;

-- 找回密码验证码
CREATE TABLE password_reset_tokens (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    identifier      TEXT NOT NULL,        -- 申请时使用的 username 或 email（小写）
    token_hash      TEXT NOT NULL,        -- 验证码 sha256
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ NULL,
    attempt_count   INT NOT NULL DEFAULT 0,
    ip              TEXT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 错误 5 次后验证码作废（§6.2 step 2 + §6.4）；CHECK 兜底
    CONSTRAINT password_reset_tokens_attempt_count_range CHECK (attempt_count BETWEEN 0 AND 5)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_hash ON password_reset_tokens(token_hash);

-- 找回密码降级：本地无 SMTP 时，验证码写入此处供 ADMIN 在控制台查看（详见 §6.5）
CREATE TABLE admin_console_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    type            TEXT NOT NULL,        -- 'PASSWORD_RESET_CODE' / 'SYSTEM' 等
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ NULL
);

CREATE INDEX idx_admin_console_messages_user ON admin_console_messages(user_id);
CREATE INDEX idx_admin_console_messages_unread ON admin_console_messages(created_at) WHERE read_at IS NULL;
```

### 11.2 数据模型变更申请（写一份 data-model-amendment）

> 由于本变更与"账号体系设计"强耦合，建议另起一份 `docs/lifewise/architecture/versions/data-model-design-v1.2-auth-amendment.md`，引用本 §11。

---

## §12 验收清单

### 12.1 功能验收

- [ ] 首次启动 → 自动跳转 `/setup/admin`、创建首位 ADMIN 成功
- [ ] 首位 ADMIN 已存在 → `POST /api/v1/setup/admin` 返回 409 + `SETUP_ADMIN_ALREADY_DONE`
- [ ] 首位 ADMIN 并发创建 → 第二个请求返回 `SETUP_ADMIN_RACE`
- [ ] 注册：username、email、password、勾选条款 → 成功；任一字段不合法 → 拒绝
- [ ] 重复 username → `REGISTER_FAILED`（不区分原因）
- [ ] 重复 email → `REGISTER_FAILED`（不区分原因）
- [ ] 登录用 username：成功 + JWT 下发
- [ ] 登录用 email：成功 + JWT 下发
- [ ] 登录失败 5 次：账号锁定 15min
- [ ] 改密：旧密码错、新密码弱、与旧密码相同 → 拒绝
- [ ] 找回密码：有 SMTP → 收邮件；无 SMTP → ADMIN 控制台可见验证码（`/admin/console-messages` 列表）
- [ ] 找回密码首次登录：业务接口被中间件拦截 → 跳转 `/password-change-required` → `POST /api/v1/users/me/password/reset-required` 成功后清标记
- [ ] 改 username：首次允许；冷却期内拒绝；过期后允许
- [ ] 首次登录引导：2 步完成 → `profile_complete = true`
- [ ] AI/通知 Modal：弹一次、可关、可在设置撤回
- [ ] 主动登出：清除 Access + Refresh、Refresh 入黑名单
- [ ] Refresh 旋转：旧 Refresh 立即失效
- [ ] **tokenVersion 校验**：改密 / 找回密码后，旧 Refresh 即使未到期、也未在黑名单，仍因 `tokenVersion` 失配被拒绝（错误码 `AUTH_REFRESH_TOKEN_VERSION_MISMATCH`）

### 12.2 安全验收

- [ ] 密码入库 bcrypt cost=12
- [ ] 密码明文不落日志
- [ ] JWT 篡改 → 401
- [ ] 跨用户访问 → 404 + 审计
- [ ] 限流超限 → 429 + 标准错误码
- [ ] 失败锁定到期自动解锁
- [ ] ADMIN 用户不被锁定
- [ ] 审计日志完整可查

### 12.3 性能验收

- [ ] 登录 P95 ≤ 200ms（不含网络）
- [ ] JWT 校验 ≤ 10ms
- [ ] bcrypt 校验 P95 ≤ 300ms（cost=12 单次）

### 12.4 测试用例

- 单元测试：§4 / §5 / §6 / §7 各流程状态机、边界
- 集成测试：跨表唯一性、并发改名、限流、Refresh 旋转
- E2E：首次注册 → 登录 → 引导 → 改密 → 找回 → 改 username 全链路

---

## §13 未来扩展（v1.1+，仅留口）

| 扩展 | 预留点 |
|---|---|
| OAuth（微信 / Google / Apple） | 安全模块加 OAuth2 filter；users 表加 `oauth_accounts` 副表 |
| Passkey（WebAuthn） | users 表加 `passkeys` 副表；端点 `/auth/passkey/*` |
| 双因素（TOTP / SMS） | §10.6 已加字段；端点 `/auth/2fa/*` 留 stub |
| username 重定向 | `username_history` 表；端点按历史映射查询 |
| 邮箱强制验证 | users 表加 `email_verified_at` 字段；发邮件 + 验证端点 |
| 多设备登录管理 | `active_sessions` 表；查看 + 强制下线 |

> 上述扩展均不破坏 v1.0 现有数据契约。

---

## §14 附录

### 14.1 与架构 §0、§5.1 的对齐检查

| 架构条目 | 本文档对应 | 一致性 |
|---|---|---|
| §0 邮箱密码 + JWT + Refresh Token | §5 + §1 决策 1、8 | ✅ 一致 |
| §5.1 bcrypt cost=12 | §10.1 | ✅ 一致 |
| §5.1 JWT HS256、Access 15min、Refresh 30d HttpOnly Secure SameSite=Strict | §5.4、§10.1 | ✅ 一致 |
| §5.1 登录失败 5 次锁 15min | §10.3 | ✅ 一致 |
| §0 可扩展 OAuth | §13 扩展口 | ✅ 一致 |
| §5.4 首次注册管理员 | §3 | ✅ 一致 |
| §5.1 跨用户 404 | §10.4 | ✅ 一致 |

### 14.2 与数据模型 v1.1.1 / v1.2 (auth amendment) 的对齐检查

| 数据模型条目 | 本文档对应 | 一致性 |
|---|---|---|
| `users(id,email,timezone,locale,status,created_at)` | §11.1 扩展 username、last_username_change_at、token_version、2FA 预留字段；保留所有原字段 | ✅ 一致 |
| `user_profiles(nickname, timezone, locale, *_enabled flags)` | §2.1.1 字段语义 + §8.3 写入字段对齐 H-3 / M-3 | ✅ 一致 |
| `user_profiles.is_password_reset_pending` (新增) | §11.1 DDL | ✅ 一致（v1.2 amendment） |
| `user_profiles.profile_complete` (新增) | §11.1 DDL | ✅ 一致（v1.2 amendment） |
| `password_reset_tokens` (新增) | §11.1 DDL | ✅ 一致（v1.2 amendment） |
| `admin_console_messages` (新增) | §11.1 DDL | ✅ 一致（v1.2 amendment） |

### 14.3 与业务架构 §6.1 的对齐

| 不变量 | 本文档对应 | 一致性 |
|---|---|---|
| 时区必须有效 | §8.2 第 2 步 IANA 校验 | ✅ |
| AI/通知同意状态可撤回 | §8.3 + §8.4 | ✅ |
| 敏感画像按最小权限暴露 | §2.3 角色矩阵 | ✅ |

---

## §15 变更记录

| 版本 | 日期 | 作者 | 摘要 |
|---|---|---|---|
| v1.0 | 2026-07-28 | Claude | 初稿：落实 18 项决策 + 接口契约 + 数据模型迁移 |
| v1.0a | 2026-07-28 | Claude | 修补：补 `token_version`字段定义与 SQL、回填改 lpad、`attempt_count` CHECK、新增 setup/admin、password/reset-required、admin/console-messages 端点、§3.1 分布式锁实现、§5.5 tokenVersion 校验、§11.1 补 admin_console_messages 表、§14.2 对齐 v1.2 amendment |
