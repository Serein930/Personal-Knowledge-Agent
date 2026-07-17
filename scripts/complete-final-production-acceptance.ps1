param(
    [Parameter(Mandatory = $true)]
    [string]$ProductionAcceptanceDirectory,
    [Parameter(Mandatory = $true)]
    [string]$ProductionReleaseDirectory,
    [Parameter(Mandatory = $true)]
    [string]$ProductionDeploymentDirectory,
    [Parameter(Mandatory = $true)]
    [string]$GitCommit,
    [Parameter(Mandatory = $true)]
    [long]$AcceptanceWorkflowRunId,
    [Parameter(Mandatory = $true)]
    [long]$ProductionReleaseWorkflowRunId,
    [Parameter(Mandatory = $true)]
    [long]$ProductionDeploymentWorkflowRunId,
    [string]$ReportRoot = ".final-production-acceptance"
)

$ErrorActionPreference = "Stop"

if ($GitCommit -notmatch '^[a-f0-9]{40}$') {
    throw "GitCommit 必须是完整的 40 位小写提交摘要"
}
foreach ($entry in ([ordered]@{
    AcceptanceWorkflowRunId = $AcceptanceWorkflowRunId
    ProductionReleaseWorkflowRunId = $ProductionReleaseWorkflowRunId
    ProductionDeploymentWorkflowRunId = $ProductionDeploymentWorkflowRunId
}).GetEnumerator()) {
    if ([long]$entry.Value -le 0) { throw "$($entry.Key) 必须是正整数" }
}
foreach ($entry in ([ordered]@{
    ProductionAcceptanceDirectory = $ProductionAcceptanceDirectory
    ProductionReleaseDirectory = $ProductionReleaseDirectory
    ProductionDeploymentDirectory = $ProductionDeploymentDirectory
}).GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Value -PathType Container)) {
        throw "$($entry.Key) 指向的证据目录不存在"
    }
}

function Read-SingleJson {
    param(
        [string]$Directory,
        [scriptblock]$Predicate,
        [string]$Description
    )
    $matches = @()
    foreach ($file in Get-ChildItem -LiteralPath $Directory -Filter "*.json" -File -Recurse) {
        try {
            $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        } catch {
            throw "发现无法解析的 JSON 证据：$($file.FullName)"
        }
        if (& $Predicate $content) {
            $matches += [pscustomobject]@{ File = $file; Content = $content }
        }
    }
    if ($matches.Count -ne 1) {
        throw "$Description 必须且只能存在一份，实际数量=$($matches.Count)"
    }
    return $matches[0]
}

