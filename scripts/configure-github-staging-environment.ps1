param(
    [Parameter(Mandatory = $true)]
    [string]$ConfigurationFile,
    [string]$Repository = "Serein930/Personal-Knowledge-Agent",
    [switch]$ValidateOnly,
    [switch]$ConfirmEnvironmentUpdate
)

$ErrorActionPreference = "Stop"

if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw "Repository 必须使用 owner/repository 格式"
}
if (-not (Test-Path -LiteralPath $ConfigurationFile -PathType Leaf)) {
    throw "GitHub Environment 配置文件不存在：$ConfigurationFile"
}

$configuration = Get-Content -LiteralPath $ConfigurationFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($configuration.environmentName -ne "staging") {
    throw "该脚本只允许配置名为 staging 的 GitHub Environment"
}
if ($configuration.waitTimerMinutes -lt 0 -or $configuration.waitTimerMinutes -gt 43200) {
    throw "waitTimerMinutes 必须位于 0 至 43200 之间"
}
if ($configuration.preventSelfReview -ne $true) {
    throw "受保护预发布环境必须启用 preventSelfReview"
}
$reviewerIds = @($configuration.reviewerUserIds)
if ($reviewerIds.Count -eq 0 -or ($reviewerIds | Where-Object { $_ -isnot [long] -and $_ -isnot [int] }).Count -gt 0) {
    throw "至少配置一个有效的 GitHub 审核人用户编号"
}

$requiredVariables = @(
    "STAGING_BASE_URL",
    "STAGING_WORKSPACE_ID",
    "STAGING_VAULT_AGENT_CONFIG",
    "STAGING_VAULT_RENDERED_DIRECTORY",
    "STAGING_DR_VAULT_AGENT_CONFIG",
    "STAGING_DR_VAULT_RENDERED_DIRECTORY",
    "STAGING_RESTIC_REPOSITORY",
    "STAGING_RESTIC_PASSWORD_FILE",
    "STAGING_BACKUP_REGION",
    "STAGING_DR_ENVIRONMENT",
    "STAGING_DR_COMPOSE_FILE",
    "STAGING_RPO_TARGET_MINUTES",
    "STAGING_RTO_TARGET_MINUTES"
)
$variables = @{}
foreach ($property in $configuration.variables.PSObject.Properties) {
    $variables[$property.Name] = [string]$property.Value
}
foreach ($name in $requiredVariables) {
    $value = $variables[$name]
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "缺少 GitHub Environment 变量：$name"
    }
    if ($value -match '替换|example\.com') {
        throw "变量 $name 仍包含示例占位值"
    }
}
if ($variables.STAGING_BASE_URL -notmatch '^https://') {
    throw "STAGING_BASE_URL 必须使用 HTTPS"
}
if ($variables.STAGING_RESTIC_REPOSITORY -notmatch '^(s3:|azure:|gs:|b2:|rest:|rclone:)') {
    throw "STAGING_RESTIC_REPOSITORY 必须指向异地仓库"
}
if ([int]$variables.STAGING_WORKSPACE_ID -le 0 -or [int]$variables.STAGING_RPO_TARGET_MINUTES -le 0 `
        -or [int]$variables.STAGING_RTO_TARGET_MINUTES -le 0) {
    throw "知识空间编号、RPO 和 RTO 必须是正整数"
}

$requiredSecretNames = @(
    "STAGING_ACCESS_TOKEN",
    "STAGING_BACKUP_ACCESS_KEY_ID",
    "STAGING_BACKUP_SECRET_ACCESS_KEY"
)
$secretSources = @{}
foreach ($property in $configuration.secretEnvironmentVariables.PSObject.Properties) {
    $secretSources[$property.Name] = [string]$property.Value
}
foreach ($name in $requiredSecretNames) {
    $sourceName = $secretSources[$name]
    if ([string]::IsNullOrWhiteSpace($sourceName) -or $sourceName -notmatch '^[A-Z][A-Z0-9_]+$') {
        throw "秘密 $name 缺少合法的外部环境变量映射"
    }
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($sourceName, "Process"))) {
        throw "外部环境变量 $sourceName 未提供秘密 $name"
    }
}

$optionalSecretSources = @{}
if ($null -ne $configuration.optionalSecretEnvironmentVariables) {
    foreach ($property in $configuration.optionalSecretEnvironmentVariables.PSObject.Properties) {
        $optionalSecretSources[$property.Name] = [string]$property.Value
    }
}

if ($ValidateOnly) {
    Write-Host "GitHub staging Environment 配置校验通过：变量=$($variables.Count)，必需秘密=$($secretSources.Count)"
    return
}
if (-not $ConfirmEnvironmentUpdate) {
    throw "写入 GitHub Environment 前必须显式传入 ConfirmEnvironmentUpdate"
}

$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($null -eq $gh) {
    throw "当前机器未安装 GitHub CLI"
}
& $gh.Source auth status
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI 尚未完成认证"
}

$temporaryPayload = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-staging-environment-$([Guid]::NewGuid().ToString('N')).json"
try {
    $payload = [ordered]@{
        wait_timer = [int]$configuration.waitTimerMinutes
        prevent_self_review = $true
        reviewers = @($reviewerIds | ForEach-Object { [ordered]@{ type = "User"; id = [long]$_ } })
        deployment_branch_policy = [ordered]@{
            protected_branches = $true
            custom_branch_policies = $false
        }
    }
    $payload | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $temporaryPayload -Encoding UTF8
    & $gh.Source api --method PUT "repos/$Repository/environments/staging" --input $temporaryPayload | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "创建或更新 staging Environment 失败" }

    foreach ($entry in $variables.GetEnumerator() | Sort-Object Key) {
        & $gh.Source variable set $entry.Key --env staging --repo $Repository --body $entry.Value
        if ($LASTEXITCODE -ne 0) { throw "写入变量 $($entry.Key) 失败" }
    }

    foreach ($entry in $secretSources.GetEnumerator() | Sort-Object Key) {
        $secretValue = [Environment]::GetEnvironmentVariable($entry.Value, "Process")
        # 通过标准输入交给 GitHub CLI，避免秘密出现在命令行参数和进程列表中。
        $secretValue | & $gh.Source secret set $entry.Key --env staging --repo $Repository
        if ($LASTEXITCODE -ne 0) { throw "写入秘密 $($entry.Key) 失败" }
    }
    foreach ($entry in $optionalSecretSources.GetEnumerator() | Sort-Object Key) {
        $secretValue = [Environment]::GetEnvironmentVariable($entry.Value, "Process")
        if (-not [string]::IsNullOrWhiteSpace($secretValue)) {
            $secretValue | & $gh.Source secret set $entry.Key --env staging --repo $Repository
            if ($LASTEXITCODE -ne 0) { throw "写入可选秘密 $($entry.Key) 失败" }
        }
    }
} finally {
    if (Test-Path -LiteralPath $temporaryPayload) {
        Remove-Item -LiteralPath $temporaryPayload -Force
    }
}

Write-Host "GitHub staging Environment 配置完成。秘密值未写入配置文件或日志。"
