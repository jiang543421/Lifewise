# Lifewise 灾备恢复 Runbook

> **目标**：当 db 容器 / `pgdata` 卷损坏或需要时间点恢复时，按本 Runbook 在 30 分钟内恢复服务。
>
> **适用范围**：docker-compose 单机部署（步骤1A 之后 + 后续阶段）

---

## 1. 备份策略（约定）

| 项 | 值 | 来源 |
|---|---|---|
| 备份方式 | `pg_dump` 自定义格式 (`-Fc`) + gzip | technical-architecture §5.3 |
| 备份时间 | 每日 03:00 | technical-architecture §5.3 |
| 容器保留 | 7 天滚动 | technical-architecture §5.3 + plan-deploy-nginx §8 |
| 卷 | `pgbackups`（命名卷），路径 `/backups` | docker-compose `backup` 服务 |
| 命名规范 | `lifewise-YYYY-MM-DD-HHMM.dump.gz` | prodrigestivill 默认 |
| RPO | 24 小时 | technical-architecture §5.3 |
| RTO | < 30 分钟 | technical-architecture §5.3 |
| 宿主机副本 | 用户自托管（推荐 Time Machine / rclone） | technical-architecture §5.3 |

---

## 2. 验证备份是否存在

```bash
# 进入 backup 容器列出 dump
docker compose exec backup ls -lh /backups

# 期望看到至少 1 个 .dump.gz 文件（24h 内）
```

若列表为空，**先不要走恢复流程**，而排查：

```bash
# 1. 容器是否健康
docker compose ps backup

# 2. 手动触发一次立即备份（prodrigestivill 支持）
docker compose exec backup backup

# 3. 查看 backup 容器日志
docker compose logs --tail=100 backup
```

---

## 3. 恢复流程（标准 8 步）

### 3.1 停机

```bash
cd <repo-root>
docker compose down          # 停所有容器，保留卷
```

> 注：`docker compose down` 默认**不**删除命名卷（`pgdata` / `pgbackups` / `redisdata` / `ollamadata` 全部保留）。

### 3.2 选择 dump

```bash
ls -lh /var/lib/docker/volumes/lifewise-pgbackups/_data/
# 或直接进容器看
docker compose run --rm backup ls -lh /backups
```

记下目标 dump 文件名，例如 `lifewise-2026-07-30-0300.dump.gz`。

### 3.3 准备新数据库目录

```bash
docker volume rm lifewise-pgdata     # ⚠️ 破坏性操作：会清空当前 db 数据
# 或保留以做对照：
# docker volume create lifewise-pgdata-restore
```

### 3.4 仅启动 db 容器（用于恢复）

```bash
docker compose up -d db
# 等待 db 健康
docker compose ps db
# 直到 STATUS = (healthy)
```

### 3.5 执行 pg_restore

```bash
docker compose exec db bash -c '
  set -euo pipefail
  DUMP="/backups/lifewise-2026-07-30-0300.dump.gz"
  gunzip -c "$DUMP" | pg_restore \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    --no-owner \
    --role="$POSTGRES_USER" \
    --jobs=4 \
    --verbose
'
```

### 3.6 启动剩余服务

```bash
docker compose up -d
```

### 3.7 校验

```bash
# 1. app 健康检查端点
curl -k https://localhost/actuator/health

# 2. 关键表行数（不少于恢复前）
docker compose exec db psql -U lifewise -d lifewise -c "
  SELECT
    (SELECT COUNT(*) FROM users)              AS users,
    (SELECT COUNT(*) FROM tasks)              AS tasks,
    (SELECT COUNT(*) FROM daily_reports)      AS daily_reports,
    (SELECT MAX(created_at) FROM outbox_events) AS last_event;
"

# 3. 部署脚本侧健康
bash deploy/healthcheck.sh
```

### 3.8 失败回滚

若恢复后发现数据不一致：

```bash
docker compose down
docker volume rm lifewise-pgdata
# 用更早的 dump 回到 §3.4
```

---

## 4. 升级前自动备份

`docker-compose.yml` 中 `backup` 容器配置 `SCHEDULE: "@daily"`、`BACKUP_KEEP_DAYS: "7"`、`BACKUP_KEEP_MINS: "10080"`（7×24×60=10080 分钟）。升级流程：

```bash
# 触发一次立即备份
docker compose exec backup backup

# 验证新 dump 出现
docker compose exec backup ls -lh /backups | tail -5

# 再升级
docker compose pull
docker compose up -d
```

---

## 5. 告警与监控

| 指标 | 阈值 | 建议处置 |
|---|---|---|
| 当天无 dump | > 24h | 检查 backup 容器是否运行；查看 `docker compose logs backup` |
| dump 体积 < 1MB | 异常 | 排查是否有源表数据被清空或 backup 容器权限问题 |
| `pgdata` 磁盘剩余 | < 5GB | 清理 `pgbackups/` 旧 dump 或扩容宿主机卷 |
| backup 容器 OOMKilled | — | 提高 deploy.resources.limits.memory 至 256M |

> 个人版无 Prometheus；以上阈值由用户在 host cron 或简易脚本中实现，详见 plan-observability-backup.md（本步骤不交付监控）。

---

## 6. 与架构契约对齐

- technical-architecture.md §5.3 灾备与恢复
- plan-deploy-nginx.md §5 数据卷与备份
- shared-strings.md §5 cron 表达式（cron 不在本步骤实现）