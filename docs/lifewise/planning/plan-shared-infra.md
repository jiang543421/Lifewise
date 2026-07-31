# plan-shared-infra 实施方案

## 参考资料

- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §3 安全设计 + §1.3 容器间网络
- [`docs/lifewise/architecture/business-architecture.md`](../architecture/business-architecture.md) §3 模块边界（横切约束）
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) §0 主键策略
- `CLAUDE.md` §7 安全规范 / §7.1 密钥 / §7.3 认证授权 / §7.4 速率限制 / §7.5 错误信息

## 参考目录

- backend：`app/src/main/java/com/lifewise/shared/infra/`
  - `security/` — JWT 过滤器、注解、密码编码器、CSRF
  - `ratelimit/` — Redis 令牌桶、AOP 切面、`@RateLimit` 注解
  - `audit/` — `@Auditable` 注解、AOP 切面、审计写入器
  - `async/` — `AsyncConfig`（`@EnableAsync` + `ThreadPoolTaskExecutor`，供 plan-06-ai 等模块的 `@Async` 注解生效）
- frontend：—（前端不参与本模块）

## 1. 模块边界 / 包结构

shared-infra 是**横切所有 controller 的基础模块**，三块组成：

```
shared/infra/
├── security/
│   ├── JwtAuthenticationFilter.java       OncePerRequestFilter 解析 Bearer
│   ├── JwtTokenProvider.java              生成 / 解析 JWT（含 jti）
│   ├── JwtRefreshTokenService.java        JWT Refresh Token 底层工具接口（H6：以下契约供 auth 模块 JwtRefreshService 调用）
│   │       接口契约（H6 强制声明）：
│   │         - Optional<RefreshResult> rotate(String refreshToken)        // rotation；返回新 access + refresh
│   │         - void detectReuse(String refreshToken)                     // 检测到 reuse → 抛 ReuseDetectedException
│   │         - void revokeFamily(String familyId)                        // 全家族失效（reuse detection 触发）
│   │         - Optional<RefreshClaims> parseClaims(String token)         // 解析 jti / family_id / exp
│   │         - record RefreshResult(String accessToken, String refreshToken, Instant expiresAt)
│   │         - record RefreshClaims(String jti, String familyId, Long userId, Instant exp)
│   ├── PasswordEncoderConfig.java         BCrypt strength=12
│   ├── CsrfFilter.java                    双 token 校验（XSRF-TOKEN cookie + header）
│   ├── SecurityConfig.java                Spring Security 配置链
│   ├── annotation/
│   │   ├── RequireAuth.java               @RequireAuth 强制鉴权
│   │   └── RequireRole.java               @RequireRole("ADMIN")
│   └── exception/
│       ├── JwtExpiredException.java
│       ├── JwtInvalidException.java
│       └── ReuseDetectedException.java    Refresh 复用告警（强制下线该家族 token）
├── ratelimit/
│   ├── RateLimit.java                     @RateLimit(key, limit, window, scope)
│   ├── RateLimitAspect.java               AOP 切面：方法前检查 → 异常抛 429
│   ├── TokenBucketService.java            Redis Lua 原子令牌桶
│   └── lua/token_bucket.lua               原子增减脚本
└── audit/
    ├── Auditable.java                     @Auditable(action, resourceType)
    ├── AuditAspect.java                   AOP 切面：方法返回后异步记录
    ├── AuditWriter.java                   异步队列 + 落库（job_runs 复用 or 新表）
    └── AuditQueryService.java             查询接口（管理后台用）
└── async/
    ├── AsyncConfig.java                   @EnableAsync + ThreadPoolTaskExecutor（核心 8 / 最大 16 / 队列 200 / 拒绝策略 CallerRuns）
    └── AsyncExceptionHandler.java         @Async 异常统一处理（写 operation_logs）
```

## 2. API 契约（间接接口 + 注解）

### 2.1 security 暴露的注解 / 接口

