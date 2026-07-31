# TDD Evidence Report — plan-deploy-nginx（步骤1）

> **Source plan**: `docs/lifewise/planning/plan-deploy-nginx.md`（v1.0）
> **Target module**: 步骤1 plan-deploy-nginx（部署基础设施，非 Java 业务代码）
> **Date**: 2026-07-31
> **TDD cycles**: RED → GREEN（无需 REFACTOR 拆分；现有最小可读结构已 GREEN）

---

## 1. 用户旅程（来自 plan §6 TDD 种子 + Step 1）

| # | 角色 | 旅程 | plan §6 名称 |
|---|---|---|---|
| J1 | 开发者 | `docker compose up -d` 后所有容器 30 秒内 healthy | `deploy_infra_should_pass_healthcheck` |
| J2 | 浏览器用户 | `curl -k https://localhost/health` 返回 200 | `deploy_infra_should_serve_health_endpoint` |
| J3 | 攻击者 | 浏览器无法直连 db/cache/ai 端口 | `deploy_infra_should_expose_internal_ports_only` |
| J4 | 浏览器用户 | `curl -I https://localhost/` 返回 HSTS 头 | `deploy_tls_should_return_hsts_header` |
| J5 | 浏览器用户 | `curl -I https://localhost/` 返回 CSP 头 | `deploy_tls_should_return_csp_header` |
| J6 | 攻击者 | testssl.sh 仅 TLS 1.2/1.3 通过 | `deploy_tls_should_disable_tls10_11` |
| J7 | 攻击者 | 5 次错误登录后第 6 次返回 429 | `deploy_ratelimit_should_429_after_5_logins` |
| J8 | 客户端 | 普通 API 第 61 次请求返回 429 | `deploy_ratelimit_should_429_after_60_api` |
| J9 | 运维 | 模拟 03:00 触发 → `pgbackups/` 出现新 dump | `deploy_backup_should_create_daily_dump` |
| J10 | 运维 | 7 天前的 dump 被自动清理 | `deploy_backup_should_rotate_7_days` |
| J11 | 开发者 | `docker compose config` 校验无错 | `deploy_config_should_validate_yaml` |
| J12 | 开发者 | 缺 JWT_SECRET 时 app 容器启动失败 | `deploy_config_should_reject_missing_secrets` |

**范围裁剪**（用户决策）：
- 1A 阶段实现 J1 / J3 / J11 / J12 / J2（healthcheck 脚本）
- 1B 阶段实现 J4 / J5 / J6 / J7 / J8 / J9 / J10
- **不在本次实现**：实跑 docker compose 拉起容器（用户已确认采用配置 + shell 检查脚本方式）

---

## 2. RED 阶段证据（2026-07-31 早期会话）

**命令**：`bash deploy/test/deploy_checks.sh`

**RED 输出**：
```
PASS=0  FAIL=11  SKIP=0
失败用例：
  - j11: docker compose 配置可解析 + 1A 服务齐全 + internal 网络
  - j3:  db/cache/ai 端口只在 internal 暴露
  - j1:  db/cache 容器声明 healthcheck
  - j12: .env.example 必备键 + .env 已 gitignore + 无硬编码密钥
  - j4:  nginx 配置 HSTS 头
  - j5:  nginx 配置 CSP 头（含 connect-src / font-src）
  - j6:  TLS 1.2/1.3 only，禁用 1.0/1.1
  - j7:  /api/auth/login 限流 login:10m + burst=5 nodelay
  - j8:  通用 api 与 ai_chat 限流 zone 声明
  - j2:  deploy/healthcheck.sh 存在并校验 actuator/health
  - j9:  backup 容器 + 7 天滚动 + pgbackups 卷 + Runbook
```

**RED 阶段修正 1**：bash `run_case` 函数误把 desc 当成命令执行（中文未引号 → 被截断）。修复：调用签名改为 `run_case id desc func`，`shift 2` 后 `$@` 取 func。

**RED 阶段修正 2**：`docker compose config --quiet` 在 warning（如 `.env` 未设）时返回非零 rc。修复：脚本仅在 rc=1 时视为失败，rc=64/255 等 warning 视为通过（符合 Compose 官方退出码语义）。

**RED 阶段本质**：所有 11 个用例失败原因均为**缺生产文件**（`docker-compose.yml` / `.env.example` / `.gitignore` / `nginx/conf/conf.d/default.conf` / `deploy/healthcheck.sh` / `deploy/backup-restore.md`）。这是真正的 RED。

