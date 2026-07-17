$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$packetScript = Join-Path $repositoryRoot "scripts/new-final-release-approval-packet.ps1"
$workflowPath = Join-Path $repositoryRoot ".github/workflows/final-release-approval.yml"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-final-approval-$([Guid]::NewGuid().ToString('N'))"
$e2eDirectory = Join-Path $temporaryRoot "e2e"
$acceptanceDirectory = Join-Path $temporaryRoot "acceptance"
$reportDirectory = Join-Path $temporaryRoot "reports"
$gitCommit = "a" * 40
$candidateImage = "ghcr.io/serein930/personal-knowledge-agent/backend@sha256:$('b' * 64)"

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

function Write-E2eEvidence([string]$Commit = $gitCommit) {
    [ordered]@{
        schemaVersion = "1.0"
        evidenceType = "staging_full_stack_e2e"
        environment = "staging"
        gitCommit = $Commit
        upstreamAcceptanceRunId = 1001
        passed = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $e2eDirectory "staging-full-stack-e2e.json") -Encoding UTF8
}

function Write-AcceptanceManifest([switch]$OmitDisasterRecovery) {
    $evidence = [ordered]@{
        preflight = @{ sha256 = "1" }
        secret_rotation = @{ sha256 = "2" }
        canary_release = @{ sha256 = "3" }
        capacity = @{ sha256 = "4" }
        fault_injection = @{ sha256 = "5" }
    }
    if (-not $OmitDisasterRecovery) {
        $evidence.disaster_recovery = @{ sha256 = "6" }
    }
    [ordered]@{
        schemaVersion = "1.0"
        releaseCandidateId = "rc-test"
        environment = "staging"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        evidence = $evidence
        passed = $true
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $acceptanceDirectory "rc-test.json") -Encoding UTF8
}

try {
    New-Item -ItemType Directory -Path $e2eDirectory, $acceptanceDirectory -Force | Out-Null
    Write-E2eEvidence
    Write-AcceptanceManifest

    $packetPath = & $packetScript `
        -StagingE2eEvidenceDirectory $e2eDirectory `
        -ProductionAcceptanceEvidenceDirectory $acceptanceDirectory `
        -GitCommit $gitCommit `
        -StagingE2eWorkflowRunId 2002 `
        -ReportRoot $reportDirectory | Select-Object -Last 1
    $packet = Get-Content -LiteralPath $packetPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $packet.passed -or $packet.gitCommit -ne $gitCommit -or $packet.candidateImage -ne $candidateImage) {
        throw "有效证据没有生成正确的最终发布审批候选包"
    }
    if (@($packet.verifiedEvidenceTypes).Count -ne 6) {
        throw "审批候选包没有固定全部六类生产验收证据"
    }

    Write-E2eEvidence -Commit ("c" * 40)
    Assert-Throws -ExpectedMessage "真实 E2E 证据与待审批提交不一致" -Action {
        & $packetScript `
            -StagingE2eEvidenceDirectory $e2eDirectory `
            -ProductionAcceptanceEvidenceDirectory $acceptanceDirectory `
            -GitCommit $gitCommit `
            -StagingE2eWorkflowRunId 2002 `
            -ReportRoot $reportDirectory
    }

    Write-E2eEvidence
    Write-AcceptanceManifest -OmitDisasterRecovery
    Assert-Throws -ExpectedMessage "缺少证据：disaster_recovery" -Action {
        & $packetScript `
            -StagingE2eEvidenceDirectory $e2eDirectory `
            -ProductionAcceptanceEvidenceDirectory $acceptanceDirectory `
            -GitCommit $gitCommit `
            -StagingE2eWorkflowRunId 2002 `
            -ReportRoot $reportDirectory
    }

    $workflow = Get-Content -LiteralPath $workflowPath -Raw -Encoding UTF8
    foreach ($requiredText in @(
        "github.event.workflow_run.conclusion == 'success'",
        "github.event.workflow_run.head_branch == 'main'",
        "environment: production-approval",
        "FINAL_RELEASE_APPROVAL_GUARD",
        "production-acceptance-*",
        "staging-full-stack-e2e-*"
    )) {
        if (-not $workflow.Contains($requiredText)) {
            throw "最终发布审批工作流缺少安全契约：$requiredText"
        }
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Host "最终发布审批证据链测试全部通过"
