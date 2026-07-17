param(
    [Parameter(Mandatory = $true)]
    [string]$Image,
    [string]$StackName = "agentmind",
    [string]$OverlayNetwork = "agentmind-production",
    [string]$EnvironmentName = "staging",
    [string]$GitCommit = $env:GITHUB_SHA,
    [string]$ReportRoot = ".production-acceptance-evidence"
)

$ErrorActionPreference = "Stop"
$startedAt = [DateTimeOffset]::UtcNow
$serviceName = "$StackName`_agentmind-backend-canary"
if ($Image -notmatch '^.+@sha256:[a-f0-9]{64}$') {
    throw "灰度发布必须使用不可变 sha256 镜像摘要"
}

# 先只替换 10% 流量对应的灰度服务，稳定组镜像保持不变。
docker service update --with-registry-auth --detach=false --image $Image $serviceName
if ($LASTEXITCODE -ne 0) {
    throw "灰度服务更新失败，Swarm 将按服务策略回滚"
}

# 从内部覆盖网络直连灰度服务，避免公网加权分流使检查结果不确定。
docker run --rm --network $OverlayNetwork --entrypoint wget $Image `
    -qO- -T 3 -t 12 "http://$serviceName`:8081/actuator/health/readiness" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "灰度实例就绪检查失败，请执行 abort-canary-release.ps1"
}
docker service ps $serviceName --no-trunc
if (-not [string]::IsNullOrWhiteSpace($GitCommit)) {
    $report = [ordered]@{
        schemaVersion = "1.0"
        evidenceType = "canary_release"
        evidenceId = "canary-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        environment = $EnvironmentName
        gitCommit = $GitCommit
        candidateImage = $Image
        startedAt = $startedAt.ToString("o")
        completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
        service = $serviceName
        overlayNetwork = $OverlayNetwork
        readiness = "通过"
        passed = $true
        failure = $null
    }
    New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
    $reportPath = Join-Path $ReportRoot "$($report.evidenceId).json"
    $report | ConvertTo-Json -Depth 6 | Set-Content -Path $reportPath -Encoding UTF8
    Write-Host "灰度发布证据已写入：$reportPath"
} else {
    Write-Warning "未提供 GitCommit，本次灰度操作不会生成发布验收证据"
}
Write-Host "灰度实例已承接约 10% 流量。完成 k6、错误率和链路指标观察后才能晋级。"
