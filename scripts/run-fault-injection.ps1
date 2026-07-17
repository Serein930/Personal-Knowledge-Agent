param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$StackName = "agentmind",
    [string]$ReportRoot = ".fault-injection-reports",
    [string]$GitCommit = $env:GITHUB_SHA,
    [string]$CandidateImage = $env:AGENTMIND_CANDIDATE_IMAGE,
    [switch]$ConfirmStagingFaultInjection
)

$ErrorActionPreference = "Stop"
if (-not $ConfirmStagingFaultInjection -or $env:AGENTMIND_ENVIRONMENT -ne "staging") {
    throw "故障注入只允许在 AGENTMIND_ENVIRONMENT=staging 且显式确认后执行"
}
if (-not $BaseUrl.StartsWith("https://")) {
    throw "故障注入必须通过 HTTPS 网关观察服务"
}

$serviceName = "$StackName`_agentmind-backend"
$containerId = docker ps `
    --filter "label=com.docker.swarm.service.name=$serviceName" `
    --filter "status=running" --format "{{.ID}}" | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($containerId)) {
    throw "当前节点没有可用于故障注入的稳定组任务"
}

$startedAt = [DateTimeOffset]::Now
$recoveryStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$failures = 0
$maximumConsecutiveFailures = 0
$consecutiveFailures = 0
$recovered = $false

# 只终止一个任务，Swarm 应自动补副本；网关必须依靠其余实例持续提供就绪响应。
docker kill $containerId | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "终止测试实例失败"
}

for ($attempt = 1; $attempt -le 60; $attempt++) {
    try {
        $response = Invoke-WebRequest -Uri "$($BaseUrl.TrimEnd('/'))/actuator/health/readiness" `
            -UseBasicParsing -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            $consecutiveFailures = 0
        } else {
            throw "状态码=$($response.StatusCode)"
        }
    } catch {
        $failures++
        $consecutiveFailures++
        $maximumConsecutiveFailures = [Math]::Max($maximumConsecutiveFailures, $consecutiveFailures)
    }

    $runningTasks = docker service ps $serviceName --filter "desired-state=running" --format "{{.CurrentState}}"
    if (($runningTasks | Where-Object { $_ -like "Running*" } | Measure-Object).Count -ge 2) {
        $recovered = $true
        break
    }
    Start-Sleep -Seconds 2
}
$recoveryStopwatch.Stop()

$report = [ordered]@{
    schemaVersion = "1.0"
    evidenceType = "fault_injection"
    evidenceId = "fault-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    environment = "staging"
    gitCommit = $GitCommit
    candidateImage = $CandidateImage
    startedAt = $startedAt.ToString("o")
    completedAt = ([DateTimeOffset]::Now).ToString("o")
    terminatedContainer = $containerId
    gatewayProbeFailures = $failures
    maximumConsecutiveFailures = $maximumConsecutiveFailures
    replicaRecovered = $recovered
    replicaRecoverySeconds = [Math]::Round($recoveryStopwatch.Elapsed.TotalSeconds, 2)
    passed = $recovered -and $maximumConsecutiveFailures -le 1
    failure = if ($recovered) { $null } else { "Swarm 未在观察窗口内恢复目标副本数" }
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "$($report.evidenceId).json"
$report | ConvertTo-Json -Depth 4 | Set-Content -Path $reportPath -Encoding UTF8
if (-not $report.passed) {
    throw "故障注入未通过，详情见 $reportPath"
}
Write-Host "故障注入通过：副本已恢复，网关最大连续失败次数=$maximumConsecutiveFailures"
