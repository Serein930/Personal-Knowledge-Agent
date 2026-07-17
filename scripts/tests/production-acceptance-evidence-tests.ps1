$ErrorActionPreference = "Stop"
$systemTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$testRoot = [System.IO.Path]::GetFullPath((Join-Path $systemTempRoot "agentmind-acceptance-$([Guid]::NewGuid().ToString('N'))"))
if (-not $testRoot.StartsWith($systemTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "测试目录没有落在系统临时目录内，拒绝继续"
}
$evidenceRoot = Join-Path $testRoot "evidence"
$reportRoot = Join-Path $testRoot "reports"
$repositoryRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$gitCommit = (git -C $repositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $gitCommit -notmatch '^[a-f0-9]{40}$') {
    throw "无法读取测试所需的完整 Git 提交摘要"
}
$candidateImage = "ghcr.io/serein930/personal-knowledge-agent/backend@sha256:1111111111111111111111111111111111111111111111111111111111111111"
$types = @("preflight", "secret_rotation", "canary_release", "capacity", "fault_injection", "disaster_recovery")

function New-EvidenceFixture([string]$Type) {
    $evidence = [ordered]@{
        schemaVersion = "1.0"
        evidenceType = $Type
        evidenceId = "$Type-test"
        environment = "staging"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        startedAt = ([DateTimeOffset]::UtcNow.AddMinutes(-1)).ToString("o")
        completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
        passed = $true
        failure = $null
    }
    $evidence | ConvertTo-Json -Depth 6 | Set-Content -Path (Join-Path $evidenceRoot "$Type.json") -Encoding UTF8
}

function Assert-Throws([scriptblock]$Operation, [string]$Message) {
    $thrown = $false
    try {
        & $Operation
    } catch {
        $thrown = $true
    }
    if (-not $thrown) {
        throw $Message
    }
}

try {
    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
    foreach ($type in $types) {
        New-EvidenceFixture -Type $type
    }

    # 正向用例验证六类证据齐全时能够生成绑定文件哈希的冻结清单。
    $manifestPath = & "$PSScriptRoot/../complete-production-acceptance.ps1" `
        -EvidenceDirectory $evidenceRoot `
        -CandidateImage $candidateImage `
        -GitCommit $gitCommit `
        -ReportRoot $reportRoot | Select-Object -Last 1
    if (-not (Test-Path $manifestPath)) {
        throw "完整证据没有生成发布候选冻结清单"
    }

    # 冻结后修改任意一个字节，晋级脚本必须在访问 Docker 之前发现哈希不一致。
    $preflightPath = Join-Path $evidenceRoot "preflight.json"
    Add-Content -Path $preflightPath -Value " " -Encoding UTF8
    Assert-Throws -Operation {
        & "$PSScriptRoot/../../deploy/promote-canary-release.ps1" `
            -AcceptanceManifest $manifestPath `
            -EvidenceDirectory $evidenceRoot
    } -Message "冻结后的证据篡改没有阻断灰度晋级"
    New-EvidenceFixture -Type "preflight"

    # 负向用例验证任何未通过的报告都不能被“其他成功报告”掩盖。
    $capacityPath = Join-Path $evidenceRoot "capacity.json"
    $capacity = Get-Content -Path $capacityPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $capacity.passed = $false
    $capacity.failure = "测试构造的性能门禁失败"
    $capacity | ConvertTo-Json -Depth 6 | Set-Content -Path $capacityPath -Encoding UTF8
    Assert-Throws -Operation {
        & "$PSScriptRoot/../complete-production-acceptance.ps1" `
            -EvidenceDirectory $evidenceRoot `
            -CandidateImage $candidateImage `
            -GitCommit $gitCommit `
            -ReportRoot $reportRoot | Out-Null
    } -Message "未通过的容量证据没有阻断候选冻结"

    # 恢复容量报告后删除灾备证据，验证缺项同样会被强制阻断。
    New-EvidenceFixture -Type "capacity"
    Remove-Item -Path (Join-Path $evidenceRoot "disaster_recovery.json") -Force
    Assert-Throws -Operation {
        & "$PSScriptRoot/../complete-production-acceptance.ps1" `
            -EvidenceDirectory $evidenceRoot `
            -CandidateImage $candidateImage `
            -GitCommit $gitCommit `
            -ReportRoot $reportRoot | Out-Null
    } -Message "缺少灾备证据没有阻断候选冻结"

    Write-Host "生产验收证据测试通过：完整证据可冻结，失败和缺项均被阻断。"
} finally {
    # 删除前再次检查绝对路径边界，避免未来修改目录构造逻辑后误删工作区数据。
    if ($testRoot.StartsWith($systemTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
