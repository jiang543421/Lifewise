#!/usr/bin/env bash
# ============================================================
# deploy/healthcheck.sh — 容器健康检查脚本
#
# 用途：
#   - Docker compose healthcheck（app / ai / 外部巡检）
#   - 检查：db / cache / ai / app 四容器的健康端点
#
# 调用：
#   bash deploy/healthcheck.sh                # 全检
#   bash deploy/healthcheck.sh db             # 单服务
#   bash deploy/healthcheck.sh app https://localhost/actuator/health
#
# 退出码：
#   0  - 全部健康
#   1  - 至少一项失败
#
# 严格遵循：
#   - plan-deploy-nginx.md §6 deploy_infra_should_serve_health_endpoint
#   - technical-architecture.md §3.8 /health 自定义检查
#
# 注意：脚本在容器内通过 Docker 网络访问 db:5432 / cache:6379 / ai:11434，
#       通过宿主机映射端口访问自身 actuator（如配置 host 网络）。
# ============================================================
set -uo pipefail

# ANSI
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; NC=''
fi

# ---- 默认值 ----
DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${POSTGRES_USER:-lifewise}"
DB_NAME="${POSTGRES_DB:-lifewise}"

CACHE_HOST="${CACHE_HOST:-cache}"
CACHE_PORT="${CACHE_PORT:-6379}"
CACHE_PASSWORD="${REDIS_PASSWORD:-}"

AI_HOST="${AI_HOST:-ai}"
AI_PORT="${AI_PORT:-11434}"

APP_URL="${APP_HEALTH_URL:-http://app:8080/actuator/health}"

CURL_TIMEOUT="${HEALTHCHECK_TIMEOUT:-5}"

PASS=0; FAIL=0

# ---- 工具 ----
print_pass() { echo -e "  ${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
print_fail() { echo -e "  ${RED}FAIL${NC} $1"; FAIL=$((FAIL+1)); }

# ---- 检查器 ----
check_db() {
  if command -v pg_isready >/dev/null 2>&1; then
    if pg_isready -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t "${CURL_TIMEOUT}" >/dev/null 2>&1; then
      print_pass "db ${DB_HOST}:${DB_PORT} ready"
    else
      print_fail "db ${DB_HOST}:${DB_PORT} pg_isready 失败"
    fi
  elif command -v nc >/dev/null 2>&1; then
    if nc -z -w "${CURL_TIMEOUT}" "${DB_HOST}" "${DB_PORT}" >/dev/null 2>&1; then
      print_pass "db ${DB_HOST}:${DB_PORT} tcp ok"
    else
      print_fail "db ${DB_HOST}:${DB_PORT} tcp 失败"
    fi
  else
    print_fail "db 缺少 pg_isready / nc，无法校验"
  fi
}

check_cache() {
  if command -v redis-cli >/dev/null 2>&1; then
    local args=("-h" "${CACHE_HOST}" "-p" "${CACHE_PORT}")
    [[ -n "${CACHE_PASSWORD}" ]] && args+=("-a" "${CACHE_PASSWORD}" "--no-auth-warning")
    if redis-cli "${args[@]}" -t "${CURL_TIMEOUT}" ping 2>/dev/null | grep -q PONG; then
      print_pass "cache ${CACHE_HOST}:${CACHE_PORT} PONG"
    else
      print_fail "cache ${CACHE_HOST}:${CACHE_PORT} ping 失败"
    fi
  elif command -v nc >/dev/null 2>&1; then
    if nc -z -w "${CURL_TIMEOUT}" "${CACHE_HOST}" "${CACHE_PORT}" >/dev/null 2>&1; then
      print_pass "cache ${CACHE_HOST}:${CACHE_PORT} tcp ok"
    else
      print_fail "cache ${CACHE_HOST}:${CACHE_PORT} tcp 失败"
    fi
  else
    print_fail "cache 缺少 redis-cli / nc，无法校验"
  fi
}

check_ai() {
  local url="http://${AI_HOST}:${AI_PORT}/api/tags"
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS --max-time "${CURL_TIMEOUT}" "${url}" >/dev/null 2>&1; then
      print_pass "ai ${AI_HOST}:${AI_PORT} /api/tags ok"
    else
      print_fail "ai ${AI_HOST}:${AI_PORT} /api/tags 失败"
    fi
  else
    print_fail "ai 缺少 curl，无法校验"
  fi
}

check_app() {
  local url="${APP_URL}"
  if [[ "${1:-}" != "" ]]; then
    url="$1"
  fi
  if command -v curl >/dev/null 2>&1; then
    local body
    body="$(curl -fsS --max-time "${CURL_TIMEOUT}" -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null)" || body="000"
    if [[ "${body}" =~ ^(200|204)$ ]]; then
      print_pass "app ${url} HTTP ${body}"
    else
      print_fail "app ${url} HTTP ${body}"
    fi
  else
    print_fail "app 缺少 curl，无法校验"
  fi
}

# ---- 入口 ----
TARGET="${1:-all}"
CUSTOM_URL="${2:-}"

case "${TARGET}" in
  all)
    check_db
    check_cache
    check_ai
    check_app
    ;;
  db)     check_db ;;
  cache)  check_cache ;;
  ai)     check_ai ;;
  app)    check_app "${CUSTOM_URL}" ;;
  *)      echo "Usage: $0 [db|cache|ai|app|all] [app_health_url]"; exit 2 ;;
esac

echo
echo "PASS=${PASS} FAIL=${FAIL}"
[[ ${FAIL} -eq 0 ]] || exit 1
exit 0