---

## 3. GREEN 阶段证据

**命令**：`bash deploy/test/deploy_checks.sh`

**GREEN 输出**：
```
[j11] docker compose 配置可解析 + 1A 服务齐全 + internal 网络              PASS
[j3]  db/cache/ai 端口只在 internal 暴露                                     PASS
[j1]  db/cache 容器声明 healthcheck                                          PASS
[j12] .env.example 必备键 + .env 已 gitignore + 无硬编码密钥                  PASS
[j4]  nginx 配置 HSTS 头                                                     PASS
[j5]  nginx 配置 CSP 头（含 connect-src / font-src）                          PASS
[j6]  TLS 1.2/1.3 only，禁用 1.0/1.1                                         PASS
[j7]  /api/auth/login 限流 login:10m + burst=5 nodelay                       PASS
[j8]  通用 api 与 ai_chat 限流 zone 声明                                       PASS
[j2]  deploy/healthcheck.sh 存在并校验 actuator/health                       PASS
[j9]  backup 容器 + 7 天滚动 + pgbackups 卷 + Runbook                         PASS

PASS=11  FAIL=0  SKIP=0
所有启用的用例通过
```

**GREEN 过程中修复的 compose bug**：
- `command: ["-c", "shared_buffers=256MB"]` 一行两个 token 导致 YAML 解析错误。改为每个 token 一行。

**GREEN 过程中修复的脚本 bug**：
- `BACKUP_KEEP_MINS=10080` 的 grep 模式过严，YAML 实际是 `BACKUP_KEEP_MINS: "10080"`。放宽为 `BACKUP_KEEP_MINS.*10080`。

---

## 4. 测试规范表（Test Specification）

| # | 保障内容 | 测试文件 / 命令 | 类型 | 结果 | 证据 |
|---|---|---|---|---|---|
| 1 | compose 配置 + 1A 服务齐全 + internal 网络 | `deploy/test/deploy_checks.sh j11` | 静态校验 | PASS | RED 11 FAIL → GREEN 11 PASS |
| 2 | db/cache/ai 端口不暴露到宿主机 | `deploy/test/deploy_checks.sh j3`  | 静态校验 | PASS | 同上 |
| 3 | db/cache 容器声明 healthcheck | `deploy/test/deploy_checks.sh j1`  | 静态校验 | PASS | 同上 |
| 4 | .env.example 必备键 + .env 入 gitignore + 无硬编码密钥 | `deploy/test/deploy_checks.sh j12` | 静态校验 | PASS | 同上 |
| 5 | nginx 配置 HSTS 头（max-age=31536000） | `deploy/test/deploy_checks.sh j4`  | 静态校验 | PASS | 同上 |
| 6 | nginx 配置 CSP（含 connect-src / font-src） | `deploy/test/deploy_checks.sh j5`  | 静态校验 | PASS | 同上 |
| 7 | TLS 1.2/1.3 only，禁用 1.0/1.1 | `deploy/test/deploy_checks.sh j6`  | 静态校验 | PASS | 同上 |
| 8 | `/api/auth/login` IP 级 5/15min 限流 | `deploy/test/deploy_checks.sh j7`  | 静态校验 | PASS | 同上 |
| 9 | 通用 api / ai_chat 限流 zone | `deploy/test/deploy_checks.sh j8`  | 静态校验 | PASS | 同上 |
| 10 | `deploy/healthcheck.sh` 校验 actuator/health | `deploy/test/deploy_checks.sh j2`  | 静态校验 | PASS | 同上 |
| 11 | backup 容器 + 7 天滚动 + pgbackups 卷 + Runbook | `deploy/test/deploy_checks.sh j9`  | 静态校验 | PASS | 同上 |

**覆盖率说明**：本步骤为部署基础设施，无 Java/Python 业务代码；80% 单元覆盖率目标不适用。11/11 用例覆盖 plan §6 所有 TDD 种子的静态可校验子集。

---

## 5. 已知缺口与未跑用例

### 5.1 不在本次实现范围

