$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$runnerScript = Join-Path $repositoryRoot "deploy/staging/register-github-runner.ps1"
$environmentScript = Join-Path $repositoryRoot "scripts/configure-github-staging-environment.ps1"
$smokeScript = Join-Path $repositoryRoot "scripts/test-staging-dependency-smoke.ps1"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-staging-bootstrap-$([Guid]::NewGuid().ToString('N'))"

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

$secretEnvironmentNames = @(
    "AGENTMIND_TEST_STAGING_TOKEN",
    "AGENTMIND_TEST_BACKUP_ACCESS_KEY",
    "AGENTMIND_TEST_BACKUP_SECRET_KEY"
)
$previousEnvironmentValues = @{}
foreach ($name in $secretEnvironmentNames) {
    $previousEnvironmentValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null

    Assert-Throws -ExpectedMessage "必须显式传入 ConfirmRunnerRegistration" -Action {
        & $runnerScript -Role primary -RunnerDirectory $temporaryRoot -RunnerName "staging-primary"
    }

    foreach ($name in $secretEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($name, "仅用于本地配置校验的测试值", "Process")
    }

    $validConfiguration = [ordered]@{
        environmentName = "staging"
        waitTimerMinutes = 0
        preventSelfReview = $true
        reviewerUserIds = @(12345678)
        variables = [ordered]@{
            STAGING_BASE_URL = "https://staging.agentmind.test"
            STAGING_WORKSPACE_ID = "1"
            STAGING_VAULT_AGENT_CONFIG = "/srv/agentmind/vault/agent.hcl"
            STAGING_VAULT_RENDERED_DIRECTORY = "/srv/agentmind/vault/rendered"
            STAGING_DR_VAULT_AGENT_CONFIG = "/srv/agentmind-dr/vault/agent.hcl"
            STAGING_DR_VAULT_RENDERED_DIRECTORY = "/srv/agentmind-dr/vault/rendered"
            STAGING_RESTIC_REPOSITORY = "s3:https://backup.internal/agentmind"
            STAGING_RESTIC_PASSWORD_FILE = "/srv/agentmind-dr/restic-password"
            STAGING_BACKUP_REGION = "cn-test-1"
            STAGING_DR_ENVIRONMENT = "agentmind-staging-dr"
            STAGING_DR_COMPOSE_FILE = "/srv/agentmind-dr/docker-compose.yml"
            STAGING_RPO_TARGET_MINUTES = "1440"
            STAGING_RTO_TARGET_MINUTES = "60"
        }
        secretEnvironmentVariables = [ordered]@{
            STAGING_ACCESS_TOKEN = "AGENTMIND_TEST_STAGING_TOKEN"
            STAGING_BACKUP_ACCESS_KEY_ID = "AGENTMIND_TEST_BACKUP_ACCESS_KEY"
            STAGING_BACKUP_SECRET_ACCESS_KEY = "AGENTMIND_TEST_BACKUP_SECRET_KEY"
        }
    }
    $validConfigurationPath = Join-Path $temporaryRoot "valid-environment.json"
    $validConfiguration | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $validConfigurationPath -Encoding UTF8
    & $environmentScript -ConfigurationFile $validConfigurationPath -ValidateOnly

    $invalidConfiguration = $validConfiguration.PSObject.Copy()
    $invalidConfiguration.variables = $validConfiguration.variables.PSObject.Copy()
    $invalidConfiguration.variables.STAGING_BASE_URL = "https://example.com"
    $invalidConfigurationPath = Join-Path $temporaryRoot "invalid-environment.json"
    $invalidConfiguration | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidConfigurationPath -Encoding UTF8
    Assert-Throws -ExpectedMessage "仍包含示例占位值" -Action {
        & $environmentScript -ConfigurationFile $invalidConfigurationPath -ValidateOnly
    }

    Assert-Throws -ExpectedMessage "只允许在 staging 环境执行" -Action {
        & $smokeScript `
            -BaseUrl "https://staging.agentmind.test" `
            -WorkspaceId 1 `
            -AccessToken "本地边界测试令牌" `
            -EnvironmentName "development" `
            -ReportRoot (Join-Path $temporaryRoot "reports")
    }
} finally {
    foreach ($name in $secretEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironmentValues[$name], "Process")
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Host "预发布引导脚本边界测试全部通过"
