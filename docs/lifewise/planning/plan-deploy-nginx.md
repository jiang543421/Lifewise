# plan-deploy-nginx 实施方案

## 参考资料

- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §1 架构总览（容器清单 / 网络 / 端口）
- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §2 配置与部署
- [`docs/lifewise/architecture/technical-architecture.md`](../architecture/technical-architecture.md) §4 灾备与恢复
- [`docs/lifewise/architecture/data-model-v1.2-amendment.md`](../architecture/data-model-v1.2-amendment.md) §0 主键策略
- `CLAUDE.md` §2.3 部署（Docker Compose 单机）
- `CLAUDE.md` §7 安全（Nginx TLS / HSTS / CSP / 限流）

## 参考目录

- backend：`docker-compose.yml` / `nginx/conf/` / `.env.example` / `deploy/healthcheck.sh`
- frontend：—（无 UI）

## 1. 模块边界 / 包结构

5+1 容器编排，对应 `docs/lifewise/architecture/technical-architecture.md` §1.2 容器清单：

| 容器 | 镜像 | 端口 | 资源上限 | 数据卷 | 职责 |
|---|---|---|---|---|---|
| `nginx` | `nginx:1.27-alpine` | 443 → 80 | 256MB / 0.5 CPU | `./nginx/conf`, `./nginx/certs` | TLS 终止、HSTS、CSP、静态资源、压缩、SSE keepalive |
| `app` | 自建 `eclipse-temurin:21-jre` | 8080 | 2GB / 2 CPU | `./app/config`, `./app/logs` | Spring Boot 3.3 模块化单体 |
| `db` | `postgres:15-alpine` | 5432（内网） | 1GB / 1 CPU | `pgdata`（命名卷） | 38 张业务表（26 业务主干 + 5 v1.2 新增 + 2 V26/V27 跨模块 + 3 V28 auth 三表 + 2 V29 observability 元数据）+ Outbox + 物化视图（详见 plan-data-flyway §3 表数清单；N22 已对齐 A40 断言） |
| `cache` | `redis:7-alpine` | 6379（内网） | 256MB / 0.5 CPU | `redisdata`（命名卷） | 限流、幂等键、缓存、Web Push 状态 |
| `ai` | `ollama/ollama:latest` | 11434（内网） | 4GB / 2 CPU | `ollamadata`（命名卷） | `deepseek:8b` 模型，单用户串行 |
| `backup` | `prodrigestivill/postgres-backup-local` | — | 128MB / 0.2 CPU | `pgbackups`（命名卷） | 每日 03:00 `pg_dump` + 滚动 7 天 |

### 落盘文件清单

```
docker-compose.yml              5+1 容器编排（network / volume / healthcheck）
.env.example                    环境变量样板（DB 密码 / JWT 密钥 / Ollama 模型名）
.gitignore                      屏蔽 .env / *.log / target/ / .idea/ 等
nginx/conf/nginx.conf           反向代理主配置
nginx/conf/conf.d/default.conf  TLS / HSTS / CSP / 限流 / 压缩 / SSE keepalive
nginx/certs/                    自签证书（dev）/ Let's Encrypt（prod）
deploy/healthcheck.sh           容器健康检查脚本
deploy/backup-restore.md        灾备恢复 Runbook
README.md                       一键启动 + 密钥轮换 + 故障排查
```

## 2. 网络拓扑

```
Browser ──HTTPS :443 / TLS 1.2+──→ nginx
                                    │
                                    ▼ proxy_pass :8080
                                  app
                                    ├── db:5432     （内网）
                                    ├── cache:6379  （内网）
                                    └── ai:11434    （内网）

backup ──共享卷 pgdata──→ db（每日 03:00 pg_dump → pgbackups）
```

- **隔离策略**：`db` / `cache` / `ai` 仅暴露端口给 `app`，Browser 无法直连
- **网络划分**：`lifewise-internal`（app/db/cache/ai/backup）+ `lifewise-edge`（nginx ↔ Browser）
- **健康检查**：每个容器带 `healthcheck` 配置，nginx 仅在 app healthy 后才转发

## 3. TLS / 安全头 / 限流（nginx.conf 关键配置）

