# Lifewise Cross-Document Planning Consistency Tests (21 assertions)

param([string]$Root = "$PSScriptRoot/..")

$ErrorActionPreference = 'Stop'
$failures = 0
$passes   = 0

function Test-Assert {
    param(
        [string]$Id,
        [string]$Desc,
        [string]$Pattern,
        [string[]]$Files,
        [int]$ExpectedCount = 1,
        [string]$Mode = 'Contains'
    )
    $hits = 0
    $detail = @()
    if ($Id -match '^A(3[8-9]|4[0-4])$') {
        Write-Host "DEBUG: Id=$Id PatternLen=$($Pattern.Length) Pattern=[$Pattern]"
    }
    foreach ($f in $Files) {
        $full = Join-Path $Root $f
        if (-not (Test-Path $full)) { $detail += "MISSING: $f"; continue }
        $lines = Get-Content $full -Encoding UTF8
        $matched = switch ($Mode) {
            'Contains' { $lines | Select-String -Pattern $Pattern -SimpleMatch }
            'Matches'  { $lines | Select-String -Pattern $Pattern }
        }
        $hits += $matched.Count
        foreach ($m in $matched) {
            $detail += "$f`:$($m.LineNumber) $($m.Line.Trim())"
        }
    }
    $ok = if ($ExpectedCount -eq 0) { $hits -eq 0 } else { $hits -ge $ExpectedCount }
    if ($ok) {
        $script:passes++
        Write-Host ("[PASS] {0}  {1}  (hits={2})" -f $Id, $Desc, $hits)
    } else {
        $script:failures++
        $expectWord = if ($ExpectedCount -eq 0) { 'eq' } else { '>=' }
        Write-Host ("[FAIL] {0}  {1}  (hits={2}, expected {3} {4})" -f $Id, $Desc, $hits, $expectWord, $ExpectedCount)
        $detail | ForEach-Object { Write-Host ("       $_") }
    }
}

Write-Host "=== Lifewise Cross-Document Planning Consistency Tests ==="
Write-Host ("Root: $Root")
Write-Host ""

