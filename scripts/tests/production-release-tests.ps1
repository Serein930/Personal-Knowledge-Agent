$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$manifestScript = Join-Path $repositoryRoot "scripts/new-production-release-manifest.ps1"
$workflowPath = Join-Path $repositoryRoot ".github/workflows/production-release.yml"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-production-release-$([Guid]::NewGuid().ToString('N'))"
$approvalDirectory = Join-Path $temporaryRoot "approval"
$reportDirectory = Join-Path $temporaryRoot "report"
$gitCommit = "a" * 40
$approvalRunId = 3003
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

function Write-ApprovalEvidence([string]$RecordPacketHash = "") {
    $candidatePacketPath = Join-Path $approvalDirectory "final-release-approval-candidate.json"
    [ordered]@{
        schemaVersion = "1.0"
        packetType = "final_release_approval_candidate"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        releaseCandidateId = "rc-test"
        passed = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $candidatePacketPath -Encoding UTF8

    $actualHash = (Get-FileHash -LiteralPath $candidatePacketPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $boundHash = if ([string]::IsNullOrWhiteSpace($RecordPacketHash)) { $actualHash } else { $RecordPacketHash }
    [ordered]@{
        schemaVersion = "1.0"
        evidenceType = "final_release_approval"
        approvalEnvironment = "production-approval"
        gitCommit = $gitCommit
        workflowRunId = $approvalRunId
        candidatePacketSha256 = $boundHash
        passed = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $approvalDirectory "final-release-approval.json") -Encoding UTF8
}

try {
    New-Item -ItemType Directory -Path $approvalDirectory -Force | Out-Null
    Write-ApprovalEvidence

    $manifestPath = & $manifestScript `
        -FinalApprovalEvidenceDirectory $approvalDirectory `
        -GitCommit $gitCommit `
        -Version "v1.0.0" `
        -FinalApprovalWorkflowRunId $approvalRunId `
        -ReportRoot $reportDirectory | Select-Object -Last 1
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $manifest.passed -or $manifest.version -ne "v1.0.0" -or $manifest.candidateImage -ne $candidateImage) {
        throw "有效最终审批产物没有生成正确的正式发布清单"
    }

    Assert-Throws -ExpectedMessage "Version 必须使用 v 开头的语义化版本" -Action {
        & $manifestScript `
            -FinalApprovalEvidenceDirectory $approvalDirectory `
            -GitCommit $gitCommit `
            -Version "latest" `
            -FinalApprovalWorkflowRunId $approvalRunId `
            -ReportRoot $reportDirectory
    }

    Write-ApprovalEvidence -RecordPacketHash ("c" * 64)
    Assert-Throws -ExpectedMessage "证据可能被替换" -Action {
        & $manifestScript `
            -FinalApprovalEvidenceDirectory $approvalDirectory `
            -GitCommit $gitCommit `
            -Version "v1.0.1" `
            -FinalApprovalWorkflowRunId $approvalRunId `
            -ReportRoot $reportDirectory
    }

    $workflow = Get-Content -LiteralPath $workflowPath -Raw -Encoding UTF8
    foreach ($requiredText in @(
        "environment: production-release",
        "PRODUCTION_RELEASE_GUARD",
        "final-release-approval-*",
        "target_commitish",
        "releaseManifestSha256",
        "candidateImage",
        '版本 $VERSION 已存在但指向其他提交'
    )) {
        if (-not $workflow.Contains($requiredText)) {
            throw "正式发布工作流缺少安全契约：$requiredText"
        }
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Host "正式版本发布边界测试全部通过"