| 名称 | 类型 | 行为 |
|---|---|---|
| `@RequireAuth` | 方法注解 | 拦截未携带 / 失效 JWT 请求，返回 401 |
| `@RequireRole("ADMIN")` | 方法注解 | 在 @RequireAuth 基础上校验 role 字段 |
| `GET /api/auth/csrf` | HTTP | 颁发 CSRF token（cookie + 响应体） |
| `POST /api/auth/refresh` | HTTP（auth 模块实现） | Refresh rotation，触发 `ReuseDetectedException` 则全家族失效 |

### 2.2 ratelimit 暴露的注解

```java
@RateLimit(
    key = "userId",        // userId | ip | global
    limit = 60,
    window = 60,           // 秒
    scope = "api"          // api | login | ai | export | webpush（G1：与 §2.2 rate limit 表 5 scope 对齐；新增 export 与 webpush 用于 v1.2 export 模块与 v1.0 webpush 投递业务）
)
```

| scope | 限制（与 business-architecture §5.2 AI-043 + technical-architecture §3.5 对齐） | key | 失败响应 |
|---|---|---|---|
| `api` | 60 req/min | userId | 429 + `Retry-After: 60` |
| `login` | 5 req/15min IP 锁定 | ip | 429 + 锁定 IP 15 分钟（与 plan-deploy-nginx §3 / plan-auth §5.1 对齐：双层防护下选择更严的 5 req/15min/IP） |
| `ai` | **10 req/min/user + 60 req/h/user + 100 req/min 全局**（三重叠加） | userId + global | 429 + `Retry-After: 60`（防 OOM + 防止单用户/全局滥用） |
| `export` | 5 req/min/user | userId | 429（与 technical-architecture §3.5 对齐） |
| `webpush` | 20 req/min/user | userId | 429 |

### 2.3 audit 暴露的注解

```java
@Auditable(
    action = "task.create",     // {module}.{verb}
    resourceType = "Task",
    captureArgs = {0,1}         // 捕获方法参数索引
)
```

行为：方法正常返回 → 异步写审计；异常 → 写失败审计。

## 3. 数据模型

### 3.1 operation_logs（新建表，V26 由本模块引入）

> 注：plan-data-flyway.md 负责 V1~V34（v1.2 表结构 DDL V21~V25 + V28 auth 三表 + V29 observability 元数据 + V30 outbox_events 三列 + V31 ai_jobs 状态机扩展 + V32 daily_reports CHECK + V33 outbox 事件枚举 + V34 export 6 模块 CHECK）+ V35（chat_messages 回填，挪到 V35 避开 V28 冲突）；本模块仅在 V26 增量引入 operation_logs；V27（outbox_dead_letter）由 plan-shared-integration 引入（不破坏现有 Flyway）。

```sql
CREATE TABLE operation_logs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action              TEXT NOT NULL,                          -- task.create
    resource_type       TEXT NOT NULL,                          -- Task
    resource_id         BIGINT,
    ip                  INET,
    user_agent          TEXT,
    request_body_hash   TEXT,                                   -- 不存原文，只存 SHA-256（合规）
    response_status     SMALLINT NOT NULL,
    latency_ms          INTEGER NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trace_id            TEXT                                    -- 关联 trace
);
CREATE INDEX idx_oplogs_user_occurred ON operation_logs(user_id, occurred_at DESC);
CREATE INDEX idx_oplogs_action_occurred ON operation_logs(action, occurred_at DESC);
CREATE INDEX idx_oplogs_trace_id ON operation_logs(trace_id) WHERE trace_id IS NOT NULL;
```

- 保留周期：90 天（日终 Job 清理）
- 高敏感字段（密码 / token）由注解 `mask=true` 在切面里替换为 `***`

### 3.2 复用自 plan-data-flyway 的表

- `users` / `user_profiles` — JWT sub 字段校验
- `outbox_events` — 不直接写，但 reuse detection 告警可发事件
- `job_runs` — 审计写入异步任务执行记录

## 4. Outbox 事件

本模块**不发布业务事件**，由 auth 模块发布 `auth.token.reuse_detected`（plan-shared-infra.security.JwtRefreshTokenService 检测到 reuse 时抛 `ReuseDetectedException`，由 plan-auth 捕获并发布事件；plan-shared-integration 投递 Web Push）。

