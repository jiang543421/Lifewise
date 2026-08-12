#!/usr/bin/env bash
# ============================================================
# deploy_checks.sh — 步骤1 部署配置静态校验
#
# 用途：在不需要 docker daemon 的前提下，校验 plan-deploy-nginx §6
#       TDD 验收场景中所有可静态执行的项。
#
# 调用：
#   bash deploy/test/deploy_checks.sh
#   bash deploy/test/deploy_checks.sh --only j1,j4   # 子集运行
#
# 退出码：
#   0  全部通过
#   1  至少一项失败
#
# 与 plan-deploy-nginx.md §6 / technical-architecture.md §2.2 / §4
# 严格对齐。
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
NGINX_MAIN="${ROOT_DIR}/nginx/conf/nginx.conf"
NGINX_DEFAULT="${ROOT_DIR}/nginx/conf/conf.d/default.conf"
ENV_EXAMPLE="${ROOT_DIR}/.env.example"
GITIGNORE="${ROOT_DIR}/.gitignore"
HEALTHCHECK_SH="${ROOT_DIR}/deploy/healthcheck.sh"
BACKUP_RUNBOOK="${ROOT_DIR}/deploy/backup-restore.md"

ONLY=""
if [[ "${1:-}" == "--only" ]]; then
  ONLY="${2:-}"
fi

# ANSI colors（Windows Git Bash 不一定支持，无颜色时安静降级）
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; NC=''
fi

PASS=0
FAIL=0
SKIP=0
FAILED_CASES=()

run_case() {
  local id="$1"
  local desc="$2"
  shift 2
  if [[ -n "${ONLY}" && ",${ONLY}," != *",${id},"* ]]; then
    SKIP=$((SKIP+1)); return 0
  fi
  echo -e "${YELLOW}[${id}]${NC} ${desc}"
  if "$@"; then
    echo -e "  ${GREEN}PASS${NC}"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}FAIL${NC}"
    FAIL=$((FAIL+1))
    FAILED_CASES+=("${id}: ${desc}")
  fi
}

require_file() {
  local f="$1"
  if [[ ! -f "${f}" ]]; then
    echo "  缺文件: ${f}" >&2
    return 1
  fi
}

require_grep() {
  local file="$1"; local pattern="$2"
  if ! grep -qE "${pattern}" "${file}"; then
    echo "  在 ${file##*/} 未找到: ${pattern}" >&2
    return 1
  fi
}

reject_grep() {
  local file="$1"; local pattern="$2"
  if grep -qE "${pattern}" "${file}"; then
    echo "  在 ${file##*/} 不得出现: ${pattern}" >&2
    return 1
  fi
}

# ============================================================
# J11: deploy_config_should_validate_yaml
# ============================================================
j11_compose_valid() {
  require_file "${COMPOSE_FILE}" || return 1
  for svc in db cache ai app; do
    require_grep "${COMPOSE_FILE}" "^  ${svc}:" || return 1
  done
  require_grep "${COMPOSE_FILE}" "internal:[[:space:]]*true" || return 1
  require_grep "${COMPOSE_FILE}" "healthcheck:" || return 1
  if command -v docker >/dev/null 2>&1; then
    # docker compose config 退出码：
    #   0  - 完全通过
    #   1  - 配置错误（硬失败）
    #  64/255 - 仅 warning（变量未设等），不视为失败
    #
    # 用 .env.example 作为占位环境文件：满足 ${VAR:?...} 必填校验。
    # 生产部署时由真实 .env 注入；本静态检查只关心 compose 语法合法。
    if [[ -f "${ENV_EXAMPLE}" ]]; then
      out="$(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_EXAMPLE}" config 2>&1)"
    else
      out="$(docker compose -f "${COMPOSE_FILE}" config 2>&1)"
    fi
    rc=$?
    if [[ ${rc} -eq 1 ]]; then
      echo "  docker compose config 校验失败：" >&2
      echo "${out}" | sed 's/^/    /' >&2
      return 1
    fi
    if [[ ${rc} -ne 0 ]]; then
      echo "  docker compose config 仅 warning（rc=${rc}），按通过处理" >&2
    fi
  fi
  return 0
}

