param(
    [Parameter(Mandatory = $true)]
    [string]$StagingE2eEvidenceDirectory,
    [Parameter(Mandatory = $true)]
    [string]$ProductionAcceptanceEvidenceDirectory,
    [Parameter(Mandatory = $true)]
    [string]$GitCommit,
    [Parameter(Mandatory = $true)]
    [long]$StagingE2eWorkflowRunId,
    [string]$ReportRoot = ".final-release-approval"
)

$ErrorActionPreference = "Stop"

if ($GitCommit -notmatch '^[a-f0-9]{40}$') {
    throw "GitCommit 必须是完整的 40 位小写提交摘要"
}
if ($StagingE2eWorkflowRunId -le 0) {
    throw "StagingE2eWorkflowRunId 必须是正整数"
}
if (-not (Test-Path -LiteralPath $StagingE2eEvidenceDirectory -PathType Container)) {
    throw "真实 staging E2E 证据目录不存在"
}
if (-not (Test-Path -LiteralPath $ProductionAcceptanceEvidenceDirectory -PathType Container)) {
    throw "生产验收证据目录不存在"
}

function Read-MatchingJson {
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

$e2eEvidence = Read-MatchingJson `
    -Directory $StagingE2eEvidenceDirectory `
    -Description "真实 staging E2E 证据" `
    -Predicate { param($json) $json.evidenceType -eq "staging_full_stack_e2e" }

$releaseCandidate = Read-MatchingJson `
    -Directory $ProductionAcceptanceEvidenceDirectory `
    -Description "冻结发布候选清单" `
    -Predicate { param($json) -not [string]::IsNullOrWhiteSpace([string]$json.releaseCandidateId) }

if ($e2eEvidence.Content.schemaVersion -ne "1.0" -or -not $e2eEvidence.Content.passed) {
    throw "真实 staging E2E 证据未通过或结构版本不受支持"
}
if ($e2eEvidence.Content.environment -ne "staging") {
    throw "真实 E2E 证据不属于 staging 环境"
}
if ($e2eEvidence.Content.gitCommit -ne $GitCommit) {
    throw "真实 E2E 证据与待审批提交不一致"
}
$acceptanceRunId = [long]$e2eEvidence.Content.upstreamAcceptanceRunId
if ($acceptanceRunId -le 0) {
    throw "真实 E2E 证据缺少有效的上游发布候选验收运行编号"
}

if ($releaseCandidate.Content.schemaVersion -ne "1.0" -or -not $releaseCandidate.Content.passed) {
    throw "冻结发布候选清单未通过或结构版本不受支持"
}
if ($releaseCandidate.Content.environment -ne "staging") {
    throw "冻结发布候选清单不属于 staging 环境"
}
if ($releaseCandidate.Content.gitCommit -ne $GitCommit) {
    throw "冻结发布候选清单与待审批提交不一致"
}
if ($releaseCandidate.Content.candidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "冻结发布候选没有绑定不可变 sha256 镜像摘要"
}

$requiredEvidenceTypes = @(
    "preflight",
    "secret_rotation",
    "canary_release",
    "capacity",
    "fault_injection",
    "disaster_recovery"
)
$actualEvidenceTypes = @($releaseCandidate.Content.evidence.PSObject.Properties.Name)
$missingEvidenceTypes = @($requiredEvidenceTypes | Where-Object { $_ -notin $actualEvidenceTypes })
if ($missingEvidenceTypes.Count -gt 0) {
    throw "冻结发布候选缺少证据：$($missingEvidenceTypes -join ', ')"
}

# 审批包只保存来源运行编号、候选身份和证据哈希，不复制令牌、数据库信息或测试正文。
$packet = [ordered]@{
    schemaVersion = "1.0"
    packetType = "final_release_approval_candidate"
    gitCommit = $GitCommit
    candidateImage = $releaseCandidate.Content.candidateImage
    releaseCandidateId = $releaseCandidate.Content.releaseCandidateId
    productionAcceptanceWorkflowRunId = $acceptanceRunId
    stagingE2eWorkflowRunId = $StagingE2eWorkflowRunId
    productionAcceptanceManifestSha256 = (Get-FileHash -LiteralPath $releaseCandidate.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    stagingE2eEvidenceSha256 = (Get-FileHash -LiteralPath $e2eEvidence.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    verifiedEvidenceTypes = $requiredEvidenceTypes
    verifiedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    passed = $true
}

New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$packetPath = Join-Path $ReportRoot "final-release-approval-candidate.json"
$packet | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $packetPath -Encoding UTF8
Write-Host "最终发布审批候选包已生成：$packetPath"
Write-Output $packetPath
