$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$runnerScript = Join-Path $repositoryRoot "deploy/production/register-github-runner.ps1"
$environmentScript = Join-Path $repositoryRoot "scripts/configure-github-production-environment.ps1"
$readinessScript = Join-Path $repositoryRoot "scripts/test-production-runner-readiness.ps1"
$workflowPath = Join-Path $repositoryRoot ".github/workflows/production-deployment.yml"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-production-bootstrap-$([Guid]::NewGuid().ToString('N'))"
$secretNames = @("AGENTMIND_TEST_PRODUCTION_TOKEN", "AGENTMIND_TEST_PRODUCTION_GUARD")
$previousSecretValues = @{}

function Assert-Throws {
    param([scriptblock]$Action, [string]$ExpectedMessage)
    try {
        & $Action
    } catch {
        if ($_.Exception.Message -notmatch [regex]::Escape($ExpectedMessage)) {
            throw "异常消息不符合预期。预期包含：$ExpectedMessage；实际：$($_.Exception.Message)"
        }
        return
    }
    throw "预期操作失败，但操作成功完成：$ExpectedMessage"
}

try {
    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
    Assert-Throws -ExpectedMessage "必须显式传入 ConfirmRunnerRegistration" -Action {
        & $runnerScript -RunnerDirectory $temporaryRoot -RunnerName "agentmind-production-01"
    }

    foreach ($name in $secretNames) {
        $previousSecretValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
        [Environment]::SetEnvironmentVariable($name, "仅用于本地配置边界测试", "Process")
    }
    $configuration = [ordered]@{
        environmentName = "production-deployment"
        waitTimerMinutes = 5
        preventSelfReview = $true
        reviewerUserIds = @(12345678)
        variables = [ordered]@{
            PRODUCTION_BASE_URL = "https://production.agentmind.test"
            PRODUCTION_WORKSPACE_ID = "1001"
            PRODUCTION_STACK_NAME = "agentmind-production"
            PRODUCTION_OVERLAY_NETWORK = "agentmind-production"
        }
        secretEnvironmentVariables = [ordered]@{
            PRODUCTION_ACCESS_TOKEN = "AGENTMIND_TEST_PRODUCTION_TOKEN"
            PRODUCTION_DEPLOYMENT_GUARD = "AGENTMIND_TEST_PRODUCTION_GUARD"
        }
    }
    $configurationPath = Join-Path $temporaryRoot "production-environment.json"
    $configuration | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configurationPath -Encoding UTF8
    & $environmentScript -ConfigurationFile $configurationPath -ValidateOnly
    Assert-Throws -ExpectedMessage "必须显式传入 ConfirmEnvironmentUpdate" -Action {
        & $environmentScript -ConfigurationFile $configurationPath
    }

    $configuration.variables.PRODUCTION_BASE_URL = "https://example.com"
    $invalidPath = Join-Path $temporaryRoot "invalid-production-environment.json"
    $configuration | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidPath -Encoding UTF8
    Assert-Throws -ExpectedMessage "仍包含示例占位值" -Action {
        & $environmentScript -ConfigurationFile $invalidPath -ValidateOnly
    }

    # 环境边界在工具和 Docker 检查之前执行，因此该用例不会读取或修改本机集群。
    $previousEnvironmentName = $env:AGENTMIND_ENVIRONMENT
    $env:AGENTMIND_ENVIRONMENT = "development"
    Assert-Throws -ExpectedMessage "只允许在 AGENTMIND_ENVIRONMENT=production" -Action {
        & $readinessScript `
            -EnvironmentName "development" `
            -BaseUrl "https://production.agentmind.test" `
            -ReportRoot (Join-Path $temporaryRoot "reports")
    }
    $readinessReport = Get-Content `
        -LiteralPath (Join-Path $temporaryRoot "reports/production-runner-readiness.json") `
        -Raw `
        -Encoding UTF8 | ConvertFrom-Json
    if ($readinessReport.passed -ne $false -or $readinessReport.reportType -ne "production_runner_readiness") {
        throw "生产 Runner 就绪失败没有生成可审计报告"
    }
    $env:AGENTMIND_ENVIRONMENT = $previousEnvironmentName

    $runnerRegistration = Get-Content -LiteralPath $runnerScript -Raw -Encoding UTF8
    foreach ($requiredText in @(
        '"--labels", "agentmind-production"',
        '$env:GITHUB_RUNNER_REGISTRATION_TOKEN = $null',
        "ConfirmRunnerRegistration"
    )) {
        if (-not $runnerRegistration.Contains($requiredText)) {
            throw "生产 Runner 注册脚本缺少安全契约：$requiredText"
        }
    }

    $workflow = Get-Content -LiteralPath $workflowPath -Raw -Encoding UTF8
    foreach ($requiredText in @(
        "runs-on: [self-hosted, agentmind-production]",
        "environment: production-deployment",
        "PRODUCTION_ACCESS_TOKEN",
        "PRODUCTION_OVERLAY_NETWORK",
        "test-production-runner-readiness.ps1"
    )) {
        if (-not $workflow.Contains($requiredText)) {
            throw "生产引导工作流缺少就绪门禁契约：$requiredText"
        }
    }
} finally {
    if ($null -ne $previousEnvironmentName) { $env:AGENTMIND_ENVIRONMENT = $previousEnvironmentName }
    foreach ($name in $secretNames) {
        [Environment]::SetEnvironmentVariable($name, $previousSecretValues[$name], "Process")
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Host "生产 Runner 与 Environment 引导边界测试全部通过"
