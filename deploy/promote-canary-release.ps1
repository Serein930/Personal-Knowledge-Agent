param(
    [Parameter(Mandatory = $true)]
    [string]$AcceptanceManifest,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,
    [string]$StackName = "agentmind",
    [int]$MaximumEvidenceAgeHours = 24
)

$ErrorActionPreference = "Stop"
$manifestPath = (Resolve-Path -Path $AcceptanceManifest).Path
$evidenceRoot = (Resolve-Path -Path $EvidenceDirectory).Path
$manifest = Get-Content -Path $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $manifest.passed -or $manifest.environment -ne "staging") {
    throw "灰度晋级只接受已经冻结且通过的 staging 验收清单"
}
if ($manifest.candidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "验收清单没有绑定不可变镜像摘要"
}
$frozenAt = [DateTimeOffset]::Parse($manifest.frozenAt)
if (([DateTimeOffset]::UtcNow - $frozenAt).TotalHours -gt $MaximumEvidenceAgeHours) {
    throw "验收清单已经超过 $MaximumEvidenceAgeHours 小时，必须重新执行验收"
}

# 晋级前重新计算全部原始证据哈希，防止冻结后报告被替换或手工修改。
$requiredEvidenceTypes = @("preflight", "secret_rotation", "canary_release", "capacity", "fault_injection", "disaster_recovery")
$validatedEvidenceTypes = @{}
foreach ($property in $manifest.evidence.PSObject.Properties) {
    $evidencePath = [System.IO.Path]::GetFullPath((Join-Path $evidenceRoot $property.Value.file))
    $rootPrefix = $evidenceRoot.TrimEnd([char[]]@('\', '/')) + [System.IO.Path]::DirectorySeparatorChar
    $pathComparison = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        [StringComparison]::OrdinalIgnoreCase
    } else {
        [StringComparison]::Ordinal
    }
    if (-not $evidencePath.StartsWith($rootPrefix, $pathComparison)) {
        throw "验收清单包含越过证据目录的非法路径"
    }
    if (-not (Test-Path $evidencePath)) {
        throw "晋级所需原始证据不存在：$($property.Value.file)"
    }
    $actualHash = (Get-FileHash -Path $evidencePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $property.Value.sha256) {
        throw "原始证据在冻结后发生变化：$($property.Value.file)"
    }
    $evidence = Get-Content -Path $evidencePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($property.Name -ne $evidence.evidenceType -or $requiredEvidenceTypes -notcontains $evidence.evidenceType) {
        throw "验收清单中的证据类型与原始报告不一致"
    }
    if ($evidence.schemaVersion -ne "1.0" -or -not $evidence.passed) {
        throw "原始验收证据版本不受支持或未通过"
    }
    if ($evidence.environment -ne $manifest.environment -or
        $evidence.gitCommit -ne $manifest.gitCommit -or
        $evidence.candidateImage -ne $manifest.candidateImage) {
        throw "原始验收证据与冻结清单绑定的环境、提交或镜像不一致"
    }
    $validatedEvidenceTypes[$evidence.evidenceType] = $true
}
$missingEvidenceTypes = $requiredEvidenceTypes | Where-Object { -not $validatedEvidenceTypes.ContainsKey($_) }
if (($missingEvidenceTypes | Measure-Object).Count -gt 0) {
    throw "冻结清单缺少晋级必需证据：$($missingEvidenceTypes -join ', ')"
}

$currentCommit = git rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $currentCommit -ne $manifest.gitCommit) {
    throw "当前检出的 Git 提交与验收清单不一致"
}
$canaryService = "$StackName`_agentmind-backend-canary"
$stableService = "$StackName`_agentmind-backend"
$canaryImage = docker service inspect $canaryService --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($canaryImage)) {
    throw "无法读取灰度镜像"
}
if ($canaryImage -ne $manifest.candidateImage) {
    throw "当前灰度镜像与验收清单绑定的候选镜像不一致"
}

# 稳定组仍按 start-first 逐实例替换；任一实例未通过健康监控都会自动回滚。
docker service update --with-registry-auth --detach=false --image $canaryImage $stableService
if ($LASTEXITCODE -ne 0) {
    throw "灰度晋级失败，稳定服务已按策略回滚"
}
docker service ps $stableService --no-trunc
Write-Host "灰度版本已晋级到稳定组，请继续观察完整监控窗口。"
