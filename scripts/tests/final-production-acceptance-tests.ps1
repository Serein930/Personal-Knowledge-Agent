$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$acceptanceScript = Join-Path $repositoryRoot "scripts/complete-final-production-acceptance.ps1"
$workflowPath = Join-Path $repositoryRoot ".github/workflows/final-production-acceptance.yml"
$systemTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$temporaryRoot = [System.IO.Path]::GetFullPath((Join-Path $systemTempRoot "agentmind-final-production-$([Guid]::NewGuid().ToString('N'))"))
if (-not $temporaryRoot.StartsWith($systemTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "测试目录没有落在系统临时目录内"
}
$acceptanceDirectory = Join-Path $temporaryRoot "acceptance"
$releaseDirectory = Join-Path $temporaryRoot "release"
$deploymentDirectory = Join-Path $temporaryRoot "deployment"
$reportDirectory = Join-Path $temporaryRoot "report"
$gitCommit = "a" * 40
$candidateImage = "ghcr.io/serein930/personal-knowledge-agent/backend@sha256:$('b' * 64)"
$releaseCandidateId = "rc-final-production-test"

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

function Write-Json([string]$Path, [object]$Content) {
    $Content | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-AcceptanceEvidence {
    $types = @("preflight", "secret_rotation", "canary_release", "capacity", "fault_injection", "disaster_recovery")
    $manifestEvidence = [ordered]@{}
    foreach ($type in $types) {
        $evidence = [ordered]@{
            schemaVersion = "1.0"
            evidenceType = $type
            evidenceId = "$type-test"
            environment = "staging"
            gitCommit = $gitCommit
            candidateImage = $candidateImage
            passed = $true
            completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
        }
        if ($type -eq "capacity") {
            $evidence.metrics = [ordered]@{
                agentmind_search_duration = [ordered]@{ values = [ordered]@{ "p(95)" = 320.5; "p(99)" = 540.5 } }
                agentmind_rag_duration = [ordered]@{ values = [ordered]@{ "p(95)" = 1450.5; "p(99)" = 2680.5 } }
                http_reqs = [ordered]@{ values = [ordered]@{ rate = 42.75 } }
            }
        }
        if ($type -eq "fault_injection") {
            $evidence.replicaRecovered = $true
            $evidence.replicaRecoverySeconds = 18.5
            $evidence.maximumConsecutiveFailures = 1
        }
        if ($type -eq "disaster_recovery") {
            $evidence.backupAgeMinutes = 12.0
            $evidence.rpoTargetMinutes = 30.0
            $evidence.rpoMet = $true
            $evidence.restoreDurationMinutes = 9.0
            $evidence.rtoTargetMinutes = 20.0
            $evidence.rtoMet = $true
        }
        $path = Join-Path $acceptanceDirectory "$type.json"
        Write-Json -Path $path -Content $evidence
        $manifestEvidence[$type] = [ordered]@{
            sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
            completedAt = $evidence.completedAt
        }
    }
    Write-Json -Path (Join-Path $acceptanceDirectory "release-candidate.json") -Content ([ordered]@{
        schemaVersion = "1.0"
        releaseCandidateId = $releaseCandidateId
        environment = "staging"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        evidence = $manifestEvidence
        passed = $true
    })
}

function Write-ReleaseEvidence([string]$Commit = $gitCommit) {
    $manifestPath = Join-Path $releaseDirectory "production-release-manifest.json"
    Write-Json -Path $manifestPath -Content ([ordered]@{
        schemaVersion = "1.0"
        manifestType = "production_release"
        version = "v1.0.0"
        gitCommit = $Commit
        candidateImage = $candidateImage
        releaseCandidateId = $releaseCandidateId
        passed = $true
    })
    Write-Json -Path (Join-Path $releaseDirectory "production-release-record.json") -Content ([ordered]@{
        schemaVersion = "1.0"
        evidenceType = "production_release"
        version = "v1.0.0"
        gitCommit = $Commit
        candidateImage = $candidateImage
        releaseManifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
        passed = $true
    })
}

function Write-DeploymentEvidence([bool]$RolledBack = $false, [bool]$IncludeStableSmoke = $true) {
    Write-Json -Path (Join-Path $deploymentDirectory "production-runner-readiness.json") -Content ([ordered]@{
        schemaVersion = "1.0"
        reportType = "production_runner_readiness"
        environment = "production"
        passed = $true
    })
    Write-Json -Path (Join-Path $deploymentDirectory "production-deployment.json") -Content ([ordered]@{
        schemaVersion = "1.0"
        evidenceType = "production_deployment"
        environment = "production"
        version = "v1.0.0"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        rolledBack = $RolledBack
        passed = -not $RolledBack
    })
    Write-Json -Path (Join-Path $deploymentDirectory "production-canary-post-release-smoke.json") -Content ([ordered]@{
        schemaVersion = "1.0"
        evidenceType = "production_post_release_smoke"
        environment = "production"
        phase = "canary"
        passed = $true
    })
    $stablePath = Join-Path $deploymentDirectory "production-stable-post-release-smoke.json"
    if ($IncludeStableSmoke) {
        Write-Json -Path $stablePath -Content ([ordered]@{
            schemaVersion = "1.0"
            evidenceType = "production_post_release_smoke"
            environment = "production"
            phase = "stable"
            passed = $true
        })
    } elseif (Test-Path -LiteralPath $stablePath) {
        Remove-Item -LiteralPath $stablePath -Force
    }
}

function Invoke-FinalAcceptance {
    & $acceptanceScript `
        -ProductionAcceptanceDirectory $acceptanceDirectory `
        -ProductionReleaseDirectory $releaseDirectory `
        -ProductionDeploymentDirectory $deploymentDirectory `
        -GitCommit $gitCommit `
        -AcceptanceWorkflowRunId 1001 `
        -ProductionReleaseWorkflowRunId 1002 `
        -ProductionDeploymentWorkflowRunId 1003 `
        -ReportRoot $reportDirectory | Select-Object -Last 1
}

try {
    New-Item -ItemType Directory -Path $acceptanceDirectory, $releaseDirectory, $deploymentDirectory -Force | Out-Null
    Write-AcceptanceEvidence
    Write-ReleaseEvidence
    Write-DeploymentEvidence

    $reportPath = Invoke-FinalAcceptance
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $report.passed -or $report.recordType -ne "final_production_acceptance" -or
            [double]$report.metrics.ragP99Milliseconds -ne 2680.5 -or
            [double]$report.metrics.observedRequestsPerSecond -ne 42.75) {
        throw "完整真实证据没有生成正确的最终生产验收记录"
    }

    Add-Content -LiteralPath (Join-Path $acceptanceDirectory "capacity.json") -Value " " -Encoding UTF8
    Assert-Throws -ExpectedMessage "与冻结清单哈希不一致" -Action { Invoke-FinalAcceptance }

    Write-AcceptanceEvidence
    Write-ReleaseEvidence -Commit ("c" * 40)
    Assert-Throws -ExpectedMessage "正式版本、冻结候选和最终验收身份不一致" -Action { Invoke-FinalAcceptance }

    Write-ReleaseEvidence
    Write-DeploymentEvidence -RolledBack $true
    Assert-Throws -ExpectedMessage "发生回滚" -Action { Invoke-FinalAcceptance }

    Write-DeploymentEvidence -IncludeStableSmoke $false
    Assert-Throws -ExpectedMessage "生产稳定组冒烟报告" -Action { Invoke-FinalAcceptance }

    $workflow = Get-Content -LiteralPath $workflowPath -Raw -Encoding UTF8
    foreach ($requiredText in @(
        "environment: production-approval",
        "FINAL_RELEASE_APPROVAL_GUARD",
        "production-acceptance-*",
        "production-release-v*",
        "production-deployment-*",
        "complete-final-production-acceptance.ps1"
    )) {
        if (-not $workflow.Contains($requiredText)) {
            throw "最终生产验收工作流缺少保护契约：$requiredText"
        }
    }
} finally {
    if ($temporaryRoot.StartsWith($systemTempRoot, [StringComparison]::OrdinalIgnoreCase) -and
            (Test-Path -LiteralPath $temporaryRoot)) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Host "最终生产验收证据闭环测试全部通过"
