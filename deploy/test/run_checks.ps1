# ============================================================
# run_checks.ps1 — Windows PowerShell 入口（转发到 bash 脚本）
#
# 用法（在仓库根目录）：
#   powershell -ExecutionPolicy Bypass -File deploy/test/run_checks.ps1
#   powershell -ExecutionPolicy Bypass -File deploy/test/run_checks.ps1 -Only j1,j11
#
# 需 Git Bash / WSL 已安装且 bash 在 PATH。
# ============================================================
[CmdletBinding()]
param(
  [string]$Only = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$bashScript = Join-Path $PSScriptRoot "deploy_checks.sh"

if (-not (Get-Command bash -ErrorAction SilentlyContinue)) {
  Write-Error "未找到 bash。请安装 Git for Windows（自带 Git Bash）或 WSL 后重试。"
  exit 2
}

$argList = @()
if ($Only) { $argList = @("--only", $Only) }

Push-Location $repoRoot
try {
  & bash $bashScript @argList
  exit $LASTEXITCODE
} finally {
  Pop-Location
}