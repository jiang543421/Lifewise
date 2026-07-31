function Test-Assert {
    param(
        [string]$Id,
        [string]$Desc,
        [string]$Pattern,
        [string[]]$Files,
        [int]$ExpectedCount = 1,
        [string]$Mode = 'Contains'
    )
    Write-Host ("DEBUG: Id={0} Desc={1} Pattern={2} Files={3} ExpectedCount={4} Mode={5}" -f $Id, $Desc, $Pattern, ($Files -join ','), $ExpectedCount, $Mode)
    $hits = 0
    $detail = @()
    foreach ($f in $Files) {
        $full = $f
        if (-not (Test-Path $full)) { $detail += "MISSING: $f"; continue }
        $lines = Get-Content $full
        $matched = switch ($Mode) {
            'Contains' { $lines | Select-String -Pattern $Pattern -SimpleMatch }
            'Matches'  { $lines | Select-String -Pattern $Pattern }
        }
        Write-Host ("DEBUG: file={0} matched.Count={1}" -f $f, $matched.Count)
        $hits += $matched.Count
        foreach ($m in $matched) {
            $detail += "$f`:$($m.LineNumber) $($m.Line.Trim())"
        }
    }
    $ok = if ($ExpectedCount -eq 0) { $hits -eq 0 } else { $hits -ge $ExpectedCount }
    Write-Host ("DEBUG: hits={0} ExpectedCount={1} ok={2}" -f $hits, $ExpectedCount, $ok)
}

Test-Assert -Id 'A27' -Desc 'N4 plan-export milestones' `
    -Pattern "ProgressService 实时聚合注入" `
    -Files @('docs/lifewise/planning/plan-export.md') `
    -ExpectedCount 1

Test-Assert -Id 'A28' -Desc 'N5 plan-export markdown test' `
    -Pattern "export_should_accept_markdown_format" `
    -Files @('docs/lifewise/planning/plan-export.md') `
    -ExpectedCount 1

Test-Assert -Id 'A29' -Desc 'N6 plan-auth ratelimit' `
    -Pattern "per IP.*userId" `
    -Files @('docs/lifewise/planning/plan-auth.md') `
    -ExpectedCount 1 -Mode Matches

Test-Assert -Id 'A30' -Desc 'N7 observability BudgetEvaluatorJob' `
    -Pattern "BudgetEvaluatorJob" `
    -Files @('docs/lifewise/planning/plan-observability-backup.md') `
    -ExpectedCount 0 -Mode Matches

Test-Assert -Id 'A31' -Desc 'N8 plan-shared-infra login scope' `
    -Pattern "二选一裁决待定" `
    -Files @('docs/lifewise/planning/plan-shared-infra.md') `
    -ExpectedCount 0 -Mode Matches

# Test regex per IP.*userId with default Get-Content
$path = 'docs/lifewise/planning/plan-auth.md'
Write-Host '--- default Get-Content ---'
$default = Get-Content $path
Write-Host ('lines count: ' + $default.Count)
$matched = $default | Select-String -Pattern 'per IP.*userId'
Write-Host ('regex hits: ' + $matched.Count)
foreach ($m in $matched) {
    Write-Host ('line ' + $m.LineNumber + ': ' + $m.Line)
}