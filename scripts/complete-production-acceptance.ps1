param(
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,
    [Parameter(Mandatory = $true)]
    [string]$CandidateImage,
    [Parameter(Mandatory = $true)]
    [string]$GitCommit,
    [string]$EnvironmentName = "staging",
    [string]$ReportRoot = ".production-acceptance-reports"
)

$ErrorActionPreference = "Stop"
if ($EnvironmentName -ne "staging") {
    throw "发布候选冻结只接受 staging 验收证据"
}
if ($CandidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "候选镜像必须使用不可变 sha256 摘要"
}
if ($GitCommit -notmatch '^[a-f0-9]{40}$') {
    throw "GitCommit 必须是完整的 40 位提交摘要"
}

$resolvedEvidenceDirectory = (Resolve-Path -Path $EvidenceDirectory).Path
$requiredEvidenceTypes = @(
    "preflight",
    "secret_rotation",
    "canary_release",
    "capacity",
    "fault_injection",
    "disaster_recovery"
)
$evidenceByType = @{}

foreach ($file in Get-ChildItem -Path $resolvedEvidenceDirectory -Filter "*.json" -Recurse -File) {
    try {
        $evidence = Get-Content -Path $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        throw "验收证据不是有效 JSON：$($file.FullName)"
    }
    if ($requiredEvidenceTypes -notcontains $evidence.evidenceType) {
        continue
    }
    if ($evidenceByType.ContainsKey($evidence.evidenceType)) {
        throw "发现重复验收证据类型：$($evidence.evidenceType)"
    }
    if ($evidence.schemaVersion -ne "1.0") {
        throw "证据 $($file.Name) 的结构版本不受支持"
    }
    if (-not $evidence.passed) {
        throw "证据 $($file.Name) 未通过，禁止冻结发布候选"
    }
    if ($evidence.environment -ne $EnvironmentName) {
        throw "证据 $($file.Name) 不属于目标验收环境"
    }
    if ($evidence.gitCommit -ne $GitCommit) {
        throw "证据 $($file.Name) 的 Git 提交与候选版本不一致"
    }
    if ($evidence.candidateImage -ne $CandidateImage) {
        throw "证据 $($file.Name) 的镜像摘要与候选版本不一致"
    }
    $evidenceByType[$evidence.evidenceType] = [ordered]@{
        evidenceId = $evidence.evidenceId
        file = $file.FullName.Substring($resolvedEvidenceDirectory.Length).TrimStart([char[]]@('\', '/'))
        sha256 = (Get-FileHash -Path $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        completedAt = $evidence.completedAt
    }
}

$missingTypes = $requiredEvidenceTypes | Where-Object { -not $evidenceByType.ContainsKey($_) }
if (($missingTypes | Measure-Object).Count -gt 0) {
    throw "缺少验收证据：$($missingTypes -join ', ')"
}

# 冻结清单绑定提交、镜像和每份证据哈希；后续修改任何原始报告都会使清单失效。
$manifest = [ordered]@{
    schemaVersion = "1.0"
    releaseCandidateId = "rc-$($GitCommit.Substring(0, 12))-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    environment = $EnvironmentName
    gitCommit = $GitCommit
    candidateImage = $CandidateImage
    frozenAt = ([DateTimeOffset]::UtcNow).ToString("o")
    evidence = $evidenceByType
    passed = $true
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$manifestPath = Join-Path $ReportRoot "$($manifest.releaseCandidateId).json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path $manifestPath -Encoding UTF8
Write-Host "发布候选证据已冻结：$manifestPath"
Write-Output $manifestPath