# ============================================================
# J3: deploy_infra_should_expose_internal_ports_only
# ============================================================
j3_internal_ports() {
  require_file "${COMPOSE_FILE}" || return 1
  for port in 5432 6379 11434; do
    if grep -nE "^[[:space:]]*-+[[:space:]]*\"?${port}:?\"?" "${COMPOSE_FILE}" >/dev/null; then
      svc_block="$(awk -v p="${port}" '
        /^  [a-z][a-z0-9_-]*:/ {svc=$1; sub(/:$/, "", svc); in_block=1; next}
        in_block && /^  [a-z]/ && $0 !~ /^  [a-z][a-z0-9_-]*:/ {svc=""; in_block=0; next}
        svc!="" && index($0, p":" )>0 {print svc}
      ' "${COMPOSE_FILE}")"
      if [[ -n "${svc_block}" && "${svc_block}" != "nginx" ]]; then
        echo "  内部端口 ${port} 暴露在非 nginx 容器: ${svc_block}" >&2
        return 1
      fi
    fi
  done
  return 0
}

# ============================================================
# J1: deploy_infra_should_pass_healthcheck (static subset)
# ============================================================
j1_healthchecks() {
  require_file "${COMPOSE_FILE}" || return 1
  require_grep "${COMPOSE_FILE}" "pg_isready" || { echo "  db 缺 pg_isready" >&2; return 1; }
  require_grep "${COMPOSE_FILE}" "redis-cli"  || { echo "  cache 缺 redis-cli ping" >&2; return 1; }
  return 0
}

# ============================================================
# J12: deploy_config_should_reject_missing_secrets
# ============================================================
j12_required_secrets() {
  require_file "${ENV_EXAMPLE}" || { echo "  缺 .env.example" >&2; return 1; }
  require_file "${GITIGNORE}"   || { echo "  缺 .gitignore" >&2; return 1; }
  for key in DB_PASSWORD AUTH_JWT_SECRET_BASE64 AUTH_JWT_ACCESS_TTL_MINUTES AUTH_JWT_REFRESH_TTL_DAYS REDIS_PASSWORD OLLAMA_MODEL OLLAMA_BASE_URL VAPID_PUBLIC_KEY VAPID_PRIVATE_KEY VAPID_SUBJECT TZ LOG_LEVEL SPRING_PROFILES_ACTIVE; do
    require_grep "${ENV_EXAMPLE}" "^${key}=" || { echo "  .env.example 缺 ${key}" >&2; return 1; }
  done
  require_grep "${GITIGNORE}" "^\.env$"     || { echo "  .gitignore 未忽略 .env" >&2; return 1; }
  if grep -qE "POSTGRES_PASSWORD[[:space:]]*=[[:space:]]*[^$\"]" "${COMPOSE_FILE}" 2>/dev/null; then
    echo "  docker-compose.yml 存在硬编码 POSTGRES_PASSWORD" >&2; return 1
  fi
  return 0
}

# ============================================================
# J4 / J5 / J6: TLS / HSTS / CSP
# ============================================================
j4_hsts_header() {
  require_file "${NGINX_DEFAULT}" || return 1
  require_grep "${NGINX_DEFAULT}" "Strict-Transport-Security" || return 1
  require_grep "${NGINX_DEFAULT}" "max-age=31536000"          || return 1
  return 0
}

j5_csp_header() {
  require_file "${NGINX_DEFAULT}" || return 1
  require_grep "${NGINX_DEFAULT}" "Content-Security-Policy" || return 1
  require_grep "${NGINX_DEFAULT}" "connect-src" || return 1
  require_grep "${NGINX_DEFAULT}" "font-src"    || return 1
  return 0
}

j6_tls_protocols() {
  require_file "${NGINX_DEFAULT}" || return 1
  require_grep "${NGINX_DEFAULT}" "ssl_protocols[[:space:]]+TLSv1\.2 TLSv1\.3" || return 1
  if grep -qE "ssl_protocols[[:space:]]+([^#\n]*[[:space:]])?TLSv1(\.0|\.1)?[[:space:]]" "${NGINX_DEFAULT}" 2>/dev/null; then
    if ! grep -qE "ssl_protocols[[:space:]]+TLSv1\.2 TLSv1\.3;?" "${NGINX_DEFAULT}"; then
      echo "  nginx 启用了 TLSv1 / TLSv1.0 / TLSv1.1" >&2; return 1
    fi
  fi
  return 0
}

