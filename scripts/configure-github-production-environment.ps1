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
if ($configuration.environmentName -ne "production-deployment") {
    throw "该脚本只允许配置名为 production-deployment 的 GitHub Environment"
}
if ($configuration.waitTimerMinutes -lt 0 -or $configuration.waitTimerMinutes -gt 43200) {
    throw "waitTimerMinutes 必须位于 0 至 43200 之间"
}
if ($configuration.preventSelfReview -ne $true) {
    throw "受保护生产部署环境必须启用 preventSelfReview"
}
$reviewerIds = @($configuration.reviewerUserIds)
if ($reviewerIds.Count -eq 0 -or ($reviewerIds | Where-Object { $_ -isnot [long] -and $_ -isnot [int] }).Count -gt 0) {
    throw "至少配置一个有效的 GitHub 审核人用户编号"
}

$requiredVariables = @(
    "PRODUCTION_BASE_URL",
    "PRODUCTION_WORKSPACE_ID",
    "PRODUCTION_STACK_NAME",
    "PRODUCTION_OVERLAY_NETWORK"
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

$baseUri = $null
if (-not [Uri]::TryCreate($variables.PRODUCTION_BASE_URL, [UriKind]::Absolute, [ref]$baseUri) -or
        $baseUri.Scheme -ne "https" -or -not [string]::IsNullOrWhiteSpace($baseUri.Query) -or
        -not [string]::IsNullOrWhiteSpace($baseUri.Fragment)) {
    throw "PRODUCTION_BASE_URL 必须是不带查询参数和片段的 HTTPS 地址"
}
if ($variables.PRODUCTION_WORKSPACE_ID -notmatch '^[1-9][0-9]*$') {
    throw "PRODUCTION_WORKSPACE_ID 必须是正整数"
}
foreach ($name in @("PRODUCTION_STACK_NAME", "PRODUCTION_OVERLAY_NETWORK")) {
    if ($variables[$name] -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{2,63}$') {
        throw "$name 格式无效"
    }
}

$requiredSecretNames = @("PRODUCTION_ACCESS_TOKEN", "PRODUCTION_DEPLOYMENT_GUARD")
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

if ($ValidateOnly) {
    Write-Host "GitHub production-deployment Environment 配置校验通过：变量=$($variables.Count)，秘密=$($secretSources.Count)"
    return
}
if (-not $ConfirmEnvironmentUpdate) {
    throw "写入 GitHub Environment 前必须显式传入 ConfirmEnvironmentUpdate"
}

$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($null -eq $gh) { throw "当前机器未安装 GitHub CLI" }
& $gh.Source auth status
if ($LASTEXITCODE -ne 0) { throw "GitHub CLI 尚未完成认证" }

$temporaryPayload = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-production-environment-$([Guid]::NewGuid().ToString('N')).json"
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
    & $gh.Source api --method PUT "repos/$Repository/environments/production-deployment" --input $temporaryPayload | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "创建或更新 production-deployment Environment 失败" }

    foreach ($entry in $variables.GetEnumerator() | Sort-Object Key) {
        & $gh.Source variable set $entry.Key --env production-deployment --repo $Repository --body $entry.Value
        if ($LASTEXITCODE -ne 0) { throw "写入变量 $($entry.Key) 失败" }
    }
    foreach ($entry in $secretSources.GetEnumerator() | Sort-Object Key) {
        $secretValue = [Environment]::GetEnvironmentVariable($entry.Value, "Process")
        # 秘密通过标准输入传递，避免出现在参数列表、进程信息和日志中。
        $secretValue | & $gh.Source secret set $entry.Key --env production-deployment --repo $Repository
        if ($LASTEXITCODE -ne 0) { throw "写入秘密 $($entry.Key) 失败" }
    }
} finally {
    if (Test-Path -LiteralPath $temporaryPayload) {
        Remove-Item -LiteralPath $temporaryPayload -Force
    }
}

Write-Host "GitHub production-deployment Environment 配置完成，秘密值未写入配置文件或日志。"