```nginx
# TLS 1.2+，禁用 1.0/1.1
ssl_protocols TLSv1.2 TLSv1.3;

# HSTS
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;

# CSP（X6：补 font-src 给 Inter / Noto Sans SC Google Fonts）
add_header Content-Security-Policy "default-src 'self'; connect-src 'self' https://localhost; img-src 'self' data:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; script-src 'self'" always;

# 限流（Redis 令牌桶由 app 侧实现，此处仅做 IP 级兜底）
limit_req_zone $binary_remote_addr zone=login:10m rate=1r/m;   # E1：登录「15 分钟 5 次」= rate=1r/m + burst=5（详见下方 location）
limit_req_zone $binary_remote_addr zone=api:10m rate=60r/m;
limit_req_zone $binary_remote_addr zone=ai_chat:10m rate=10r/m;   # H1：/api/ai/chat IP 级兜底（与 app scope=ai 双重防护）

# E1：/api/auth/login 路由（IP 级 15 分钟 5 次：rate=1r/m + burst=5 + nodelay）
location = /api/auth/login {
    limit_req zone=login burst=5 nodelay;             # 前 5 个瞬时通过，第 6 个即拒（nodelay 不排队）
    proxy_pass http://app/api/auth/login;             # N1：URI 必须含 /api（nginx `=` 精确匹配 + 带 URI proxy_pass = 完全替换；不加 /api 会让 app 收到 /auth/login 触发 404）
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

# M4 + X6：/api/ai/chat 路由 + SSE keepalive 配置（location 块；X6：SSE proxy_buffering off / proxy_read_timeout 24h 必须在 location 内，否则通用 /api/ai/ 反向匹配会覆盖走默认配置导致 Ollama 流被缓冲）
location = /api/ai/chat {
    limit_req zone=ai_chat burst=2 nodelay;            # IP 级 10r/m，burst=2 防瞬时抖动
    proxy_pass http://app/api/ai/chat;                 # N1：URI 必须含 /api（同 login 注释，nginx `=` 精确匹配 + 带 URI proxy_pass = 完全替换）
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering off;                               # X6：SSE 必关 buffering，否则 Ollama 流式响应被 nginx 缓存到 4KB 才下发
    proxy_read_timeout 24h;                            # X6：SSE 长连接；Ollama 推理慢时 30s 会切断
    proxy_set_header Connection '';                    # X6：去掉 hop-by-hop 头避免 upstream close
    add_header X-Accel-Buffering no;                   # X6：禁用 X-Accel-Buffering 防止内部 buffering 重新启用
    chunked_transfer_encoding on;                      # X6：SSE chunked 编码
}

# 通用 /api/ai/* 路由（除 chat 外的 reports / jobs / consent 等）
location /api/ai/ {
    proxy_pass http://app;                              # 走 app 侧 @RateLimit scope=ai
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

# 压缩
gzip on;
gzip_types text/plain application/json text/css application/javascript application/xml;

# SSE keepalive（X6：以下全局设置仅对非 /api/ai/chat 的 location 生效；/api/ai/chat 已在 location 内单独配置避免被通用 /api/ 反向覆盖）
proxy_buffering off;
proxy_read_timeout 24h;
proxy_set_header Connection '';
```

### 速率限制策略（nginx IP 级 + app Redis 令牌桶双层）

| 接口 | 限制 | 维度 | 实现层 |
|---|---|---|---|
| 通用 API | 60 req/min | per user（JWT sub） | app Redis 令牌桶 |
| 登录 / 找回密码 | 5 req/15min 锁定 | per IP | nginx `limit_req_zone` |
| AI 报告生成 | 10 req/min | per user（防 OOM） | app Redis 令牌桶 |
| AI Chat | 10 req/min（IP） + 60 req/h（user） + 100 req/min（global）三重叠加 | per IP + per user + global | nginx `limit_req_zone ai_chat` + app Redis 令牌桶 |

## 4. 环境变量（.env.example）

```dotenv
# 数据库（180 天轮换）
DB_PASSWORD=change-me-180d

# JWT 密钥（90 天轮换）
JWT_SECRET=change-me-90d
JWT_ACCESS_TTL=PT15M
JWT_REFRESH_TTL=P30D

# Redis
REDIS_PASSWORD=change-me-180d

# Ollama
OLLAMA_MODEL=deepseek:8b
OLLAMA_BASE_URL=http://ai:11434

# Web Push VAPID 密钥（90 天轮换；生成：npx web-push generate-vapid-keys）
VAPID_PUBLIC_KEY=
VAPID_PRIVATE_KEY=
VAPID_SUBJECT=mailto:admin@lifewise.local

# 系统
TZ=Asia/Shanghai
LOG_LEVEL=INFO
SPRING_PROFILES_ACTIVE=prod
```