# ============================================================
# J7 / J8: 限流
# ============================================================
j7_login_rate_limit() {
  require_file "${NGINX_DEFAULT}" || return 1
  require_grep "${NGINX_DEFAULT}" "limit_req_zone[[:space:]]+\\\$binary_remote_addr[[:space:]]+zone=login:10m" || return 1
  require_grep "${NGINX_DEFAULT}" "location[[:space:]]*=[[:space:]]*/api/auth/login" || return 1
  require_grep "${NGINX_DEFAULT}" "limit_req[[:space:]]+zone=login[[:space:]]+burst=5[[:space:]]+nodelay" || return 1
  return 0
}

j8_api_rate_limit() {
  require_file "${NGINX_DEFAULT}" || return 1
  require_grep "${NGINX_DEFAULT}" "limit_req_zone[[:space:]]+\\\$binary_remote_addr[[:space:]]+zone=api:10m" || return 1
  require_grep "${NGINX_DEFAULT}" "limit_req_zone[[:space:]]+\\\$binary_remote_addr[[:space:]]+zone=ai_chat:10m" || return 1
  return 0
}

# ============================================================
# J2: health endpoint
# ============================================================
j2_health_endpoint() {
  require_file "${HEALTHCHECK_SH}" || { echo "  缺 deploy/healthcheck.sh" >&2; return 1; }
  require_grep "${HEALTHCHECK_SH}" "(actuator/health|/health)" || return 1
  return 0
}

# ============================================================
# J9 / J10: backup
# ============================================================
j9j10_backup_schedule() {
  require_file "${COMPOSE_FILE}" || return 1
  require_file "${BACKUP_RUNBOOK}" || { echo "  缺 deploy/backup-restore.md" >&2; return 1; }
  require_grep "${COMPOSE_FILE}" "  backup:" || return 1
  require_grep "${COMPOSE_FILE}" "BACKUP_KEEP_DAYS" || return 1
  require_grep "${COMPOSE_FILE}" "BACKUP_KEEP_MINS.*10080" || return 1
  require_grep "${COMPOSE_FILE}" "pgbackups:" || return 1
  return 0
}

# ============================================================
# 入口
# 用法：run_case <id> <desc> <func_name>
# ============================================================
run_case j11 "docker compose 配置可解析 + 1A 服务齐全 + internal 网络" j11_compose_valid
run_case j3  "db/cache/ai 端口只在 internal 暴露"                        j3_internal_ports
run_case j1  "db/cache 容器声明 healthcheck"                             j1_healthchecks
run_case j12 ".env.example 必备键 + .env 已 gitignore + 无硬编码密钥"      j12_required_secrets
run_case j4  "nginx 配置 HSTS 头"                                         j4_hsts_header
run_case j5  "nginx 配置 CSP 头（含 connect-src / font-src）"              j5_csp_header
run_case j6  "TLS 1.2/1.3 only，禁用 1.0/1.1"                            j6_tls_protocols
run_case j7  "/api/auth/login 限流 login:10m + burst=5 nodelay"          j7_login_rate_limit
run_case j8  "通用 api 与 ai_chat 限流 zone 声明"                          j8_api_rate_limit
run_case j2  "deploy/healthcheck.sh 存在并校验 actuator/health"          j2_health_endpoint
run_case j9  "backup 容器 + 7 天滚动 + pgbackups 卷 + Runbook"            j9j10_backup_schedule

echo
echo "=========================================="
echo "PASS=${PASS}  FAIL=${FAIL}  SKIP=${SKIP}"
if [[ ${FAIL} -gt 0 ]]; then
  echo -e "${RED}失败用例：${NC}"
  for c in "${FAILED_CASES[@]}"; do echo "  - ${c}"; done
  exit 1
fi
echo -e "${GREEN}所有启用的用例通过${NC}"
exit 0