## 5. 关键验收场景（TDD 种子）

### 5.1 security

- `security_should_reject_missing_jwt`：无 Authorization 头 → 401
- `security_should_reject_expired_jwt`：`exp` 已过 → 401 + 错误码 `TOKEN_EXPIRED`
- `security_should_reject_tampered_jwt`：签名不匹配 → 401 + `TOKEN_INVALID`
- `security_should_refresh_token_rotation`：用 refresh 换新 access + 新 refresh，旧 refresh 失效
- `security_should_reject_reused_refresh_token`：旧 refresh 二次使用 → 全家族失效 + 告警
- `security_should_bcrypt_password`：BCrypt 编码 + 匹配
- `security_should_enforce_csrf_on_state_change`：POST/PUT/DELETE 缺 CSRF → 403
- `security_should_skip_csrf_on_get`：GET 请求不校验 CSRF
- `security_should_resolve_user_from_jwt`：JWT sub → UserDetails 加载 user_profiles

### 5.2 ratelimit

- `ratelimit_should_allow_under_60_per_min`：60 次请求全过
- `ratelimit_should_429_over_60_per_min`：第 61 次返回 429
- `ratelimit_should_redis_atomic`：并发 100 次只有 60 次成功（Lua 原子）
- `ratelimit_should_distinguish_user_vs_ip_key`：userId 与 ip key 互不影响
- `ratelimit_should_window_sliding`：窗口滑动后配额恢复
- `ratelimit_should_login_lock_15min`：5 次登录失败 → 第 6 次 429 + IP 锁定 15min

### 5.3 audit

- `audit_should_record_on_success`：方法正常返回 → operation_logs 写入
- `audit_should_record_on_failure`：方法抛异常 → operation_logs 写入 status=500
- `audit_should_async_write`：审计写入不阻塞主链路（<5ms）
- `audit_should_not_block_on_failure`：Redis 故障时业务不失败（降级到本地队列）
- `audit_should_mask_sensitive_args`：`mask=true` 的参数存 `***`
- `audit_should_capture_trace_id`：与 MDC trace_id 关联

## 6. 验收标准

- [ ] security 模块单测覆盖率 ≥ 90%（关键路径 100%）
- [ ] ratelimit Redis Lua 原子性压测通过（1000 并发）
- [ ] audit 失败不阻塞业务链路（P99 延迟 +0ms）
- [ ] CSRF 默认开启，开发模式可关闭（profile=dev）
- [ ] JWT 90 天轮换流程跑通（含 reuse detection 演练）
- [ ] @RequireAuth / @RateLimit / @Auditable 三注解集成到 task 模块验证
- [ ] Redis 降级路径（ratelimit 失败 → nginx IP 限流兜底）已配置

## 7. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| Redis 故障导致 ratelimit 失效 | 高 | 降级到 nginx IP 限流（plan-deploy-nginx 已配）+ fail-open 业务侧 |
| audit 异步队列堆积 | 中 | 容量上限 10 万 + 日终批量落库 + 90 天清理 |
| JWT 密钥泄露 | 高 | 90 天轮换 + reuse detection + 告警事件 |
| CSRF 与 CORS 冲突 | 中 | SameSite=Lax cookie + 显式 origin 白名单 |
| 审计写入成为攻击面 | 低 | 不存原文 + SHA-256 哈希 + 高敏字段 mask |
| 限流误伤正常用户 | 中 | 默认 60 req/min 宽松；可按 scope 调优 |
| 注解 AOP 失效（内部调用） | 中 | 文档化约束 + 集成测试覆盖 controller 入口 |

## 8. 关联文档

- 上游：
  - `plan-deploy-nginx.md`（Redis 容器 + nginx IP 限流兜底）
  - `plan-data-flyway.md`（users / user_profiles / outbox 表）
- 下游：
  - `plan-auth.md`（直接消费 security 包）
  - `plan-shared-integration.md`（同一 shared 层，并行实现）
  - `plan-01-task.md` ~ `plan-06-ai.md`（所有 controller 入口依赖本模块注解）
  - `plan-observability-backup.md`（接管 operation_logs 90 天清理）