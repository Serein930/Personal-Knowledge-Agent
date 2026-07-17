$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$deploymentScript = Join-Path $repositoryRoot "deploy/invoke-production-deployment.ps1"
$workflowPath = Join-Path $repositoryRoot ".github/workflows/production-deployment.yml"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "agentmind-production-deployment-$([Guid]::NewGuid().ToString('N'))"
$evidenceDirectory = Join-Path $temporaryRoot "release"
$reportDirectory = Join-Path $temporaryRoot "report"
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

function Write-ReleaseEvidence([string]$ManifestHash = "") {
    $manifestPath = Join-Path $evidenceDirectory "production-release-manifest.json"
    [ordered]@{
        schemaVersion = "1.0"
        manifestType = "production_release"
        version = "v1.0.0"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        passed = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    $actualHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $boundHash = if ([string]::IsNullOrWhiteSpace($ManifestHash)) { $actualHash } else { $ManifestHash }
    [ordered]@{
        schemaVersion = "1.0"
        evidenceType = "production_release"
        version = "v1.0.0"
        gitCommit = $gitCommit
        candidateImage = $candidateImage
        releaseManifestSha256 = $boundHash
        passed = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidenceDirectory "production-release-record.json") -Encoding UTF8
}

function Invoke-EvidenceValidation {
    & $deploymentScript `
        -ProductionReleaseEvidenceDirectory $evidenceDirectory `
        -BaseUrl "https://knowledge.example.com" `
        -WorkspaceId 1001 `
        -SmokeQuery "生产冒烟固定问题" `
        -ExpectedDocumentId 2001 `
        -StackName "agentmind-production" `
        -EnvironmentName "production" `
        -ReportRoot $reportDirectory `
        -ValidateOnly
}

try {
    New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
    Write-ReleaseEvidence

    # ValidateOnly 必须在 Docker、Git 和真实服务调用前返回，因此该测试不会修改任何部署资源。
    Invoke-EvidenceValidation

    Write-ReleaseEvidence -ManifestHash ("c" * 64)
    Assert-Throws -ExpectedMessage "没有绑定当前正式发布清单" -Action { Invoke-EvidenceValidation }

    Write-ReleaseEvidence
    Assert-Throws -ExpectedMessage "只允许在 production 环境执行" -Action {
        & $deploymentScript `
            -ProductionReleaseEvidenceDirectory $evidenceDirectory `
            -BaseUrl "https://knowledge.example.com" `
            -WorkspaceId 1001 `
            -SmokeQuery "生产冒烟固定问题" `
            -ExpectedDocumentId 2001 `
            -EnvironmentName "staging" `
            -ValidateOnly
    }
    Assert-Throws -ExpectedMessage "生产堆栈名称格式无效" -Action {
        & $deploymentScript `
            -ProductionReleaseEvidenceDirectory $evidenceDirectory `
            -BaseUrl "https://knowledge.example.com" `
            -WorkspaceId 1001 `
            -SmokeQuery "生产冒烟固定问题" `
            -ExpectedDocumentId 2001 `
            -StackName "!" `
            -EnvironmentName "production" `
            -ValidateOnly
    }

    $workflow = Get-Content -LiteralPath $workflowPath -Raw -Encoding UTF8
    foreach ($requiredText in @(
        "runs-on: [self-hosted, agentmind-production]",
        "environment: production-deployment",
        "PRODUCTION_DEPLOYMENT_GUARD",
        "production-release-v*",
        "-ConfirmProductionDeployment",
        'SMOKE_QUERY: ${{ inputs.smoke_query }}',
        '-SmokeQuery $env:SMOKE_QUERY'
    )) {
        if (-not $workflow.Contains($requiredText)) {
            throw "独立生产部署工作流缺少安全契约：$requiredText"
        }
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

Write-Host "独立生产部署边界测试全部通过"
