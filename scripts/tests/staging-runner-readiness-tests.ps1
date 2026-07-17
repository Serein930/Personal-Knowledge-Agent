$ErrorActionPreference = "Stop"
$systemTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$testRoot = [System.IO.Path]::GetFullPath((Join-Path $systemTempRoot "agentmind-runner-readiness-$([Guid]::NewGuid().ToString('N'))"))
if (-not $testRoot.StartsWith($systemTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "测试目录没有落在系统临时目录内，拒绝继续"
}

$repositoryRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$reportRoot = Join-Path $testRoot "reports"
$previousEnvironment = $env:AGENTMIND_ENVIRONMENT

try {
    # 普通开发机必须在任何 Docker、Vault 或网络命令前被环境边界拒绝。
    $env:AGENTMIND_ENVIRONMENT = "development"
    $thrown = $false
    try {
        & "$repositoryRoot/scripts/test-staging-runner-readiness.ps1" `
            -Role primary `
            -EnvironmentName development `
            -BaseUrl "https://staging.example.com" `
            -ReportRoot $reportRoot
    } catch {
        $thrown = $true
    }
    if (-not $thrown) {
        throw "普通开发环境绕过了受保护 Runner 门禁"
    }

    $reportPath = Join-Path $reportRoot "primary-runner-readiness.json"
    if (-not (Test-Path -LiteralPath $reportPath)) {
        throw "就绪检查失败后没有生成可审计报告"
    }
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.passed -ne $false -or $report.reportType -ne "staging_runner_readiness") {
        throw "失败报告的状态或类型不正确"
    }
    if ($null -ne $report.evidenceType) {
        throw "Runner 就绪报告不得伪装成发布验收证据"
    }

    Write-Host "预发布 Runner 就绪门禁测试通过：开发环境被拒绝，失败报告可审计且不会参与候选冻结。"
} finally {
    $env:AGENTMIND_ENVIRONMENT = $previousEnvironment
    # 删除前再次复核路径边界，避免测试维护时误删工作区内容。
    if ($testRoot.StartsWith($systemTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