Test-Assert -Id 'A01' -Desc 'V21/V34 module CHECK has 6 items' `
    -Pattern "module IN" `
    -Files @('docs/lifewise/architecture/data-model-v1.2-amendment.md', 'docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 2

Test-Assert -Id 'A02' -Desc 'V21/V34 format CHECK has json' `
    -Pattern "'json'" `
    -Files @('docs/lifewise/architecture/data-model-v1.2-amendment.md', 'docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 2

Test-Assert -Id 'A03' -Desc 'V33 outbox CHECK covers 4 auth.* events' `
    -Pattern "auth\." `
    -Files @('docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 4 -Mode Matches

Test-Assert -Id 'A04' -Desc 'ai_jobs.status 7 states include DONE_NO_LLM/DONE_PARTIAL' `
    -Pattern "DONE_NO_LLM" `
    -Files @('docs/lifewise/planning/plan-06-ai.md', 'docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 2

Test-Assert -Id 'A05' -Desc 'V32 daily_reports has is_draft column' `
    -Pattern "is_draft BOOLEAN" `
    -Files @('docs/lifewise/planning/plan-data-flyway.md', 'docs/lifewise/planning/plan-02-daily.md') `
    -ExpectedCount 2

Test-Assert -Id 'A06' -Desc 'V32 CHECK uses is_draft not status (X1)' `
    -Pattern "is_draft=TRUE" `
    -Files @('docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 1

Test-Assert -Id 'A07' -Desc 'mv_meal_nutrition_weekly UNIQUE INDEX declared (X5)' `
    -Pattern "uq_mv_meal_user_week" `
    -Files @('docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 1

Test-Assert -Id 'A08' -Desc 'nginx /api/auth/login location present (E1)' `
    -Pattern "/api/auth/login" `
    -Files @('docs/lifewise/planning/plan-deploy-nginx.md') `
    -ExpectedCount 1

Test-Assert -Id 'A09' -Desc 'nginx login zone rate=1r/m (E1)' `
    -Pattern "rate=1r/m" `
    -Files @('docs/lifewise/planning/plan-deploy-nginx.md') `
    -ExpectedCount 1

Test-Assert -Id 'A10' -Desc 'nginx /api/ai/chat SSE buffering off in location (X6)' `
    -Pattern "proxy_buffering off" `
    -Files @('docs/lifewise/planning/plan-deploy-nginx.md') `
    -ExpectedCount 2

Test-Assert -Id 'A11' -Desc 'CSP font-src includes Google Fonts (X6)' `
    -Pattern "fonts.gstatic.com" `
    -Files @('docs/lifewise/planning/plan-deploy-nginx.md') `
    -ExpectedCount 1

Test-Assert -Id 'A12' -Desc 'outbox retry 3 times consistent (F1)' `
    -Pattern "3 .*30s" `
    -Files @('docs/lifewise/planning/plan-shared-integration.md') `
    -ExpectedCount 1 -Mode Matches
Test-Assert -Id 'A12b' -Desc 'observability retry exhausted threshold 3 (F1)' `
    -Pattern "> 3" `
    -Files @('docs/lifewise/planning/plan-observability-backup.md') `
    -ExpectedCount 1

Test-Assert -Id 'A13' -Desc '@RateLimit scope 5 items (G1)' `
    -Pattern "export .* webpush" `
    -Files @('docs/lifewise/planning/plan-shared-infra.md') `
    -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A14' -Desc 'budget.threshold consistent in 4 files (X7+B2)' `
    -Pattern "budget.threshold" `
    -Files @('docs/lifewise/planning/plan-notify.md', 'docs/lifewise/planning/plan-shared-integration.md', 'docs/lifewise/planning/plan-data-flyway.md', 'docs/lifewise/planning/plan-03-expense.md') `
    -ExpectedCount 4

Test-Assert -Id 'A15' -Desc 'ai.job.completed 3-state trigger (X3)' `
    -Pattern "DONE_PARTIAL" `
    -Files @('docs/lifewise/planning/plan-06-ai.md', 'docs/lifewise/planning/plan-shared-integration.md', 'docs/lifewise/planning/plan-data-flyway.md') `
    -ExpectedCount 3

Test-Assert -Id 'A16' -Desc 'ai-data-scopes report_types use _summary suffix (X4)' `
    -Pattern "_summary" `
    -Files @('docs/lifewise/planning/plan-06-ai.md') `
    -ExpectedCount 3

Test-Assert -Id 'A17' -Desc 'milestone link duplicate test renamed to reject (D1)' `
    -Pattern "reject_duplicate" `
    -Files @('docs/lifewise/planning/plan-05-plan.md') `
    -ExpectedCount 1

Test-Assert -Id 'A18' -Desc 'meal.created consumer does not include export mislabel (C1)' `
    -Pattern "meal\.created" `
    -Files @('docs/lifewise/planning/plan-04-diet.md') `
    -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A19' -Desc 'plan-01-task has task_should_emit_created_event (A1)' `
    -Pattern "task_should_emit_created_event" `
    -Files @('docs/lifewise/planning/plan-01-task.md') `
    -ExpectedCount 1

Test-Assert -Id 'A20' -Desc 'plan-02-daily is_draft comment is X1 (I1)' `
    -Pattern "is_draft BOOLEAN NOT NULL DEFAULT TRUE" `
    -Files @('docs/lifewise/planning/plan-02-daily.md') `
    -ExpectedCount 1

Test-Assert -Id 'A21' -Desc 'plan-export BR-31 H1 fix comment present' `
    -Pattern "H1" `
    -Files @('docs/lifewise/planning/plan-export.md') `
    -ExpectedCount 1

# ===== 第三轮新增：N1~N8 修复断言（A22~A31） =====

Test-Assert -Id 'A22' -Desc 'N1 nginx proxy_pass /api/auth/login has /api prefix' `
    -Pattern "proxy_pass http://app/api/auth/login;" `
    -Files @('docs/lifewise/planning/plan-deploy-nginx.md') `
    -ExpectedCount 1

Test-Assert -Id 'A23' -Desc 'N1 nginx proxy_pass /api/ai/chat has /api prefix' `
    -Pattern "proxy_pass http://app/api/ai/chat;" `
    -Files @('docs/lifewise/planning/plan-deploy-nginx.md') `
    -ExpectedCount 1

Test-Assert -Id 'A24' -Desc 'N2 plan-notify §3.1 type 含 budget.threshold.80' `
    -Pattern "budget\.threshold\.80" `
    -Files @('docs/lifewise/planning/plan-notify.md') `
    -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A25' -Desc 'N2 plan-notify §3.1 type 含 milestone.due_soon' `
    -Pattern "milestone\.due_soon" `
    -Files @('docs/lifewise/planning/plan-notify.md') `
    -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A26' -Desc 'N3 plan-notify §3.1 type 不含 meal.reminder（已删）' `
    -Pattern "meal\.reminder" `
    -Files @('docs/lifewise/planning/plan-notify.md') `
    -ExpectedCount 0 -Mode Matches

Test-Assert -Id 'A27' -Desc 'N4 plan-export milestones 字段对齐 ProgressView' -Pattern 'milestones\[\]\.progress\{completed_tasks' -Files @('docs/lifewise/planning/plan-export.md') -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A28' -Desc 'N5 plan-export markdown format test' -Pattern 'export_should_accept_markdown_format' -Files @('docs/lifewise/planning/plan-export.md') -ExpectedCount 1

Test-Assert -Id 'A29' -Desc 'N6 plan-auth ratelimit per IP' -Pattern 'per IP.*userId dim not in v1.0 scope' -Files @('docs/lifewise/planning/plan-auth.md') -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A30' -Desc 'N7 observability BudgetEvaluatorJob removed' -Pattern 'BudgetEvaluatorJob' -Files @('docs/lifewise/planning/plan-observability-backup.md') -ExpectedCount 0 -Mode Matches

Test-Assert -Id 'A31' -Desc 'N8 plan-shared-infra login scope comment updated' `
    -Pattern '二选一裁决待定' `
    -Files @('docs/lifewise/planning/plan-shared-infra.md') `
    -ExpectedCount 0 -Mode Matches

# ===== Round 4: N9~N14 (A32~A37) =====

Test-Assert -Id 'A32a' -Desc 'N9 data-flyway V28 not chat backfill' -Pattern 'V28.*chat_messages.*conversations' -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Matches

Test-Assert -Id 'A32b' -Desc 'N9 auth V28 owns refresh_tokens 3 tables' -Pattern 'V28' -Files @('docs/lifewise/planning/plan-auth.md') -ExpectedCount 2 -Mode Matches

$bug_n10 = 'V24 ' + ([char[]](0x4e8b,0x4ef6,0x8865,0x5f55) -join '')
Test-Assert -Id 'A33' -Desc 'N10 shared-integration ref must say V33 not V24' -Pattern $bug_n10 -Files @('docs/lifewise/planning/plan-shared-integration.md') -ExpectedCount 0 -Mode Contains

Test-Assert -Id 'A34' -Desc 'N11 data-flyway V range must include V34' -Pattern 'V1~V32' -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Contains

$bug_n12 = 'v1.0 ' + ([char[]](0x6295,0x4ea7) -join '') + ' 4 ' + ([char[]](0x6a21,0x5757) -join '')
Test-Assert -Id 'A35' -Desc 'N12 V34 COMMENT must say 6 modules not 4' -Pattern $bug_n12 -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Contains

Test-Assert -Id 'A36' -Desc 'N13 shared-infra V range comment must include V34' -Pattern 'V1~V25' -Files @('docs/lifewise/planning/plan-shared-infra.md') -ExpectedCount 0 -Mode Contains

Test-Assert -Id 'A37' -Desc 'N14 BR-34 must reference UNIQUE not artifact_count' -Pattern 'artifact_count' -Files @('docs/lifewise/planning/plan-export.md') -ExpectedCount 0 -Mode Contains

# ===== Round 5: N15~N21 (A38~A44) =====

# A38 (N17): V33 SQL 注释「不在此 23 项内」错（应改为 25 项）
$bug_n17 = ([char[]](0x4e0d,0x5728,0x6b64) -join '') + ' 23 ' + ([char[]](0x9879,0x5185) -join '')
Test-Assert -Id 'A38' -Desc 'N17 V33 SQL note 23 should be 25' -Pattern $bug_n17 -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Contains

$bug_n18 = '= 19 ' + [string]([char]0x6761)
Write-Host "DEBUG A39 pre-call: bug_n18=[$bug_n18] len=$($bug_n18.Length)"
Test-Assert -Id 'A39' -Desc 'N18 sec4 title 19 should be 23' -Pattern $bug_n18 -Files @('docs/lifewise/planning/plan-shared-integration.md') -ExpectedCount 0 -Mode Contains
# A40 (N22): plan-data-flyway §2 表清单「31 张」应改为 38 张
$bug_n22 = '31 ' + [char]0x5f20
Test-Assert -Id 'A40' -Desc 'N22 table count 31 should be 38' -Pattern $bug_n22 -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Contains

# A41 (N15): V35 行位置错位（V30 后插了 V35，应在 V34 之后）
Test-Assert -Id 'A41' -Desc 'N15 V35 row position wrong (after V30 not V34)' -Pattern 'V30.*V35.*V31' -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Matches

# A42 (N16): plan-06-ai 关联文档「V28 chat_messages 回填」应改为 V35
Test-Assert -Id 'A42' -Desc 'N16 plan-06-ai ref V28 chat should be V35' -Pattern 'V28 chat_messages' -Files @('docs/lifewise/planning/plan-06-ai.md') -ExpectedCount 0 -Mode Matches

# A43 (N20): observability-backup 验收「8 个 @Scheduled Job」应改为 13
$bug_n20 = '8 ' + [char]0x4e2a + ' @Scheduled Job'
Test-Assert -Id 'A43' -Desc 'N20 observability 8 jobs should be 13' -Pattern $bug_n20 -Files @('docs/lifewise/planning/plan-observability-backup.md') -ExpectedCount 0 -Mode Contains

# A44 (N21): plan-06-ai 验收「payload 含 report_id 引用」应改为 job_id
$bug_n21 = 'payload ' + [char]0x542b + ' `' + 'report_id'
Test-Assert -Id 'A44' -Desc 'N21 plan-06-ai payload report_id should be job_id' -Pattern $bug_n21 -Files @('docs/lifewise/planning/plan-06-ai.md') -ExpectedCount 0 -Mode Contains

# ===== Round 6: P0 BUG B22+B4+B5+B11+B14+B13+B17+B21 (A45~A54) =====

# A45 (B22a): plan-shared-integration §4 事件触发源应写 MissedMilestoneJob 不是 MilestoneMissedJob
Test-Assert -Id 'A45' -Desc 'B22a shared-integration MilestoneMissedJob typo' -Pattern 'MilestoneMissedJob' -Files @('docs/lifewise/planning/plan-shared-integration.md') -ExpectedCount 0 -Mode Contains

# A46 (B22b): plan-data-flyway §7 同 typo
Test-Assert -Id 'A46' -Desc 'B22b data-flyway MilestoneMissedJob typo' -Pattern 'MilestoneMissedJob' -Files @('docs/lifewise/planning/plan-data-flyway.md') -ExpectedCount 0 -Mode Contains

# A47 (B4): plan-04-diet MV 列名应为 period_year/period_week 不是 week_start
Test-Assert -Id 'A47' -Desc 'B4 diet MV column week_start should be period_year/week' -Pattern 'week_start' -Files @('docs/lifewise/planning/plan-04-diet.md') -ExpectedCount 0 -Mode Contains

# A48 (B5): plan-04-diet meal.created 触发应仅为 INSERT 不是 INSERT/UPDATE
Test-Assert -Id 'A48' -Desc 'B5 diet meal.created trigger should be INSERT not INSERT/UPDATE' -Pattern 'meals INSERT/UPDATE' -Files @('docs/lifewise/planning/plan-04-diet.md') -ExpectedCount 0 -Mode Contains

# A49 (B11): plan-06-ai §2.4 状态应为 DONE_NO_LLM 不是 llm_skipped
Test-Assert -Id 'A49' -Desc 'B11 ai llm_skipped should be DONE_NO_LLM' -Pattern 'llm_skipped' -Files @('docs/lifewise/planning/plan-06-ai.md') -ExpectedCount 0 -Mode Contains

# A50 (B14): plan-06-ai TDD 测试名应为 job_completed 不是 report_generated
Test-Assert -Id 'A50' -Desc 'B14 ai emit_report_generated_event should be job_completed' -Pattern 'ai_should_emit_report_generated_event' -Files @('docs/lifewise/planning/plan-06-ai.md') -ExpectedCount 0 -Mode Contains

# A51 (B13): plan-06-ai §1 控制器应为 AiConsentController 不是 AiReportController
Test-Assert -Id 'A51' -Desc 'B13 ai AiReportController should be AiConsentController' -Pattern 'AiReportController' -Files @('docs/lifewise/planning/plan-06-ai.md') -ExpectedCount 0 -Mode Contains

# A52 (B17): plan-auth §1 应列 TokenReuseDetectedEvent（4 个 event 类）
Test-Assert -Id 'A52' -Desc 'B17 auth should have TokenReuseDetectedEvent' -Pattern 'TokenReuseDetectedEvent' -Files @('docs/lifewise/planning/plan-auth.md') -ExpectedCount 1

# A53 (B21a): plan-auth §3.1 users 应有 password_hash 列
Test-Assert -Id 'A53' -Desc 'B21a auth users should have password_hash column' -Pattern 'password_hash' -Files @('docs/lifewise/planning/plan-auth.md') -ExpectedCount 1

# A54 (B21b): plan-auth §3.1 users 应有 email_verified 列
Test-Assert -Id 'A54' -Desc 'B21b auth users should have email_verified column' -Pattern 'email_verified' -Files @('docs/lifewise/planning/plan-auth.md') -ExpectedCount 1

Write-Host ""
Write-Host "=== Result Summary ==="
Write-Host ("PASS: $passes")
Write-Host ("FAIL: $failures")

if ($failures -gt 0) { exit 1 } else { exit 0 }