- 强制规则：DB 密码 180 天轮换；JWT 密钥 90 天轮换（CLAUDE.md §7.1）
- `.env` 必须 `.gitignore`；`.env.example` 入库供新人参考
- 启动校验：缺关键密钥时 app 容器直接退出（fail-fast）

## 5. 数据卷与备份

| 卷 | 容器 | 类型 | 备份策略 |
|---|---|---|---|
| `pgdata` | db | 命名卷 | backup 容器每日 `pg_dump` → `pgbackups` |
| `redisdata` | cache | 命名卷 | 启动时全量重放（限流/幂等键/缓存非关键） |
| `ollamadata` | ai | 命名卷 | 模型预下载，不备份（可重建） |
| `pgbackups` | backup | 命名卷 | **7 天滚动 + 宿主机副本**（RPO 24h / RTO <30min） |
| `./app/config` | app | 绑定挂载 | 配置走 Git |
| `./app/logs` | app | 绑定挂载 | 按日切割，保留 30 天 |
| `./nginx/conf`, `./nginx/certs` | nginx | 绑定挂载 | 配置走 Git，证书走 certbot |

## 6. 关键验收场景（TDD 种子）

### 容器启动

- `deploy_infra_should_pass_healthcheck`：`docker compose up -d` 后所有容器 30 秒内 `healthy`
- `deploy_infra_should_serve_health_endpoint`：`curl -k https://localhost/health` 返回 200
- `deploy_infra_should_expose_internal_ports_only`：浏览器无法直连 db/cache/ai 端口（连接被拒）

### TLS / 安全头

- `deploy_tls_should_return_hsts_header`：`curl -I -k https://localhost/` 返回 `Strict-Transport-Security`
- `deploy_tls_should_return_csp_header`：返回 `Content-Security-Policy`
- `deploy_tls_should_disable_tls10_11`：testssl.sh 仅 TLS 1.2/1.3 通过

### 限流

- `deploy_ratelimit_should_429_after_5_logins`：5 次错误登录后第 6 次返回 429
- `deploy_ratelimit_should_429_after_60_api`：普通 API 第 61 次请求返回 429

### 备份

- `deploy_backup_should_create_daily_dump`：模拟 03:00 触发 → `pgbackups/` 出现新 dump 文件
- `deploy_backup_should_rotate_7_days`：7 天前的 dump 被自动清理

### 配置

- `deploy_config_should_validate_yaml`：`docker compose config` 校验无错
- `deploy_config_should_reject_missing_secrets`：缺 JWT_SECRET 时 app 容器启动失败

## 7. 验收标准

- [ ] `docker compose up -d` 一键启动所有容器
- [ ] 所有容器 healthcheck 通过
- [ ] TLS / HSTS / CSP 头验证通过（testssl.sh）
- [ ] 限流策略生效（429 触发条件满足）
- [ ] 数据库 / 缓存 / AI 端口不暴露公网
- [ ] `.env.example` 完整；README 说明密钥轮换周期
- [ ] `docker compose config` 校验无错
- [ ] 备份恢复 Runbook 跑通（手动模拟一次恢复）

## 8. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| Ollama 内存压力（4GB 上限） | 中 | AI 报告限流 10 req/min，单用户串行；OOM 时仅丢该次请求，不影响其他模块 |
| 备份失败未告警 | 高 | backup 容器挂掉时通过 docker event 通知；日终 cron 检查 `pgbackups/` 是否当天有产物 |
| .env 误提交 | 高 | `.gitignore` + CI 检查 + Secret Rotation 周期 |
| TLS 证书过期 | 中 | certbot 自动续期 + 监控证书剩余天数 < 30 天告警 |
| nginx 单点故障 | 低 | 个人版单机部署接受；CLAUDE.md §1.4 范围限定 |

## 9. 关联文档

- 上游：`../architecture/technical-architecture.md` §1.2 容器清单
- 下游：`plan-data-flyway.md`（依赖 db 容器就绪）
- 后续阶段：`plan-observability-backup.md`（接管监控与备份）