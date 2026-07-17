param(
    [Parameter(Mandatory = $true)]
    [string]$FinalApprovalEvidenceDirectory,
    [Parameter(Mandatory = $true)]
    [string]$GitCommit,
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [long]$FinalApprovalWorkflowRunId,
    [string]$ReportRoot = ".production-release"
)

$ErrorActionPreference = "Stop"

if ($GitCommit -notmatch '^[a-f0-9]{40}$') {
    throw "GitCommit 必须是完整的 40 位小写提交摘要"
}
if ($Version -notmatch '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$') {
    throw "Version 必须使用 v 开头的语义化版本，例如 v1.0.0 或 v1.0.0-rc.1"
}
if ($FinalApprovalWorkflowRunId -le 0) {
    throw "FinalApprovalWorkflowRunId 必须是正整数"
}
if (-not (Test-Path -LiteralPath $FinalApprovalEvidenceDirectory -PathType Container)) {
    throw "最终审批证据目录不存在"
}

function Read-SingleJsonByType {
    param([string]$TypeProperty, [string]$TypeValue, [string]$Description)
    $matches = @()
    foreach ($file in Get-ChildItem -LiteralPath $FinalApprovalEvidenceDirectory -Filter "*.json" -File -Recurse) {
        try {
            $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        } catch {
            throw "最终审批产物包含无效 JSON：$($file.FullName)"
        }
        if ([string]$content.$TypeProperty -eq $TypeValue) {
            $matches += [pscustomobject]@{ File = $file; Content = $content }
        }
    }
    if ($matches.Count -ne 1) {
        throw "$Description 必须且只能存在一份，实际数量=$($matches.Count)"
    }
    return $matches[0]
}

$candidatePacket = Read-SingleJsonByType `
    -TypeProperty "packetType" `
    -TypeValue "final_release_approval_candidate" `
    -Description "最终审批候选包"
$approvalRecord = Read-SingleJsonByType `
    -TypeProperty "evidenceType" `
    -TypeValue "final_release_approval" `
    -Description "最终人工审批记录"

if ($candidatePacket.Content.schemaVersion -ne "1.0" -or -not $candidatePacket.Content.passed) {
    throw "最终审批候选包未通过或结构版本不受支持"
}
if ($approvalRecord.Content.schemaVersion -ne "1.0" -or -not $approvalRecord.Content.passed) {
    throw "最终人工审批记录未通过或结构版本不受支持"
}
if ($candidatePacket.Content.gitCommit -ne $GitCommit -or $approvalRecord.Content.gitCommit -ne $GitCommit) {
    throw "最终审批产物与待发布提交不一致"
}
if ($candidatePacket.Content.candidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "最终审批候选包没有绑定不可变镜像摘要"
}
if ($approvalRecord.Content.approvalEnvironment -ne "production-approval") {
    throw "最终人工审批记录不来自 production-approval Environment"
}
if ([long]$approvalRecord.Content.workflowRunId -ne $FinalApprovalWorkflowRunId) {
    throw "最终审批记录的工作流运行编号与下载来源不一致"
}

$candidatePacketHash = (Get-FileHash -LiteralPath $candidatePacket.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
if ($approvalRecord.Content.candidatePacketSha256 -ne $candidatePacketHash) {
    throw "最终审批记录没有绑定当前审批候选包，证据可能被替换"
}

# 正式发布清单只引用已经审批的不可变身份，不携带访问令牌、基础设施地址或业务数据。
$manifest = [ordered]@{
    schemaVersion = "1.0"
    manifestType = "production_release"
    version = $Version
    gitCommit = $GitCommit
    candidateImage = $candidatePacket.Content.candidateImage
    releaseCandidateId = $candidatePacket.Content.releaseCandidateId
    finalApprovalWorkflowRunId = $FinalApprovalWorkflowRunId
    finalApprovalCandidateSha256 = $candidatePacketHash
    finalApprovalRecordSha256 = (Get-FileHash -LiteralPath $approvalRecord.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    generatedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    passed = $true
}

New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$manifestPath = Join-Path $ReportRoot "production-release-manifest.json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Write-Host "正式发布清单已生成：$manifestPath"
Write-Output $manifestPath
