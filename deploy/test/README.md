# 步骤1 部署配置检查脚本

> **运行方式**：`bash deploy/test/deploy_checks.sh`（推荐 Git Bash / WSL / Linux）
> **Windows PowerShell 入口**：`powershell -ExecutionPolicy Bypass -File deploy/test/run_checks.ps1 [-Only j1,j11]`
> **子集运行**：`bash deploy/test/deploy_checks.sh --only j1,j4`
> **退出码**：0=全部通过，1=至少一项失败

## 覆盖 plan-deploy-nginx.md §6 TDD 验收场景

| 用例 ID | 描述 | 计划 §6 名称 |
|---|---|---|
| `j1`  | db/cache 容器声明 healthcheck | `deploy_infra_should_pass_healthcheck`（静态子集） |
| `j2`  | `deploy/healthcheck.sh` 存在并校验 actuator/health | `deploy_infra_should_serve_health_endpoint` |
| `j3`  | db/cache/ai 端口仅在 internal 网络暴露 | `deploy_infra_should_expose_internal_ports_only` |
| `j4`  | nginx 配置 HSTS 头 | `deploy_tls_should_return_hsts_header` |
| `j5`  | nginx 配置 CSP 头（含 connect-src / font-src） | `deploy_tls_should_return_csp_header` |
| `j6`  | TLS 1.2/1.3 only，禁用 1.0/1.1 | `deploy_tls_should_disable_tls10_11` |
| `j7`  | `/api/auth/login` 限流 login:10m + burst=5 nodelay | `deploy_ratelimit_should_429_after_5_logins` |
| `j8`  | 通用 api 与 ai_chat 限流 zone 声明 | `deploy_ratelimit_should_429_after_60_api` |
| `j9`  | backup 容器 + 7 天滚动 + pgbackups 卷 + Runbook | `deploy_backup_should_create_daily_dump` + `..._rotate_7_days` |
| `j11` | docker compose 配置可解析 + 1A 服务齐全 + internal 网络 | `deploy_config_should_validate_yaml` |
| `j12` | .env.example 必备键 + .env 已 gitignore + 无硬编码密钥 | `deploy_config_should_reject_missing_secrets` |

## 不在静态校验范围内（需 docker 实跑）

- `j1` 容器 30 秒内 healthy（实际拉起）
- `j2` `curl -k https://localhost/health` 200（需 app 容器 + TLS）
- `j6` testssl.sh 跑全协议矩阵
- `j7` / `j8` 第 6 / 61 次请求实际返回 429
- `j9` 模拟 03:00 触发 → pgbackups 出现新 dump

这些用例由后续 CI / 手动 `docker compose up` 跑通，不在 `deploy_checks.sh` 范围内。

## 与架构契约对齐

- `docker-compose.yml` 容器清单 → `technical-architecture.md §1.2`
- nginx 配置（HSTS / CSP / TLS / 限流 / SSE） → `technical-architecture.md §2.2`
- PostgreSQL / Redis / Ollama 命令行参数 → `technical-architecture.md §4.2 / §4.3 / §4.4`
- backup 容器配置 → `technical-architecture.md §4.5` + `§5.3`
- .env 必备键 → `technical-architecture.md §5.4`
- 限流 burst / rate 数值 → `docs/lifewise/planning/references/shared-strings.md §6`
- nginx location 精确匹配 → `shared-strings.md §9`