function Get-FileSha256([System.IO.FileInfo]$File) {
    return (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-PositiveMetric([string]$Name, [object]$Value) {
    $number = 0.0
    if (-not [double]::TryParse(
            [string]$Value,
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$number
        ) -or $number -le 0) {
        throw "真实容量报告缺少有效指标：$Name"
    }
    return $number
}

$releaseCandidate = Read-SingleJson `
    -Directory $ProductionAcceptanceDirectory `
    -Description "冻结发布候选清单" `
    -Predicate { param($json) -not [string]::IsNullOrWhiteSpace([string]$json.releaseCandidateId) }

if ($releaseCandidate.Content.schemaVersion -ne "1.0" -or -not $releaseCandidate.Content.passed -or
        $releaseCandidate.Content.environment -ne "staging") {
    throw "冻结发布候选清单未通过、环境错误或结构版本不受支持"
}
if ($releaseCandidate.Content.gitCommit -ne $GitCommit) {
    throw "冻结发布候选清单与最终验收提交不一致"
}
$candidateImage = [string]$releaseCandidate.Content.candidateImage
if ($candidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "冻结发布候选清单没有绑定不可变 sha256 镜像摘要"
}

$requiredAcceptanceTypes = @(
    "preflight",
    "secret_rotation",
    "canary_release",
    "capacity",
    "fault_injection",
    "disaster_recovery"
)
$acceptanceEvidence = @{}
$acceptanceHashes = [ordered]@{}
foreach ($type in $requiredAcceptanceTypes) {
    $source = Read-SingleJson `
        -Directory $ProductionAcceptanceDirectory `
        -Description "发布候选原始证据 $type" `
        -Predicate { param($json) $json.evidenceType -eq $type }
    if ($source.Content.schemaVersion -ne "1.0" -or -not $source.Content.passed -or
            $source.Content.environment -ne "staging" -or $source.Content.gitCommit -ne $GitCommit -or
            $source.Content.candidateImage -ne $candidateImage) {
        throw "发布候选原始证据 $type 未通过或与候选身份不一致"
    }
    $manifestEntry = $releaseCandidate.Content.evidence.PSObject.Properties |
        Where-Object { $_.Name -eq $type } |
        Select-Object -ExpandProperty Value -First 1
    $actualHash = Get-FileSha256 -File $source.File
    if ($null -eq $manifestEntry -or $manifestEntry.sha256 -ne $actualHash) {
        throw "发布候选原始证据 $type 与冻结清单哈希不一致"
    }
    $acceptanceEvidence[$type] = $source
    $acceptanceHashes[$type] = $actualHash
}

$productionReleaseManifest = Read-SingleJson `
    -Directory $ProductionReleaseDirectory `
    -Description "正式版本发布清单" `
    -Predicate { param($json) $json.manifestType -eq "production_release" }
$productionReleaseRecord = Read-SingleJson `
    -Directory $ProductionReleaseDirectory `
    -Description "GitHub 正式版本发布记录" `
    -Predicate { param($json) $json.evidenceType -eq "production_release" }

if (-not $productionReleaseManifest.Content.passed -or -not $productionReleaseRecord.Content.passed -or
        $productionReleaseManifest.Content.schemaVersion -ne "1.0" -or
        $productionReleaseRecord.Content.schemaVersion -ne "1.0") {
    throw "正式版本发布证据未通过或结构版本不受支持"
}
if ($productionReleaseManifest.Content.gitCommit -ne $GitCommit -or
        $productionReleaseRecord.Content.gitCommit -ne $GitCommit -or
        $productionReleaseManifest.Content.candidateImage -ne $candidateImage -or
        $productionReleaseRecord.Content.candidateImage -ne $candidateImage -or
        $productionReleaseManifest.Content.releaseCandidateId -ne $releaseCandidate.Content.releaseCandidateId -or
        $productionReleaseManifest.Content.version -ne $productionReleaseRecord.Content.version) {
    throw "正式版本、冻结候选和最终验收身份不一致"
}
$releaseManifestHash = Get-FileSha256 -File $productionReleaseManifest.File
if ($productionReleaseRecord.Content.releaseManifestSha256 -ne $releaseManifestHash) {
    throw "GitHub 正式版本发布记录没有绑定当前发布清单"
}

$runnerReadiness = Read-SingleJson `
    -Directory $ProductionDeploymentDirectory `
    -Description "生产 Runner 就绪报告" `
    -Predicate { param($json) $json.reportType -eq "production_runner_readiness" }
$deployment = Read-SingleJson `
    -Directory $ProductionDeploymentDirectory `
    -Description "生产部署报告" `
    -Predicate { param($json) $json.evidenceType -eq "production_deployment" }
$canarySmoke = Read-SingleJson `
    -Directory $ProductionDeploymentDirectory `
    -Description "生产灰度冒烟报告" `
    -Predicate { param($json) $json.evidenceType -eq "production_post_release_smoke" -and $json.phase -eq "canary" }
$stableSmoke = Read-SingleJson `
    -Directory $ProductionDeploymentDirectory `
    -Description "生产稳定组冒烟报告" `
    -Predicate { param($json) $json.evidenceType -eq "production_post_release_smoke" -and $json.phase -eq "stable" }

if (-not $runnerReadiness.Content.passed -or $runnerReadiness.Content.environment -ne "production") {
    throw "生产 Runner 就绪报告未通过或环境错误"
}
if (-not $deployment.Content.passed -or $deployment.Content.rolledBack -or
        $deployment.Content.environment -ne "production" -or $deployment.Content.gitCommit -ne $GitCommit -or
        $deployment.Content.candidateImage -ne $candidateImage -or
        $deployment.Content.version -ne $productionReleaseManifest.Content.version) {
    throw "生产部署失败、发生回滚或与正式版本身份不一致"
}
foreach ($smoke in @($canarySmoke, $stableSmoke)) {
    if ($smoke.Content.schemaVersion -ne "1.0" -or -not $smoke.Content.passed -or
            $smoke.Content.environment -ne "production") {
        throw "生产灰度或稳定组冒烟未通过"
    }
}

$capacity = $acceptanceEvidence.capacity.Content
$fault = $acceptanceEvidence.fault_injection.Content
$disasterRecovery = $acceptanceEvidence.disaster_recovery.Content
if (-not $fault.replicaRecovered -or [double]$fault.replicaRecoverySeconds -lt 0) {
    throw "故障注入报告没有证明实例恢复"
}
if (-not $disasterRecovery.rpoMet -or -not $disasterRecovery.rtoMet -or
        [double]$disasterRecovery.backupAgeMinutes -lt 0 -or
        [double]$disasterRecovery.restoreDurationMinutes -lt 0) {
    throw "灾备报告没有满足 RPO 或 RTO"
}

# 只从 k6 原始指标读取数值，不使用手工填写的汇总数字。
$searchP95 = Assert-PositiveMetric "检索 P95" $capacity.metrics.agentmind_search_duration.values.'p(95)'
$searchP99 = Assert-PositiveMetric "检索 P99" $capacity.metrics.agentmind_search_duration.values.'p(99)'
$ragP95 = Assert-PositiveMetric "RAG P95" $capacity.metrics.agentmind_rag_duration.values.'p(95)'
$ragP99 = Assert-PositiveMetric "RAG P99" $capacity.metrics.agentmind_rag_duration.values.'p(99)'
$requestRate = Assert-PositiveMetric "请求吞吐" $capacity.metrics.http_reqs.values.rate

$report = [ordered]@{
    schemaVersion = "1.0"
    recordType = "final_production_acceptance"
    version = $productionReleaseManifest.Content.version
    gitCommit = $GitCommit
    candidateImage = $candidateImage
    releaseCandidateId = $releaseCandidate.Content.releaseCandidateId
    workflowRuns = [ordered]@{
        acceptance = $AcceptanceWorkflowRunId
        productionRelease = $ProductionReleaseWorkflowRunId
        productionDeployment = $ProductionDeploymentWorkflowRunId
    }
    metrics = [ordered]@{
        searchP95Milliseconds = $searchP95
        searchP99Milliseconds = $searchP99
        ragP95Milliseconds = $ragP95
        ragP99Milliseconds = $ragP99
        observedRequestsPerSecond = $requestRate
        backupAgeMinutes = [double]$disasterRecovery.backupAgeMinutes
        rpoTargetMinutes = [double]$disasterRecovery.rpoTargetMinutes
        restoreDurationMinutes = [double]$disasterRecovery.restoreDurationMinutes
        rtoTargetMinutes = [double]$disasterRecovery.rtoTargetMinutes
        replicaRecoverySeconds = [double]$fault.replicaRecoverySeconds
        maximumConsecutiveFailures = [int]$fault.maximumConsecutiveFailures
    }
    sourceHashes = [ordered]@{
        releaseCandidate = Get-FileSha256 -File $releaseCandidate.File
        acceptanceEvidence = $acceptanceHashes
        productionReleaseManifest = $releaseManifestHash
        productionReleaseRecord = Get-FileSha256 -File $productionReleaseRecord.File
        productionRunnerReadiness = Get-FileSha256 -File $runnerReadiness.File
        productionDeployment = Get-FileSha256 -File $deployment.File
        canarySmoke = Get-FileSha256 -File $canarySmoke.File
        stableSmoke = Get-FileSha256 -File $stableSmoke.File
    }
    completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    passed = $true
}

New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "final-production-acceptance.json"
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host "最终生产验收证据已固化：$reportPath"
Write-Output $reportPath