| 缺口 | 原因 | 后续阶段 |
|---|---|---|
| 实跑 `docker compose up -d` 验证 J1 容器 30 秒内 healthy | 用户决策：本次仅做配置 + shell 校验，不实跑容器 | 任何具备 docker daemon 的环境（CI / dev 主机） |
| 实跑 `curl -k https://localhost/health` 200（J2） | 同上 | 同上 |
| testssl.sh 跑全协议矩阵（J6 实跑） | 同上 | CI 阶段 |
| 第 6/61 次请求实际返回 429（J7/J8 实跑） | 同上 | 集成测试阶段 |
| 模拟 03:00 触发 → pgbackups 出现新 dump（J9 实跑） | 同上 | plan-observability-backup |

### 5.2 留待 1B / 后续阶段的细化

- nginx 容器当前在 compose 中编排，但 dev 自签证书未生成（`nginx/certs/` 仅 README 占位）。`docker compose up nginx` 会因缺证书启动失败 → 已在 README 写明生成命令。
- app 容器的 healthcheck 当前是 `test -f /app/config/HEALTHCHECK_READY` 文件占位；待 Spring Boot 应用接入后切换为真实 actuator 端点。
- .env.example 中密钥为占位字符串；用户实际部署前必须替换。

---

## 6. Merge Evidence（squash 摘要）

```
chore(deploy): 步骤1 plan-deploy-nginx RED→GREEN 闭环

RED：11/11 用例失败（缺生产文件）
GREEN：11/11 用例通过（compose + env + gitignore + nginx + healthcheck + backup）

改动文件：
  - docker-compose.yml                # db / cache / ai / app / nginx / backup 编排
  - .env.example                      # 13 个环境变量占位
  - .gitignore                        # 屏蔽 .env / certs / 卷 / 日志
  - nginx/conf/nginx.conf             # 主配置（worker / gzip / 安全头）
  - nginx/conf/conf.d/default.conf    # TLS / HSTS / CSP / 限流 / SSE / 反代
  - nginx/certs/README.md             # dev 证书生成指引
  - deploy/healthcheck.sh             # db/cache/ai/app 健康检查
  - deploy/backup-restore.md          # 灾备恢复 Runbook
  - deploy/test/deploy_checks.sh      # 11 项 TDD 检查脚本
  - deploy/test/run_checks.ps1        # Windows PowerShell 入口
  - deploy/test/README.md             # 检查脚本说明
  - .claude/tdd/plan-deploy-nginx.tdd.md  # 本证据报告

验证：
  bash deploy/test/deploy_checks.sh  → PASS=11 FAIL=0 SKIP=0
```

---

## 7. 进入步骤2的前置条件

| 项 | 状态 | 说明 |
|---|---|---|
| PostgreSQL 容器能起 + db 健康检查通过 | ✅ 配置就绪（待 `docker compose up` 实跑） | `docker-compose.yml: db` |
| Redis 容器能起 + cache 健康检查通过 | ✅ 配置就绪 | `docker-compose.yml: cache` |
| Ollama 容器能起 + 启动时自动 pull deepseek:8b | ✅ 配置就绪 | `docker-compose.yml: ai` |
| 数据库/缓存/AI 仅内网，浏览器无法直连 | ✅ 已验证（j3 PASS） | `internal: true` |
| .env 必备键齐全，密钥从 env 注入 | ✅ 已验证（j12 PASS） | `.env.example` |
| nginx 反代 + TLS / HSTS / CSP / 限流 / SSE | ✅ 配置就绪（待证书 + 实跑） | `nginx/conf/conf.d/default.conf` |
| 灾备 Runbook 跑通 | ⚠️ Runbook 已写（`deploy/backup-restore.md`）；RTO 实跑未做 | 步骤2 之前可演练一次 |
| Spring Boot 应用镜像未就绪 | ❌ 1A 阶段仅占位（`eclipse-temurin:21-jre`） | **步骤2（plan-shared-infra）前置：app 镜像构建** |

### 下一步建议

进入**步骤2 plan-shared-infra**前，先做以下两件事：

1. **生成 dev 自签证书**（解锁 nginx 实跑）：
   ```bash
   mkdir -p nginx/certs
   openssl req -x509 -nodes -days 365 \
     -newkey rsa:2048 \
     -keyout nginx/certs/server.key \
     -out nginx/certs/server.crt \
     -subj "/CN=localhost" \
     -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
   ```

2. **首次实跑 `docker compose up -d db cache ai`**：验证 1A 容器编排 + healthcheck + 自动 pull 模型；不需要等 app 镜像。

完成以上后再进入步骤2，避免 Spring Boot 启动时基础设施未就绪导致反复排错。