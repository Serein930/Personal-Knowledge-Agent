param(
    [Parameter(Mandatory = $true)]
    [string]$ProductionReleaseEvidenceDirectory,
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [long]$WorkspaceId,
    [Parameter(Mandatory = $true)]
    [string]$SmokeQuery,
    [Parameter(Mandatory = $true)]
    [long]$ExpectedDocumentId,
    [string]$StackName = "agentmind-production",
    [ValidateRange(0, 1800)]
    [int]$CanaryObservationSeconds = 60,
    [string]$EnvironmentName = $env:AGENTMIND_ENVIRONMENT,
    [string]$ReportRoot = ".production-deployment-evidence",
    [switch]$ValidateOnly,
    [switch]$ConfirmProductionDeployment
)

$ErrorActionPreference = "Stop"
$startedAt = [DateTimeOffset]::UtcNow

if ($EnvironmentName -ne "production") { throw "独立生产部署只允许在 production 环境执行" }
if ($BaseUrl -notmatch '^https://') { throw "生产部署地址必须使用 HTTPS" }
if ($StackName -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$') { throw "生产堆栈名称格式无效" }
if ($WorkspaceId -le 0 -or $ExpectedDocumentId -le 0) { throw "知识空间和冒烟文档编号必须是正整数" }
if ([string]::IsNullOrWhiteSpace($SmokeQuery)) { throw "生产冒烟检索词不能为空" }
if (-not (Test-Path -LiteralPath $ProductionReleaseEvidenceDirectory -PathType Container)) {
    throw "正式版本发布证据目录不存在"
}

function Find-SingleEvidence {
    param([scriptblock]$Predicate, [string]$Description)
    $matches = @()
    foreach ($file in Get-ChildItem -LiteralPath $ProductionReleaseEvidenceDirectory -Filter "*.json" -File -Recurse) {
        $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        if (& $Predicate $content) { $matches += [pscustomobject]@{ File = $file; Content = $content } }
    }
    if ($matches.Count -ne 1) { throw "$Description 必须且只能存在一份，实际数量=$($matches.Count)" }
    return $matches[0]
}

$releaseManifest = Find-SingleEvidence `
    -Description "正式发布清单" `
    -Predicate { param($json) $json.manifestType -eq "production_release" }
$releaseRecord = Find-SingleEvidence `
    -Description "GitHub 正式发布记录" `
    -Predicate { param($json) $json.evidenceType -eq "production_release" }

if (-not $releaseManifest.Content.passed -or -not $releaseRecord.Content.passed) {
    throw "正式版本发布清单或 GitHub Release 记录未通过"
}
if ($releaseManifest.Content.schemaVersion -ne "1.0" -or $releaseRecord.Content.schemaVersion -ne "1.0") {
    throw "正式发布证据版本不受当前部署脚本支持"
}
if ($releaseManifest.Content.gitCommit -ne $releaseRecord.Content.gitCommit -or
    $releaseManifest.Content.version -ne $releaseRecord.Content.version -or
    $releaseManifest.Content.candidateImage -ne $releaseRecord.Content.candidateImage) {
    throw "正式发布清单与 GitHub Release 记录绑定的提交、版本或镜像不一致"
}
if ($releaseManifest.Content.candidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "生产部署必须使用不可变 sha256 镜像摘要"
}
$releaseManifestHash = (Get-FileHash -LiteralPath $releaseManifest.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
if ($releaseRecord.Content.releaseManifestSha256 -ne $releaseManifestHash) {
    throw "GitHub Release 记录没有绑定当前正式发布清单"
}

if ($ValidateOnly) {
    Write-Host "生产部署证据校验通过：版本=$($releaseManifest.Content.version)，镜像=$($releaseManifest.Content.candidateImage)"
    return
}
if (-not $ConfirmProductionDeployment) {
    throw "生产部署会修改真实服务，必须显式传入 ConfirmProductionDeployment"
}

$currentCommit = git rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $currentCommit -ne $releaseManifest.Content.gitCommit) {
    throw "当前检出提交与正式发布清单不一致"
}
$swarm = docker info --format '{{json .Swarm}}' | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $swarm.LocalNodeState -ne "active" -or -not $swarm.ControlAvailable) {
    throw "当前 Runner 不是可用的生产 Swarm 管理节点"
}

