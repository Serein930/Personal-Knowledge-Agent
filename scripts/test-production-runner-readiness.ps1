param(
    [string]$EnvironmentName = $env:AGENTMIND_ENVIRONMENT,
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$StackName = "agentmind-production",
    [string]$OverlayNetwork = "agentmind-production",
    [string]$ReportRoot = ".production-deployment-evidence"
)

$ErrorActionPreference = "Stop"
$startedAt = [DateTimeOffset]::UtcNow
$checks = [ordered]@{}
$failure = $null

function Assert-LastCommand([string]$Message) {
    if ($LASTEXITCODE -ne 0) { throw $Message }
}

function Get-RequiredTool([string]$Name, [string[]]$VersionArguments) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) { throw "生产 Runner 缺少必需工具：$Name" }
    $version = (& $command.Source @VersionArguments 2>&1 | Select-Object -First 1 | Out-String).Trim()
    Assert-LastCommand "无法执行 $Name 的版本检查"
    return [ordered]@{ path = $command.Source; version = $version }
}

try {
    if ($EnvironmentName -ne "production" -or $env:AGENTMIND_ENVIRONMENT -ne "production") {
        throw "生产 Runner 就绪检查只允许在 AGENTMIND_ENVIRONMENT=production 的受保护环境运行"
    }
    if ($PSVersionTable.PSVersion.Major -lt 7) {
        throw "生产 Runner 必须使用 PowerShell 7 或更高版本"
    }
    if ($StackName -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{2,63}$' -or
            $OverlayNetwork -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{2,63}$') {
        throw "生产堆栈或覆盖网络名称格式无效"
    }

    $baseUri = $null
    if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$baseUri) -or
            $baseUri.Scheme -ne "https" -or -not [string]::IsNullOrWhiteSpace($baseUri.Query) -or
            -not [string]::IsNullOrWhiteSpace($baseUri.Fragment)) {
        throw "PRODUCTION_BASE_URL 必须是不带查询参数和片段的 HTTPS 地址"
    }
    $checks.baseUrl = $baseUri.GetLeftPart([UriPartial]::Authority)
    $checks.runnerName = $env:RUNNER_NAME
    $checks.tools = [ordered]@{
        powershell = $PSVersionTable.PSVersion.ToString()
        git = Get-RequiredTool -Name "git" -VersionArguments @("--version")
        docker = Get-RequiredTool -Name "docker" -VersionArguments @("--version")
    }

    $swarm = docker info --format '{{json .Swarm}}' | ConvertFrom-Json
    Assert-LastCommand "无法读取生产 Docker Swarm 状态"
    if ($swarm.LocalNodeState -ne "active" -or -not $swarm.ControlAvailable) {
        throw "生产 Runner 必须连接到可用的 Docker Swarm 管理节点"
    }
    $checks.swarm = [ordered]@{
        nodeId = $swarm.NodeID
        state = $swarm.LocalNodeState
        controlAvailable = $swarm.ControlAvailable
    }

    $networkDriver = docker network inspect $OverlayNetwork --format '{{.Driver}}'
    Assert-LastCommand "无法读取生产覆盖网络 $OverlayNetwork"
    $networkOptions = docker network inspect $OverlayNetwork --format '{{json .Options}}' | ConvertFrom-Json
    Assert-LastCommand "无法读取生产覆盖网络加密配置"
    if ($networkDriver -ne "overlay" -or $networkOptions.PSObject.Properties.Name -notcontains "encrypted") {
        throw "生产网络 $OverlayNetwork 必须是启用 encrypted 选项的 overlay 网络"
    }
    $checks.overlayNetwork = [ordered]@{ name = $OverlayNetwork; driver = $networkDriver; encrypted = $true }

    $requiredServices = [ordered]@{
        "$StackName`_agentmind-backend" = 2
        "$StackName`_agentmind-backend-canary" = 1
        "$StackName`_agentmind-gateway" = 2
    }
    $serviceChecks = [ordered]@{}
    foreach ($entry in $requiredServices.GetEnumerator()) {
        $serviceName = $entry.Key
        $desiredReplicas = docker service inspect $serviceName --format '{{.Spec.Mode.Replicated.Replicas}}'
        Assert-LastCommand "无法读取生产服务 $serviceName"
        $runningTasks = docker service ps $serviceName --filter "desired-state=running" --format '{{.CurrentState}}'
        Assert-LastCommand "无法读取生产服务 $serviceName 的任务状态"
        $runningReplicas = ($runningTasks | Where-Object { $_ -like "Running*" } | Measure-Object).Count
        if ([int]$desiredReplicas -lt $entry.Value -or $runningReplicas -lt $entry.Value) {
            throw "生产服务 $serviceName 未达到最低副本数：要求=$($entry.Value)，运行=$runningReplicas"
        }

        # 部署编排依赖 start-first 和失败自动回滚，必须在修改镜像前验证真实服务策略。
        $updateConfig = docker service inspect $serviceName --format '{{json .Spec.UpdateConfig}}' | ConvertFrom-Json
        Assert-LastCommand "无法读取生产服务 $serviceName 的更新策略"
        if ($updateConfig.Order -ne "start-first" -or $updateConfig.FailureAction -ne "rollback") {
            throw "生产服务 $serviceName 未配置 start-first 和失败自动回滚"
        }
        $serviceChecks[$serviceName] = [ordered]@{
            desired = [int]$desiredReplicas
            running = $runningReplicas
            updateOrder = $updateConfig.Order
            failureAction = $updateConfig.FailureAction
        }
    }
    $checks.services = $serviceChecks

    # 最后执行只读 HTTPS 探测，验证 Runner 到生产网关的 DNS、TLS 和路由均可用。
    $readiness = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/actuator/health/readiness" -TimeoutSec 15
    if ($readiness.status -ne "UP") { throw "生产就绪探针未返回 UP" }
    $checks.httpsReadiness = "通过"
} catch {
    $failure = $_.Exception.Message
}

# 就绪报告不使用 evidenceType，避免被正式发布证据查找逻辑误识别。
$report = [ordered]@{
    schemaVersion = "1.0"
    reportType = "production_runner_readiness"
    environment = $EnvironmentName
    runnerName = $env:RUNNER_NAME
    startedAt = $startedAt.ToString("o")
    completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    checks = $checks
    passed = $null -eq $failure
    failure = $failure
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "production-runner-readiness.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $reportPath -Encoding UTF8
if (-not $report.passed) {
    throw "生产 Runner 就绪检查未通过：$failure。报告=$reportPath"
}
Write-Host "生产 Runner 就绪检查通过：报告=$reportPath"