$stableService = "$StackName`_agentmind-backend"
$canaryService = "$StackName`_agentmind-backend-canary"
$previousStableImage = docker service inspect $stableService --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($previousStableImage)) { throw "无法读取生产稳定组镜像" }
$previousCanaryImage = docker service inspect $canaryService --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($previousCanaryImage)) { throw "无法读取生产灰度组镜像" }

$candidateImage = [string]$releaseManifest.Content.candidateImage
$failure = $null
$rolledBack = $false
try {
    # 先替换约 10% 流量的灰度服务；内部就绪检查通过前稳定组保持原镜像。
    & "$PSScriptRoot/canary-release.ps1" `
        -Image $candidateImage `
        -StackName $StackName `
        -EnvironmentName "production" `
        -GitCommit $releaseManifest.Content.gitCommit `
        -ReportRoot $ReportRoot
    if ($CanaryObservationSeconds -gt 0) { Start-Sleep -Seconds $CanaryObservationSeconds }

    & "$PSScriptRoot/../scripts/test-production-post-release-smoke.ps1" `
        -BaseUrl $BaseUrl `
        -WorkspaceId $WorkspaceId `
        -Query $SmokeQuery `
        -ExpectedDocumentId $ExpectedDocumentId `
        -EnvironmentName "production" `
        -Phase "canary" `
        -ReportRoot $ReportRoot

    # 稳定组使用 start-first 和 Swarm 健康监控逐实例替换；失败时服务策略先执行自身回滚。
    docker service update --with-registry-auth --detach=false --image $candidateImage $stableService
    if ($LASTEXITCODE -ne 0) { throw "生产稳定组更新失败" }
    docker service update --with-registry-auth --detach=false --image $candidateImage $canaryService
    if ($LASTEXITCODE -ne 0) { throw "生产灰度组收敛到正式镜像失败" }

    & "$PSScriptRoot/../scripts/test-production-post-release-smoke.ps1" `
        -BaseUrl $BaseUrl `
        -WorkspaceId $WorkspaceId `
        -Query $SmokeQuery `
        -ExpectedDocumentId $ExpectedDocumentId `
        -EnvironmentName "production" `
        -Phase "stable" `
        -ReportRoot $ReportRoot
} catch {
    $failure = $_.Exception.Message
    # 无论故障发生在灰度、稳定组切换还是发布后冒烟，都恢复部署开始前的两个镜像。
    docker service update --with-registry-auth --detach=false --image $previousStableImage $stableService | Out-Null
    $stableRollbackExitCode = $LASTEXITCODE
    docker service update --with-registry-auth --detach=false --image $previousCanaryImage $canaryService | Out-Null
    $canaryRollbackExitCode = $LASTEXITCODE
    $rolledBack = $stableRollbackExitCode -eq 0 -and $canaryRollbackExitCode -eq 0
}

$report = [ordered]@{
    schemaVersion = "1.0"
    evidenceType = "production_deployment"
    environment = "production"
    version = $releaseManifest.Content.version
    gitCommit = $releaseManifest.Content.gitCommit
    candidateImage = $candidateImage
    previousStableImage = $previousStableImage
    previousCanaryImage = $previousCanaryImage
    startedAt = $startedAt.ToString("o")
    completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    rolledBack = $rolledBack
    passed = $null -eq $failure
    failure = $failure
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "production-deployment.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
if ($null -ne $failure) {
    throw "生产部署失败：$failure；自动回滚=$rolledBack。报告=$reportPath"
}
Write-Host "生产摘要镜像部署及发布后冒烟通过：$reportPath"
